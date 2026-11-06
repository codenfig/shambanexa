import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { InventoryAdjustment } from '../../core/models';
import { apiError, formatNumber } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { downloadCsv, scopeChip, signed, whenLabel } from './inventory.format';
import { InventoryStore } from './inventory.store';

@Component({
  selector: 'app-stock-adjustments',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './adjustments.component.html',
  styleUrl: './inventory.component.css'
})
export class StockAdjustmentsComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);
  readonly store = inject(InventoryStore);
  readonly fmt = formatNumber;
  readonly when = whenLabel;
  readonly signed = signed;
  session = this.auth.session;
  open = signal(false);
  busy = signal(false);
  error = signal('');
  search = signal('');
  statusFilter = signal('ALL');
  selectedId = signal<string | null>(null);
  form = this.fb.nonNullable.group({
    itemId: ['', Validators.required],
    delta: [0, Validators.required],
    reason: ['Physical count', Validators.required],
    reasonCode: [''],
    notes: ['']
  });
  pending = computed(() => this.store.adjustments().filter(row => row.status === 'PENDING'));
  filtered = computed(() => {
    const query = this.search().trim().toLowerCase();
    const status = this.statusFilter();
    return this.store.adjustments().filter(row => {
      if (status !== 'ALL' && row.status !== status) {
        return false;
      }
      if (!query) {
        return true;
      }
      return `${row.itemName} ${row.sku} ${row.farmName} ${row.reason} ${row.requestedByName}`.toLowerCase().includes(query);
    });
  });
  chosen = computed(() => this.pending().find(row => row.id === this.selectedId()) || this.pending()[0] || null);

  constructor() {
    const item = this.route.snapshot.queryParamMap.get('item') || this.store.items()[0]?.id || '';
    if (item) {
      this.form.controls.itemId.setValue(item);
      if (this.route.snapshot.queryParamMap.get('item')) {
        this.open.set(true);
      }
    }
  }

  canWrite(): boolean {
    return !!this.session()?.canWriteOperations;
  }

  canApprove(): boolean {
    return !!this.session()?.canManageAccess;
  }

  chip(): string {
    return scopeChip(this.store.items().map(item => item.farmName));
  }

  pick(row: InventoryAdjustment): void {
    if (row.status === 'PENDING') {
      this.selectedId.set(row.id);
    }
  }

  exportCsv(): void {
    downloadCsv('shambanexa-adjustments.csv',
      ['item', 'sku', 'farm', 'delta', 'unit', 'reason', 'status', 'requested_by', 'requested_at', 'decided_by'],
      this.filtered().map(row => [
        row.itemName, row.sku, row.farmName, String(row.delta), row.unit, row.reason, row.status,
        row.requestedByName, row.requestedAt, row.decidedByName || ''
      ]));
    this.toast.show(this.filtered().length ? 'Adjustment export downloaded.' : 'Export downloaded. No adjustments are stored.');
  }

  statusLabel(status: string): string {
    if (status === 'APPROVED') {
      return 'Approved';
    }
    if (status === 'REJECTED') {
      return 'Rejected';
    }
    return 'Pending';
  }

  showForm(): void {
    if (!this.store.items().length) {
      this.toast.show('No stock items to adjust.');
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
      return;
    }
    const raw = this.form.getRawValue();
    this.busy.set(true);
    this.error.set('');
    this.api.adjustInventory(raw.itemId, { delta: raw.delta, reason: raw.reason, reasonCode: raw.reasonCode || null, notes: raw.notes.trim() || null }).subscribe({
      next: () => {
        this.busy.set(false);
        this.open.set(false);
        this.toast.show('Adjustment submitted for approval. Stock is unchanged until it is approved.');
        this.store.refresh();
      },
      error: err => {
        this.busy.set(false);
        this.error.set(apiError(err, 'Adjustment rejected'));
      }
    });
  }

  decide(row: InventoryAdjustment, approve: boolean): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api.decideAdjustment(row.id, approve).subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.show(approve ? 'Adjustment approved and posted to the ledger.' : 'Adjustment rejected. Stock is unchanged.');
        this.store.refresh();
      },
      error: err => {
        this.busy.set(false);
        this.toast.show(apiError(err, 'Could not decide the adjustment'));
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
