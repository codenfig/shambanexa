import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Farm } from '../../core/models';
import { apiError, formatNumber } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { downloadCsv, movementTitle, scopeChip, signed, whenLabel } from './inventory.format';
import { InventoryStore } from './inventory.store';

@Component({
  selector: 'app-stock-transfers',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './transfers.component.html',
  styleUrl: './inventory.component.css'
})
export class StockTransfersComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);
  readonly store = inject(InventoryStore);
  readonly fmt = formatNumber;
  readonly when = whenLabel;
  readonly signed = signed;
  readonly title = movementTitle;
  session = this.auth.session;
  open = signal(false);
  busy = signal(false);
  error = signal('');
  search = signal('');
  farmFilter = signal('ALL');
  form = this.fb.nonNullable.group({
    itemId: ['', Validators.required],
    destinationFarmId: ['', Validators.required],
    quantity: [1, [Validators.required, Validators.min(0.01)]],
    reason: ['Farm transfer', Validators.required],
    destinationStore: ['']
  });
  rows = computed(() => {
    const query = this.search().trim().toLowerCase();
    return this.store.movements().filter(row => {
      if (row.movementType !== 'TRANSFER_IN' && row.movementType !== 'TRANSFER_OUT') {
        return false;
      }
      if (this.farmFilter() !== 'ALL' && row.farmId !== this.farmFilter()) {
        return false;
      }
      if (!query) {
        return true;
      }
      return `${row.itemName} ${row.sku} ${row.farmName} ${row.reason} ${row.actorName} ${row.storeName || ''}`.toLowerCase().includes(query);
    });
  });
  sent = computed(() => this.rows().filter(row => row.movementType === 'TRANSFER_OUT').length);
  received = computed(() => this.rows().filter(row => row.movementType === 'TRANSFER_IN').length);
  farmsTouched = computed(() => new Set(this.rows().map(row => row.farmId)).size);

  canWrite(): boolean {
    return !!this.session()?.canWriteOperations;
  }

  chip(): string {
    if (this.farmFilter() !== 'ALL') {
      return this.store.farms().find(farm => farm.id === this.farmFilter())?.name || 'Selected farm';
    }
    return scopeChip(this.store.items().map(item => item.farmName));
  }

  exportCsv(): void {
    downloadCsv('shambanexa-transfers.csv',
      ['when', 'movement', 'item', 'sku', 'farm', 'quantity', 'unit', 'store', 'reason', 'recorded_by'],
      this.rows().map(row => [
        row.occurredAt, row.movementType, row.itemName, row.sku, row.farmName, String(row.quantityDelta),
        row.unit, row.storeName || '', row.reason, row.actorName
      ]));
    this.toast.show(this.rows().length ? 'Transfer export downloaded.' : 'Export downloaded. No transfers are stored.');
  }

  otherFarms(itemId: string): Farm[] {
    const item = this.store.items().find(row => row.id === itemId);
    return this.store.farms().filter(farm => farm.id !== item?.farmId);
  }

  pickDestination(): void {
    const farms = this.otherFarms(this.form.controls.itemId.value);
    this.form.controls.destinationFarmId.setValue(farms.length ? farms[0].id : '');
  }

  showForm(): void {
    if (!this.store.items().length) {
      this.toast.show('No stock items to transfer.');
      return;
    }
    this.form.controls.itemId.setValue(this.store.items()[0].id);
    this.pickDestination();
    this.error.set('');
    this.open.set(true);
  }

  submit(): void {
    if (this.busy() || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    this.busy.set(true);
    this.error.set('');
    this.api.transferInventory(raw.itemId, {
      destinationFarmId: raw.destinationFarmId,
      quantity: raw.quantity,
      reason: raw.reason,
      destinationStore: raw.destinationStore.trim() || null
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.open.set(false);
        this.toast.show('Transfer recorded.');
        this.store.refresh();
      },
      error: err => {
        this.busy.set(false);
        this.error.set(apiError(err, 'Could not record the transfer'));
      }
    });
  }

  @HostListener('document:keydown.escape')
  close(): void {
    if (!this.busy()) {
      this.open.set(false);
    }
  }
}
