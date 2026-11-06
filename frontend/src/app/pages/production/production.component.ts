import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { chartSlice } from '../../core/chart-colors';
import { Farm, FishCycle, ProductionRecord } from '../../core/models';
import { apiError, formatNumber } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

@Component({
  selector: 'app-production',
  imports: [ReactiveFormsModule, PageHeaderComponent, RouterLink],
  templateUrl: './production.component.html',
  styleUrl: './production.component.css'
})
export class ProductionComponent {
  private api = inject(ApiService);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  farms = signal<Farm[]>([]);
  records = signal<ProductionRecord[]>([]);
  cycles = signal<FishCycle[]>([]);
  loading = signal(true);
  loadError = signal('');
  busy = signal(false);
  error = signal('');
  formOpen = signal(false);
  page = signal(0);
  readonly pageSize = 8;
  fmt = formatNumber;
  totals = computed(() => {
    const sum = (type: string) => this.records()
      .filter(row => row.productType === type)
      .reduce((total, row) => total + Number(row.quantity), 0);
    return { milk: sum('MILK'), eggs: sum('EGGS'), fish: sum('FISH'), count: this.records().length };
  });
  summaryLine = computed(() => {
    if (this.loading() && this.records().length === 0) {
      return 'Milk, eggs, and fish recorded for farms in your scope.';
    }
    const totals = this.totals();
    const parts = [
      totals.milk ? `${formatNumber(totals.milk)} L of milk` : '',
      totals.eggs ? `${formatNumber(totals.eggs)} eggs` : '',
      totals.fish ? `${formatNumber(totals.fish)} fish` : ''
    ].filter(Boolean);
    if (parts.length === 0) {
      return 'No production quantity is recorded.';
    }
    return `${parts.join(', ')} across ${formatNumber(totals.count)} records.`;
  });
  pageCount = computed(() => Math.max(1, Math.ceil(this.records().length / this.pageSize)));
  visiblePage = computed(() => Math.min(this.page(), this.pageCount() - 1));
  pagedRecords = computed(() => {
    const start = this.visiblePage() * this.pageSize;
    return this.records().slice(start, start + this.pageSize);
  });
  pages = computed(() => Array.from({ length: this.pageCount() }, (_, index) => index));
  rangeLabel = computed(() => {
    const total = this.records().length;
    if (total === 0) {
      return 'Showing 0 records';
    }
    const start = this.visiblePage() * this.pageSize + 1;
    const end = Math.min(total, start + this.pageSize - 1);
    return `Showing ${start}–${end} of ${formatNumber(total)} records`;
  });
  milkByFarm = computed(() => {
    const rows = this.records().filter(row => row.productType === 'MILK');
    const total = rows.reduce((sum, row) => sum + Number(row.quantity), 0) || 1;
    const counts = new Map<string, number>();
    for (const row of rows) {
      counts.set(row.farmName, (counts.get(row.farmName) || 0) + Number(row.quantity));
    }
    return [...counts.entries()]
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
      .map(([name, count], index) => ({
        name,
        count,
        share: Math.round((count / total) * 100),
        color: chartSlice(index)
      }));
  });
  milkDonut = computed(() => {
    const items = this.milkByFarm();
    const total = items.reduce((sum, item) => sum + item.count, 0);
    if (!total) {
      return 'conic-gradient(#e7ece8 0 100%)';
    }
    let cursor = 0;
    return `conic-gradient(${items.map(item => {
      const start = cursor;
      cursor += (item.count / total) * 100;
      return `${item.color} ${start}% ${cursor}%`;
    }).join(',')})`;
  });
  form = this.fb.nonNullable.group({
    farmId: ['', Validators.required],
    productType: ['MILK', Validators.required],
    quantity: [0, [Validators.required, Validators.min(0.01)]],
    unit: ['L', Validators.required],
    recordedOn: [new Date().toISOString().slice(0, 10)],
    notes: ['']
  });

  constructor() {
    this.api.farms().subscribe(farms => {
      this.farms.set(farms);
      if (farms[0]) {
        this.form.controls.farmId.setValue(farms[0].id);
      }
    });
    this.api.fishCycles().subscribe(cycles => this.cycles.set(cycles));
    this.route.fragment.subscribe(fragment => {
      if (fragment === 'record') this.formOpen.set(true);
    });
    this.reload();
  }

  openForm(): void {
    this.error.set('');
    this.formOpen.set(true);
  }

  closeForm(): void {
    if (this.busy()) return;
    this.formOpen.set(false);
    if (this.route.snapshot.fragment === 'record') {
      this.router.navigate(['/production'], { replaceUrl: true });
    }
  }

  @HostListener('document:keydown.escape')
  closeOnEscape(): void {
    if (this.formOpen()) this.closeForm();
  }

  submit(): void {
    if (this.busy() || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.api.recordProduction(this.form.getRawValue()).subscribe({
      next: () => {
        this.busy.set(false);
        this.formOpen.set(false);
        this.form.patchValue({ quantity: 0, notes: '' });
        this.toast.show('Production recorded.');
        this.reload();
      },
      error: err => {
        this.busy.set(false);
        this.error.set(apiError(err, 'Could not save production'));
      }
    });
  }

  go(index: number): void {
    this.page.set(Math.min(Math.max(index, 0), this.pageCount() - 1));
  }

  productLabel(type: string): string {
    const names: Record<string, string> = { MILK: 'Milk', EGGS: 'Eggs', FISH: 'Fish' };
    return names[type] || this.word(type);
  }

  word(value: string): string {
    if (!value) {
      return '—';
    }
    return value.charAt(0) + value.slice(1).toLowerCase();
  }

  dateLabel(value: string): string {
    const day = value.slice(0, 10);
    return new Intl.DateTimeFormat('en-GB', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      timeZone: 'UTC'
    }).format(new Date(`${day}T00:00:00Z`));
  }

  progress(stocked: number, live: number): number {
    const base = Number(stocked);
    if (!base) return 0;
    return Math.max(0, Math.min(100, Math.round((Number(live) / base) * 100)));
  }

  reload(): void {
    this.loading.set(true);
    this.loadError.set('');
    this.api.production().subscribe({
      next: records => {
        this.records.set(records);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set('Unable to load data.');
      }
    });
  }
}
