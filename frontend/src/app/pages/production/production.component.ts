import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { Farm, FishCycle, ProductionRecord } from '../../core/models';
import { apiError, formatNumber } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

@Component({
  selector: 'app-production',
  imports: [ReactiveFormsModule, PageHeaderComponent],
  templateUrl: './production.component.html',
  styleUrl: './production.component.css'
})
export class ProductionComponent {
  private api = inject(ApiService);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);
  farms = signal<Farm[]>([]);
  records = signal<ProductionRecord[]>([]);
  cycles = signal<FishCycle[]>([]);
  loading = signal(true);
  loadError = signal('');
  busy = signal(false);
  error = signal('');
  fmt = formatNumber;
  totals = computed(() => {
    const sum = (type: string) => this.records()
      .filter(row => row.productType === type)
      .reduce((total, row) => total + Number(row.quantity), 0);
    return { milk: sum('MILK'), eggs: sum('EGGS'), fish: sum('FISH'), count: this.records().length };
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
    this.reload();
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
        this.toast.show('Production recorded.');
        this.reload();
      },
      error: err => {
        this.busy.set(false);
        this.error.set(apiError(err, 'Could not save production'));
      }
    });
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
