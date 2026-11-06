import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { formatNumber } from '../../core/format';
import { categoryLabel, downloadCsv, money } from './inventory.format';
import { InventoryStore } from './inventory.store';

@Component({
  selector: 'app-stock-reports',
  imports: [RouterLink],
  templateUrl: './reports.component.html',
  styleUrl: './inventory.component.css'
})
export class StockReportsComponent {
  readonly store = inject(InventoryStore);
  readonly fmt = formatNumber;
  readonly label = categoryLabel;

  money(value: number | null | undefined): string {
    return money(value ?? null, this.store.report()?.currency || this.store.currencyCode());
  }

  exportCsv(): void {
    const report = this.store.report();
    if (!report) {
      return;
    }
    downloadCsv('shambanexa-inventory-report.csv',
      ['section', 'name', 'count', 'value', 'currency'],
      [
        ...report.byCategory.map(row => ['category', row.category, String(row.products), String(row.value), report.currency]),
        ...report.expiring.map(row => ['expiring', `${row.days} days`, String(row.batches), '', report.currency]),
        ['expired', 'Expired stock value', String(report.expiredBatches), String(report.expiredValue), report.currency]
      ]);
  }
}
