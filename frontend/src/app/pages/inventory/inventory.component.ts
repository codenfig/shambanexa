import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { InventoryItem, StockBatch } from '../../core/models';
import { apiError, formatNumber } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { STOCK_GROUPS, batchStatusLabel, categoryLabel, downloadCsv, expired, expiring, expiryLabel, money, statusClass, statusOf, whenLabel } from './inventory.format';
import { categoryPictures, stockPicture } from '../../core/catalog-images';
import { InventoryStore } from './inventory.store';

const PAGE_SIZE = 12;

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
  readonly pictures = categoryPictures;
  readonly expiry = expiryLabel;
  readonly when = whenLabel;
  readonly statusOf = statusOf;
  readonly statusClass = statusClass;
  readonly batchStatus = batchStatusLabel;
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
  filtersOpen = signal(false);
  chosenId = signal<string | null>(null);
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
  pageCount = computed(() => Math.max(1, Math.ceil(this.visibleBatches().length / PAGE_SIZE)));
  paged = computed(() => {
    const page = Math.min(this.page(), this.pageCount());
    return this.visibleBatches().slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  });
  stockUnits = computed(() => this.scoped().reduce((sum, item) => sum + Number(item.quantityOnHand), 0));
  scopedBatches = computed(() => {
    const farm = this.farmFilter();
    return this.store.batches().filter(batch => (farm === 'ALL' || batch.farmId === farm) && batch.status !== 'DISPOSED');
  });
  expiryBars = computed(() => {
    const rows = this.scopedBatches();
    const count = (codes: string[]) => rows.filter(batch => codes.includes(batch.status)).length;
    const bars = [
      { label: 'Valid', count: count(['VALID']), color: '#1e8330' },
      { label: 'Expiring Soon', count: count(['EXPIRING', 'OPEN_USE']), color: '#e8a317' },
      { label: 'Expired', count: count(['EXPIRED']), color: '#d23b35' },
      { label: 'Best Before', count: count(['BEST_BEFORE']), color: '#94a3b8' }
    ];
    const max = Math.max(1, ...bars.map(bar => bar.count));
    return bars.map(bar => ({ ...bar, height: Math.max(bar.count ? 8 : 0, Math.round((bar.count / max) * 100)) }));
  });
  medicineCount = computed(() => this.scoped().filter(item => item.category === 'MEDICINE').length);
  feedCount = computed(() => this.scoped().filter(item => item.category === 'FEED').length);
  supplyCount = computed(() => this.scoped().filter(item => item.category === 'SUPPLY').length);
  visibleBatches = computed(() => {
    const query = this.search().trim().toLowerCase();
    const category = this.category();
    const farm = this.farmFilter();
    const supplier = this.supplierFilter();
    const store = this.storeFilter();
    const expiry = this.expiryFilter();
    const status = this.statusFilter();
    return this.store.batches().filter(batch => {
      if (batch.status === 'DISPOSED') return false;
      if (category !== 'ALL' && batch.category !== category) return false;
      if (farm !== 'ALL' && batch.farmId !== farm) return false;
      if (store !== 'ALL' && (batch.storeName || '') !== store) return false;
      if (supplier !== 'ALL' && (batch.supplierName || '') !== supplier) return false;
      if (expiry === 'SOON' && batch.status !== 'EXPIRING' && batch.status !== 'BEST_BEFORE' && batch.status !== 'OPEN_USE') return false;
      if (expiry === 'EXPIRED' && batch.status !== 'EXPIRED') return false;
      if (expiry === 'NONE' && batch.expiresOn) return false;
      const item = this.store.items().find(row => row.id === batch.itemId);
      if (status === 'LOW' && !item?.lowStock) return false;
      if (status === 'OUT' && (item?.available || 0) > 0 && batch.quantityRemaining > 0) return false;
      if (status === 'EXPIRING' && batch.status !== 'EXPIRING' && batch.status !== 'BEST_BEFORE' && batch.status !== 'OPEN_USE') return false;
      if (status === 'EXPIRED' && batch.status !== 'EXPIRED') return false;
      if (status === 'IN' && batch.status !== 'VALID') return false;
      if (!query) return true;
      return `${batch.productName} ${batch.sku} ${batch.lotCode} ${batch.productType || ''}`.toLowerCase().includes(query);
    });
  });
  expiredBatchCount = computed(() => this.visibleBatches().filter(batch => batch.status === 'EXPIRED').length);
  quarantinedCount = computed(() => this.visibleBatches().filter(batch => batch.status === 'QUARANTINED').length);
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
  recentReceipts = computed(() => {
    const farm = this.farmFilter();
    return this.store.movements()
      .filter(row => row.movementType === 'RECEIPT' && (farm === 'ALL' || row.farmId === farm))
      .slice(0, 4);
  });
  nearExpiry = computed(() => this.scopedBatches()
    .filter(batch => batch.status === 'EXPIRING' || batch.status === 'OPEN_USE' || (batch.daysLeft != null && batch.daysLeft >= 0 && batch.daysLeft <= 30 && batch.status !== 'EXPIRED'))
    .slice(0, 4));
  lowItems = computed(() => this.scoped().filter(item => item.lowStock || item.available <= 0).slice(0, 4));
  attention = computed(() => this.scoped().filter(item => item.lowStock || item.available <= 0 || expiring(item) || expired(item)).slice(0, 5));
  pendingCount = computed(() => this.store.adjustments().filter(row => row.status === 'PENDING').length);
  composition = computed(() => {
    const total = this.scoped().length || 1;
    const order = ['MEDICINE', 'FEED', 'SUPPLY', 'EQUIPMENT'];
    return order
      .map(category => {
        const count = this.scoped().filter(item => item.category === category).length;
        return { category, label: this.categoryTitle(category), count, pct: Math.round((count / total) * 100), color: this.categoryColor(category) };
      })
      .filter(part => part.count > 0);
  });

  constructor() {
    const view = this.route.snapshot.queryParamMap.get('view');
    const fragment = this.route.snapshot.fragment;
    const redirect: Record<string, string> = {
      low: 'low',
      receipts: 'receipts',
      issues: 'issues',
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

  label(code: string): string {
    const stored = (this.store.lists()['INV_CATEGORY'] || []).find(row => row.code === code);
    return stored?.name || categoryLabel(code);
  }

  categoryTitle(code: string): string {
    if (code === 'MEDICINE') return 'Veterinary Medicines';
    if (code === 'FEED') return 'Animal Feeds';
    if (code === 'SUPPLY') return 'Agricultural Supplies';
    if (code === 'EQUIPMENT') return 'Equipment';
    return this.label(code);
  }

  categoryColor(code: string): string {
    if (code === 'MEDICINE') return '#1e8330';
    if (code === 'FEED') return '#e8a317';
    if (code === 'SUPPLY') return '#3b82f6';
    if (code === 'EQUIPMENT') return '#64748b';
    return '#94a3b8';
  }

  categoryTint(code: string): string {
    if (code === 'MEDICINE') return '#e7f6ec';
    if (code === 'FEED') return '#fff6df';
    if (code === 'SUPPLY') return '#e8f1fb';
    if (code === 'EQUIPMENT') return '#f1f5f9';
    return '#f3f6f4';
  }

  share(count: number): number {
    const total = this.scoped().length;
    return total ? Math.round((count / total) * 100) : 0;
  }

  productImage(batch: StockBatch): string | null {
    return stockPicture(batch.category, batch.productType);
  }

  itemOf(batch: StockBatch): InventoryItem | undefined {
    return this.store.items().find(item => item.id === batch.itemId);
  }

  chosenBatch(): StockBatch | null {
    return this.store.batches().find(batch => batch.id === this.chosenId()) || null;
  }

  openBatch(batch: StockBatch): void {
    this.chosenId.set(batch.id);
  }

  money(value: number | null): string {
    return money(value, this.store.currencyCode());
  }

  ring(): string {
    const parts = this.composition();
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
    const total = this.visibleBatches().length;
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
      this.chosenId.set(null);
    }
  }
}
