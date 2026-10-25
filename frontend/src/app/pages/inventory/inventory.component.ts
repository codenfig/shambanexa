import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { InventoryItem } from '../../core/models';
import { apiError, formatNumber } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { STOCK_COLORS, STOCK_GROUPS, categoryLabel, downloadCsv, expired, expiring, expiryLabel, money, statusClass, statusOf } from './inventory.format';
import { InventoryStore } from './inventory.store';

const PAGE_SIZE = 8;

@Component({
  selector: 'app-inventory',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './inventory.component.html',
  styleUrl: './inventory.component.css'
})
export class InventoryComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  readonly store = inject(InventoryStore);
  readonly groups = STOCK_GROUPS;
  readonly label = categoryLabel;
  readonly expiry = expiryLabel;
  readonly statusOf = statusOf;
  readonly statusClass = statusClass;
  readonly fmt = formatNumber;
  session = this.auth.session;
  search = signal('');
  farmFilter = signal('ALL');
  statusFilter = signal('ALL');
  storeFilter = signal('ALL');
  supplierFilter = signal('ALL');
  expiryFilter = signal('ALL');
  category = signal('ALL');
  page = signal(1);
  issueOpen = signal(false);
  busy = signal(false);
  error = signal('');
  issueForm = this.fb.nonNullable.group({
    itemId: ['', Validators.required],
    quantity: [1, [Validators.required, Validators.min(0.01)]],
    reason: ['', Validators.required]
  });

  scoped = computed(() => {
    const farm = this.farmFilter();
    return this.store.items().filter(item => farm === 'ALL' || item.farmId === farm);
  });
  filtered = computed(() => {
    const query = this.search().trim().toLowerCase();
    const category = this.category();
    const status = this.statusFilter();
    const store = this.storeFilter();
    const supplier = this.supplierFilter();
    const expiry = this.expiryFilter();
    return this.scoped().filter(item => {
      if (category !== 'ALL' && item.category !== category) {
        return false;
      }
      if (store !== 'ALL' && (item.storeName || '') !== store) {
        return false;
      }
      if (supplier !== 'ALL' && (item.supplierName || '') !== supplier) {
        return false;
      }
      if (expiry === 'SOON' && !expiring(item)) {
        return false;
      }
      if (expiry === 'EXPIRED' && !expired(item)) {
        return false;
      }
      if (expiry === 'NONE' && item.expiresOn) {
        return false;
      }
      if (status === 'LOW' && !item.lowStock) {
        return false;
      }
      if (status === 'OUT' && item.available > 0) {
        return false;
      }
      if (status === 'EXPIRING' && !expiring(item)) {
        return false;
      }
      if (status === 'EXPIRED' && !expired(item)) {
        return false;
      }
      if (status === 'IN' && (item.available <= 0 || item.lowStock || expiring(item) || expired(item))) {
        return false;
      }
      if (!query) {
        return true;
      }
      return `${item.name} ${item.sku} ${item.lotCode || ''} ${item.supplierName || ''} ${item.storeName || ''}`.toLowerCase().includes(query);
    });
  });
  pageCount = computed(() => Math.max(1, Math.ceil(this.filtered().length / PAGE_SIZE)));
  paged = computed(() => {
    const page = Math.min(this.page(), this.pageCount());
    return this.filtered().slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  });
  farmOptions = computed(() => {
    const seen = new Map<string, string>();
    for (const item of this.store.items()) {
      seen.set(item.farmId, item.farmName);
    }
    return [...seen.entries()].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name));
  });
  storeOptions = computed(() => [...new Set(this.scoped().map(item => item.storeName).filter((name): name is string => !!name))].sort());
  supplierOptions = computed(() => [...new Set(this.scoped().map(item => item.supplierName).filter((name): name is string => !!name))].sort());
  lowCount = computed(() => this.scoped().filter(item => item.lowStock).length);
  expiringCount = computed(() => this.scoped().filter(item => expiring(item)).length);
  valued = computed(() => this.scoped().filter(item => item.stockValue != null));
  stockValue = computed(() => this.valued().reduce((sum, item) => sum + Number(item.stockValue), 0));
  attention = computed(() => this.scoped().filter(item => item.lowStock || item.available <= 0 || expiring(item) || expired(item)).slice(0, 5));
  pendingCount = computed(() => this.store.adjustments().filter(row => row.status === 'PENDING').length);
  breakdown = computed(() => {
    const total = this.scoped().length || 1;
    const counts = new Map<string, number>();
    for (const item of this.scoped()) {
      counts.set(item.category, (counts.get(item.category) || 0) + 1);
    }
    return [...counts.entries()]
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
      .map(([category, count]) => ({
        category,
        label: categoryLabel(category),
        count,
        pct: Math.round((count / total) * 100),
        color: STOCK_COLORS[category] || '#64748b'
      }));
  });

  constructor() {
    const view = this.route.snapshot.queryParamMap.get('view');
    const fragment = this.route.snapshot.fragment;
    const redirect: Record<string, string> = {
      low: 'low',
      receipts: 'receipts',
      transfers: 'transfers',
      suppliers: 'suppliers'
    };
    if (view && redirect[view]) {
      this.router.navigate(['/inventory', redirect[view]], { replaceUrl: true });
      return;
    }
    if (fragment === 'adjustments') {
      this.router.navigate(['/inventory/adjustments'], { replaceUrl: true });
    }
  }

  canWrite(): boolean {
    return !!this.session()?.canWriteOperations;
  }

  scopeName(): string {
    const farm = this.farmFilter();
    if (farm !== 'ALL') {
      return this.farmOptions().find(item => item.id === farm)?.name || 'Selected farm';
    }
    const names = [...new Set(this.scoped().map(item => item.farmName))];
    return names.length === 1 ? names[0] : 'All farms';
  }

  valueNote(): string {
    const missing = this.scoped().length - this.valued().length;
    if (!this.scoped().length) {
      return 'No stock lines in this view';
    }
    if (!this.valued().length) {
      return 'No unit cost is stored';
    }
    if (missing) {
      return `${missing} item${missing === 1 ? '' : 's'} have no unit cost`;
    }
    return 'On-hand quantity × unit cost';
  }

  money(value: number | null): string {
    return money(value, this.store.currencyCode());
  }

  ring(): string {
    const parts = this.breakdown();
    if (!this.scoped().length) {
      return 'conic-gradient(#e7eee9 0 100%)';
    }
    let cursor = 0;
    const stops = parts.map(part => {
      const start = cursor;
      cursor += (part.count / this.scoped().length) * 100;
      return `${part.color} ${start}% ${cursor}%`;
    });
    return `conic-gradient(${stops.join(', ')})`;
  }

  rangeLabel(): string {
    const total = this.filtered().length;
    if (!total) {
      return '0 items';
    }
    const page = Math.min(this.page(), this.pageCount());
    const start = (page - 1) * PAGE_SIZE + 1;
    return `${start}–${Math.min(total, page * PAGE_SIZE)} of ${total}`;
  }

  setSearch(value: string): void {
    this.search.set(value);
    this.page.set(1);
  }

  setFarm(value: string): void {
    this.farmFilter.set(value);
    this.category.set('ALL');
    this.storeFilter.set('ALL');
    this.supplierFilter.set('ALL');
    this.page.set(1);
  }

  setCategory(value: string): void {
    this.category.set(value);
    this.page.set(1);
  }

  setStatus(value: string): void {
    this.statusFilter.set(value);
    this.page.set(1);
  }

  setStore(value: string): void {
    this.storeFilter.set(value);
    this.page.set(1);
  }

  setSupplier(value: string): void {
    this.supplierFilter.set(value);
    this.page.set(1);
  }

  setExpiry(value: string): void {
    this.expiryFilter.set(value);
    this.page.set(1);
  }

  clearFilters(): void {
    this.search.set('');
    this.farmFilter.set('ALL');
    this.category.set('ALL');
    this.statusFilter.set('ALL');
    this.storeFilter.set('ALL');
    this.supplierFilter.set('ALL');
    this.expiryFilter.set('ALL');
    this.page.set(1);
  }

  shiftPage(step: number): void {
    const next = this.page() + step;
    if (next >= 1 && next <= this.pageCount()) {
      this.page.set(next);
    }
  }

  exportCsv(): void {
    downloadCsv('shambanexa-inventory.csv',
      ['name', 'sku', 'category', 'farm', 'store', 'supplier', 'quantity', 'unit', 'reorder', 'available', 'batch', 'expires', 'unit_cost', 'stock_value', 'currency', 'status'],
      this.filtered().map(item => [
        item.name, item.sku, item.category, item.farmName, item.storeName || '', item.supplierName || '',
        String(item.quantityOnHand), item.unit, String(item.reorderPoint), String(item.available),
        item.lotCode || '', item.expiresOn || '', item.unitCost == null ? '' : String(item.unitCost),
        item.stockValue == null ? '' : String(item.stockValue), item.stockValue == null ? '' : this.store.currencyCode(),
        statusOf(item)
      ]));
    this.toast.show(this.filtered().length ? 'Inventory export downloaded.' : 'Export downloaded. No stock lines matched the filters.');
  }

  openIssue(item?: InventoryItem): void {
    const row = item || this.scoped()[0];
    if (!row) {
      this.toast.show('No stock items to issue.');
      return;
    }
    this.issueForm.controls.itemId.setValue(row.id);
    this.error.set('');
    this.issueOpen.set(true);
  }

  issue(): void {
    if (this.busy() || this.issueForm.invalid) {
      this.issueForm.markAllAsTouched();
      return;
    }
    const raw = this.issueForm.getRawValue();
    this.busy.set(true);
    this.error.set('');
    this.api.issueInventory(raw.itemId, { quantity: raw.quantity, reason: raw.reason }).subscribe({
      next: () => {
        this.busy.set(false);
        this.issueOpen.set(false);
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
      this.issueOpen.set(false);
    }
  }
}
