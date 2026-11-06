import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { apiError, formatNumber } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { downloadCsv, scopeChip, signed, whenLabel } from './inventory.format';
import { InventoryStore } from './inventory.store';

@Component({
  selector: 'app-stock-issues',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './issues.component.html',
  styleUrl: './inventory.component.css'
})
export class StockIssuesComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);
  readonly store = inject(InventoryStore);
  readonly fmt = formatNumber;
  readonly when = whenLabel;
  readonly signed = signed;
  session = this.auth.session;
  open = signal(false);
  busy = signal(false);
  error = signal('');
  search = signal('');
  farmFilter = signal('ALL');
  form = this.fb.nonNullable.group({
    itemId: ['', Validators.required],
    quantity: [1, [Validators.required, Validators.min(0.01)]],
    reason: ['', Validators.required]
  });
  rows = computed(() => {
    const query = this.search().trim().toLowerCase();
    return this.store.movements().filter(row => {
      if (row.movementType !== 'ISSUE') {
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
  farms = computed(() => this.store.farms().map(farm => ({ id: farm.id, name: farm.name })).sort((a, b) => a.name.localeCompare(b.name)));

  constructor() {
    const item = this.store.items()[0];
    if (item) {
      this.form.controls.itemId.setValue(item.id);
    }
  }

  canWrite(): boolean {
    return !!this.session()?.canWriteOperations;
  }

  chip(): string {
    if (this.farmFilter() !== 'ALL') {
      return this.farms().find(farm => farm.id === this.farmFilter())?.name || 'Selected farm';
    }
    return scopeChip(this.rows().map(row => row.farmName));
  }

  exportCsv(): void {
    downloadCsv('shambanexa-issues.csv',
      ['when', 'item', 'sku', 'farm', 'quantity', 'unit', 'store', 'reason', 'recorded_by'],
      this.rows().map(row => [
        row.occurredAt, row.itemName, row.sku, row.farmName, String(row.quantityDelta),
        row.unit, row.storeName || '', row.reason, row.actorName
      ]));
    this.toast.show(this.rows().length ? 'Issue export downloaded.' : 'Export downloaded. No issues are stored.');
  }

  showForm(): void {
    if (!this.store.items().length) {
      this.toast.show('No stock items to issue.');
      return;
    }
    if (!this.form.controls.itemId.value) {
      this.form.controls.itemId.setValue(this.store.items()[0].id);
    }
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
    this.api.issueInventory(raw.itemId, { quantity: raw.quantity, reason: raw.reason }).subscribe({
      next: () => {
        this.busy.set(false);
        this.open.set(false);
        this.form.patchValue({ quantity: 1, reason: '' });
        this.toast.show('Stock issue recorded.');
        this.store.refresh();
      },
      error: err => {
        this.busy.set(false);
        this.error.set(apiError(err, 'Could not record the issue'));
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
