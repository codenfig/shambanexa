import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { InventoryItem } from '../../core/models';
import { formatNumber } from '../../core/format';
import { categoryLabel, downloadCsv, expiryLabel, scopeChip, statusClass, statusOf } from './inventory.format';
import { InventoryStore } from './inventory.store';

@Component({
  selector: 'app-low-stock',
  imports: [RouterLink],
  templateUrl: './low-stock.component.html',
  styleUrl: './inventory.component.css'
})
export class LowStockComponent {
  private auth = inject(AuthService);
  readonly store = inject(InventoryStore);
  readonly fmt = formatNumber;
  readonly label = categoryLabel;
  readonly expiry = expiryLabel;
  readonly statusOf = statusOf;
  readonly statusClass = statusClass;
  session = this.auth.session;
  search = signal('');
  selectedId = signal<string | null>(null);
  rows = computed(() => {
    const query = this.search().trim().toLowerCase();
    return this.store.items().filter(item => {
      if (!item.lowStock) {
        return false;
      }
      if (!query) {
        return true;
      }
      return `${item.name} ${item.sku} ${item.farmName} ${item.category} ${item.lotCode || ''}`.toLowerCase().includes(query);
    });
  });
  outCount = computed(() => this.rows().filter(item => item.available <= 0).length);
  selected = computed(() => this.rows().find(item => item.id === this.selectedId()) || this.rows()[0] || null);

  canWrite(): boolean {
    return !!this.session()?.canWriteOperations;
  }

  chip(): string {
    return scopeChip(this.rows().map(item => item.farmName));
  }

  pick(item: InventoryItem): void {
    this.selectedId.set(item.id);
  }

  exportCsv(): void {
    downloadCsv('shambanexa-low-stock.csv',
      ['item', 'sku', 'category', 'farm', 'available', 'unit', 'reorder', 'on_hand', 'reserved', 'batch', 'expiry', 'status'],
      this.rows().map(item => [
        item.name, item.sku, item.category, item.farmName, String(item.available), item.unit,
        String(item.reorderPoint), String(item.quantityOnHand), String(item.reservedQuantity),
        item.lotCode || '', item.expiresOn || '', statusOf(item)
      ]));
  }
}
