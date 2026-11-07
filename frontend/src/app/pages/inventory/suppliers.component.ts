import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { formatNumber } from '../../core/format';
import { categoryLabel, downloadCsv, scopeChip } from './inventory.format';
import { InventoryStore } from './inventory.store';

@Component({
  selector: 'app-stock-suppliers',
  imports: [RouterLink],
  templateUrl: './suppliers.component.html',
  styleUrl: './inventory.component.css'
})
export class StockSuppliersComponent {
  private auth = inject(AuthService);
  readonly store = inject(InventoryStore);
  readonly fmt = formatNumber;
  readonly label = categoryLabel;
  session = this.auth.session;
  search = signal('');
  selected = signal('');
  groups = computed(() => {
    const query = this.search().trim().toLowerCase();
    const grouped = new Map<string, { farms: Set<string>; lines: number; categories: Set<string> }>();
    for (const item of this.store.items()) {
      if (!item.supplierName) {
        continue;
      }
      if (query && !`${item.supplierName} ${item.farmName} ${item.name} ${item.category}`.toLowerCase().includes(query)) {
        continue;
      }
      const row = grouped.get(item.supplierName) || { farms: new Set<string>(), lines: 0, categories: new Set<string>() };
      row.farms.add(item.farmName);
      row.categories.add(item.category);
      row.lines += 1;
      grouped.set(item.supplierName, row);
    }
    return [...grouped.entries()]
      .map(([name, row]) => ({
        name,
        lines: row.lines,
        farms: [...row.farms].sort((a, b) => a.localeCompare(b)),
        categories: [...row.categories].map(categoryLabel).sort((a, b) => a.localeCompare(b))
      }))
      .sort((a, b) => a.name.localeCompare(b.name));
  });
  chosen = computed(() => this.groups().find(row => row.name === this.selected()) ?? this.groups().at(0) ?? null);
  lines = computed(() => {
    const name = this.chosen()?.name;
    return name ? this.store.items().filter(item => item.supplierName === name) : [];
  });
  missing = computed(() => this.store.items().filter(item => !item.supplierName));

  directory() {
    const name = this.chosen()?.name;
    if (!name) {
      return null;
    }
    return this.store.suppliers().find(row => row.name.toLowerCase() === name.toLowerCase()) || null;
  }

  isChosen(name: string): boolean {
    return this.chosen()?.name === name;
  }

  canWrite(): boolean {
    return !!this.session()?.canWriteOperations;
  }

  chip(): string {
    return scopeChip(this.store.items().map(item => item.farmName));
  }

  exportCsv(): void {
    downloadCsv('shambanexa-suppliers.csv',
      ['supplier', 'stock_lines', 'farms', 'categories'],
      this.groups().map(row => [row.name, String(row.lines), row.farms.join('; '), row.categories.join('; ')]));
  }
}
