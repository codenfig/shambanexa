import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { apiError, formatNumber } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { downloadCsv, expiryLabel, scopeChip, whenLabel } from './inventory.format';
import { InventoryStore } from './inventory.store';

@Component({
  selector: 'app-stock-receipts',
  imports: [FormsModule, ReactiveFormsModule, RouterLink],
  templateUrl: './receipts.component.html',
  styleUrls: ['./inventory.component.css', './receipts.component.css']
})
export class StockReceiptsComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);
  readonly store = inject(InventoryStore);
  readonly fmt = formatNumber;
  readonly when = whenLabel;
  readonly expiry = expiryLabel;
  session = this.auth.session;
  open = signal(false);
  mode = signal<'existing' | 'new'>('existing');
  busy = signal(false);
  error = signal('');
  farmFilter = signal('ALL');
  search = signal('');
  form = this.fb.nonNullable.group({
    sku: ['', Validators.required],
    name: ['', Validators.required],
    farmId: ['', Validators.required],
    category: ['FEED', Validators.required],
    quantityOnHand: [0, Validators.required],
    reorderPoint: [0, Validators.required],
    unit: ['kg', Validators.required],
    lotCode: [''],
    expiresOn: [''],
    bestBeforeOn: [''],
    manufacturedOn: [''],
    openDays: [''],
    genericName: [''],
    description: [''],
    unitCost: [''],
    supplierName: [''],
    storeName: ['']
  });
  receiptForm = this.fb.nonNullable.group({
    itemId: ['', Validators.required],
    quantity: [1, [Validators.required, Validators.min(0.01)]],
    reason: ['Delivery', Validators.required],
    lotCode: [''],
    expiresOn: [''],
    bestBeforeOn: [''],
    manufacturedOn: [''],
    purchaseOrder: [''],
    inspection: ['PASSED'],
    openDays: [''],
    unitCost: [''],
    supplierName: [''],
    storeName: ['']
  });
  rows = computed(() => {
    const query = this.search().trim().toLowerCase();
    return this.store.movements().filter(row => {
      if (row.movementType !== 'RECEIPT') {
        return false;
      }
      if (this.farmFilter() !== 'ALL' && row.farmId !== this.farmFilter()) {
        return false;
      }
      if (!query) {
        return true;
      }
      return `${row.itemName} ${row.sku} ${row.farmName} ${row.supplierName || ''} ${row.storeName || ''} ${row.actorName} ${row.lotCode || ''}`.toLowerCase().includes(query);
    });
  });
  farms = computed(() => this.store.farms().map(farm => ({ id: farm.id, name: farm.name })).sort((a, b) => a.name.localeCompare(b.name)));
  productCount = computed(() => new Set(this.rows().map(row => row.sku)).size);
  farmCount = computed(() => new Set(this.rows().map(row => row.farmName)).size);

  constructor() {
    const first = this.store.farms()[0];
    if (first) {
      this.form.controls.farmId.setValue(first.id);
    }
    const item = this.store.items()[0];
    if (item) {
      this.receiptForm.controls.itemId.setValue(item.id);
    }
    const requested = this.route.snapshot.queryParamMap.get('form');
    if (requested === 'product' || requested === 'receipt') {
      this.showForm();
      if (requested === 'product') {
        this.mode.set('new');
      }
    }
  }

  canWrite(): boolean {
    return !!this.session()?.canWriteOperations;
  }

  chip(): string {
    if (this.farmFilter() !== 'ALL') {
      return this.farms().find(farm => farm.id === this.farmFilter())?.name || 'Selected farm';
    }
    return scopeChip(this.store.items().map(item => item.farmName));
  }

  exportCsv(): void {
    downloadCsv('shambanexa-receipts.csv',
      ['when', 'item', 'sku', 'farm', 'quantity', 'unit', 'batch', 'expiry', 'supplier', 'store', 'recorded_by', 'reason'],
      this.rows().map(row => [
        row.occurredAt, row.itemName, row.sku, row.farmName, String(row.quantityDelta), row.unit,
        row.lotCode || '', row.expiresOn || '', row.supplierName || '', row.storeName || '', row.actorName, row.reason
      ]));
    this.toast.show(this.rows().length ? 'Receipt export downloaded.' : 'Export downloaded. No receipts are stored.');
  }

  printPage(): void {
    window.print();
  }

  showForm(): void {
    this.error.set('');
    this.mode.set(this.store.items().length ? 'existing' : 'new');
    const farm = this.store.farms()[0];
    if (farm && !this.form.controls.farmId.value) {
      this.form.controls.farmId.setValue(farm.id);
    }
    const item = this.store.items()[0];
    if (item) {
      this.receiptForm.controls.itemId.setValue(item.id);
    }
    this.open.set(true);
  }

  create(): void {
    if (this.busy() || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    if (raw.category === 'MEDICINE' && (!raw.lotCode.trim() || !raw.expiresOn)) {
      this.error.set('Medicines need a batch code and an expiry date.');
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.api.createInventory({
      ...raw,
      lotCode: raw.lotCode.trim() || null,
      expiresOn: raw.expiresOn || null,
      bestBeforeOn: raw.bestBeforeOn || null,
      manufacturedOn: raw.manufacturedOn || null,
      openDays: raw.openDays === '' ? null : Number(raw.openDays),
      genericName: raw.genericName.trim() || null,
      description: raw.description.trim() || null,
      unitCost: this.cost(raw.unitCost),
      supplierName: raw.supplierName.trim() || null,
      storeName: raw.storeName.trim() || null
    }).subscribe({
      next: () => this.done('Stock receipt recorded.'),
      error: err => this.fail(err, 'Could not record the receipt')
    });
  }

  receive(): void {
    if (this.busy() || this.receiptForm.invalid) {
      this.receiptForm.markAllAsTouched();
      return;
    }
    const raw = this.receiptForm.getRawValue();
    const item = this.store.items().find(row => row.id === raw.itemId);
    if (item?.category === 'MEDICINE' && !(raw.lotCode.trim() || item.lotCode)) {
      this.error.set('Medicines need a batch code and an expiry date.');
      return;
    }
    if (item?.category === 'MEDICINE' && !(raw.expiresOn || item.expiresOn)) {
      this.error.set('Medicines need a batch code and an expiry date.');
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.api.receiveInventory(raw.itemId, {
      quantity: raw.quantity,
      reason: raw.reason,
      lotCode: raw.lotCode.trim() || null,
      expiresOn: raw.expiresOn || null,
      bestBeforeOn: raw.bestBeforeOn || null,
      manufacturedOn: raw.manufacturedOn || null,
      purchaseOrder: raw.purchaseOrder.trim() || null,
      inspection: raw.inspection || null,
      openDays: raw.openDays === '' ? null : Number(raw.openDays),
      unitCost: this.cost(raw.unitCost),
      supplierName: raw.supplierName.trim() || null,
      storeName: raw.storeName.trim() || null
    }).subscribe({
      next: () => this.done('Stock receipt recorded.'),
      error: err => this.fail(err, 'Could not record the receipt')
    });
  }

  @HostListener('document:keydown.escape')
  close(): void {
    if (!this.busy()) {
      this.open.set(false);
    }
  }

  private cost(value: string): number | null {
    const text = value.trim();
    return text ? Number(text) : null;
  }

  private done(message: string): void {
    this.busy.set(false);
    this.open.set(false);
    this.toast.show(message);
    this.store.refresh();
  }

  private fail(err: unknown, fallback: string): void {
    this.busy.set(false);
    this.error.set(apiError(err, fallback));
  }
}
