import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { Farm, InventoryItem } from '../../core/models';
import { apiError, formatNumber } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

@Component({
  selector: 'app-inventory',
  imports: [ReactiveFormsModule, PageHeaderComponent],
  templateUrl: './inventory.component.html',
  styleUrl: './inventory.component.css'
})
export class InventoryComponent {
  private api = inject(ApiService);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);
  items = signal<InventoryItem[]>([]);
  lowOnly = signal(false);
  farms = signal<Farm[]>([]);
  selected = signal<InventoryItem | null>(null);
  loading = signal(true);
  loadError = signal('');
  busy = signal(false);
  adjusting = signal(false);
  error = signal('');
  adjustError = signal('');
  fmt = formatNumber;
  form = this.fb.nonNullable.group({
    sku: ['', Validators.required],
    name: ['', Validators.required],
    farmId: ['', Validators.required],
    category: ['FEED', Validators.required],
    quantityOnHand: [0, Validators.required],
    reorderPoint: [0, Validators.required],
    unit: ['kg', Validators.required]
  });
  adjustForm = this.fb.nonNullable.group({
    delta: [0, Validators.required],
    reason: ['Physical count', Validators.required]
  });

  category = signal('ALL');
  categories = computed(() => [...new Set(this.items().map(item => item.category))].sort((a, b) => a.localeCompare(b)));
  visibleItems = computed(() => {
    const category = this.category();
    return this.items().filter(item => {
      if (this.lowOnly() && !item.lowStock) return false;
      return category === 'ALL' || item.category === category;
    });
  });

  constructor() {
    this.route.queryParamMap.subscribe(params => this.lowOnly.set(params.get('view') === 'low'));
    this.api.farms().subscribe(farms => {
      this.farms.set(farms);
      if (farms[0]) {
        this.form.controls.farmId.setValue(farms[0].id);
      }
    });
    this.reload();
  }

  create(): void {
    if (this.busy() || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.api.createInventory(this.form.getRawValue()).subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.show('Inventory item added.');
        this.form.controls.sku.setValue('');
        this.form.controls.name.setValue('');
        this.reload();
      },
      error: err => {
        this.busy.set(false);
        this.error.set(apiError(err, 'Could not add the item'));
      }
    });
  }

  adjust(item: InventoryItem): void {
    if (this.adjusting() || this.adjustForm.invalid) {
      return;
    }
    this.adjusting.set(true);
    this.adjustError.set('');
    this.api.adjustInventory(item.id, this.adjustForm.getRawValue()).subscribe({
      next: () => {
        this.adjusting.set(false);
        this.selected.set(null);
        this.toast.show('Adjustment posted.');
        this.reload();
      },
      error: err => {
        this.adjusting.set(false);
        this.adjustError.set(apiError(err, 'Adjustment rejected'));
      }
    });
  }

  @HostListener('document:keydown.escape')
  closeAdjust(): void {
    if (!this.adjusting()) {
      this.selected.set(null);
    }
  }

  reload(): void {
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
  }
}
