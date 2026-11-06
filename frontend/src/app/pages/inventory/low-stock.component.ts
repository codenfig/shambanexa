import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { StockAlert } from '../../core/models';
import { apiError, formatNumber } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { categoryLabel, downloadCsv, expiryLabel, scopeChip } from './inventory.format';
import { InventoryStore } from './inventory.store';

@Component({
  selector: 'app-low-stock',
  imports: [RouterLink],
  templateUrl: './low-stock.component.html',
  styleUrl: './inventory.component.css'
})
export class LowStockComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);
  readonly store = inject(InventoryStore);
  readonly fmt = formatNumber;
  readonly label = categoryLabel;
  readonly expiry = expiryLabel;
  session = this.auth.session;
  search = signal('');
  kind = signal('ALL');
  selectedId = signal<string | null>(null);
  busy = signal(false);
  readonly kinds = [
    ['ALL', 'All Alerts'],
    ['LOW', 'Low Stock'],
    ['OUT', 'Out of Stock'],
    ['EXPIRING', 'Expiring Soon'],
    ['EXPIRED', 'Expired'],
    ['BEST_BEFORE', 'Best Before'],
    ['OPEN_USE', 'After opening'],
    ['QUARANTINED', 'Quarantined']
  ];
  rows = computed(() => {
    const query = this.search().trim().toLowerCase();
    const kind = this.kind();
    return this.store.alerts().filter(row => {
      if (kind !== 'ALL' && row.kind !== kind) {
        return false;
      }
      if (!query) {
        return true;
      }
      return `${row.productName} ${row.lotCode || ''} ${row.farmName} ${row.category} ${row.kind}`.toLowerCase().includes(query);
    });
  });
  selected = computed(() => this.rows().find(row => this.key(row) === this.selectedId()) || this.rows()[0] || null);

  canWrite(): boolean {
    return !!this.session()?.canWriteOperations;
  }

  chip(): string {
    return scopeChip(this.rows().map(row => row.farmName));
  }

  key(row: StockAlert): string {
    return `${row.kind}:${row.batchId || row.itemId}`;
  }

  pick(row: StockAlert): void {
    this.selectedId.set(this.key(row));
  }

  kindLabel(code: string): string {
    return this.kinds.find(item => item[0] === code)?.[1] || code;
  }

  reorder(itemId: string): string {
    const point = this.store.items().find(item => item.id === itemId)?.reorderPoint;
    return point == null ? '—' : this.fmt(point, point % 1 ? 2 : 0);
  }

  acknowledge(row: StockAlert): void {
    if (!row.batchId || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api.acknowledgeInventoryAlert({ batchId: row.batchId, alertKind: row.kind }).subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.show('Alert acknowledged.');
        this.store.refresh();
      },
      error: err => {
        this.busy.set(false);
        this.toast.show(apiError(err, 'Could not acknowledge the alert'));
      }
    });
  }

  exportCsv(): void {
    downloadCsv('shambanexa-stock-alerts.csv',
      ['item', 'category', 'farm', 'batch', 'quantity', 'use_by', 'days_left', 'kind', 'severity', 'action'],
      this.rows().map(row => [
        row.productName, row.category, row.farmName, row.lotCode || '', String(row.quantity),
        row.useBy || '', row.daysLeft == null ? '' : String(row.daysLeft), row.kind, row.severity, row.action
      ]));
  }
}

