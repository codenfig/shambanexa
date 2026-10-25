import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { Farm, InventoryAdjustment, InventoryItem, InventoryMovement } from '../../core/models';

@Injectable({ providedIn: 'root' })
export class InventoryStore {
  private api = inject(ApiService);
  items = signal<InventoryItem[]>([]);
  movements = signal<InventoryMovement[]>([]);
  adjustments = signal<InventoryAdjustment[]>([]);
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
  }
}
