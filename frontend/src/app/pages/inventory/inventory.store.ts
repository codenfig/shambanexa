import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { Farm, InventoryAdjustment, InventoryItem, InventoryMovement, InventoryReport, MasterRecord, StockAlert, StockBatch, SupplierRecord } from '../../core/models';

@Injectable({ providedIn: 'root' })
export class InventoryStore {
  private api = inject(ApiService);
  items = signal<InventoryItem[]>([]);
  movements = signal<InventoryMovement[]>([]);
  adjustments = signal<InventoryAdjustment[]>([]);
  batches = signal<StockBatch[]>([]);
  alerts = signal<StockAlert[]>([]);
  suppliers = signal<SupplierRecord[]>([]);
  report = signal<InventoryReport | null>(null);
  lists = signal<Record<string, MasterRecord[]>>({});
  farms = signal<Farm[]>([]);
  currencyCode = signal('USD');
  loading = signal(true);
  loadError = signal('');

  constructor() {
    this.api.farms().subscribe({
      next: farms => this.farms.set(farms),
      error: () => this.farms.set([])
    });
    this.api.settings().subscribe({
      next: settings => this.currencyCode.set(settings.currencyCode || 'USD'),
      error: () => undefined
    });
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.loadError.set('');
    this.api.inventory().subscribe({
      next: items => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set('Unable to load data.');
      }
    });
    this.api.inventoryLedger().subscribe({
      next: rows => this.movements.set(rows),
      error: () => this.movements.set([])
    });
    this.api.inventoryAdjustments().subscribe({
      next: rows => this.adjustments.set(rows),
      error: () => this.adjustments.set([])
    });
    this.api.inventoryBatches().subscribe({
      next: rows => this.batches.set(rows),
      error: () => this.batches.set([])
    });
    this.api.inventoryAlerts().subscribe({
      next: rows => this.alerts.set(rows),
      error: () => this.alerts.set([])
    });
    this.api.supplierRecords().subscribe({
      next: rows => this.suppliers.set(rows),
      error: () => this.suppliers.set([])
    });
    this.api.inventoryReport().subscribe({
      next: row => this.report.set(row),
      error: () => this.report.set(null)
    });
    const kinds = ['INV_CATEGORY', 'INV_TYPE', 'INV_MED_CLASS', 'INV_FEED_TYPE', 'INV_UNIT', 'INV_STORAGE', 'INV_REASON', 'INV_WINDOW'];
    for (const kind of kinds) {
      this.api.masterPage(kind, { size: 50 }).subscribe({
        next: page => this.lists.update(current => ({ ...current, [kind]: page.records })),
        error: () => undefined
      });
    }
  }
}
