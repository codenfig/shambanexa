import { Component, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { Dashboard, HealthCase } from '../../core/models';
import { formatMoney, formatMonth, formatNumber, signedPercent } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

@Component({
  selector: 'app-analytics',
  imports: [PageHeaderComponent],
  templateUrl: './analytics.component.html',
  styleUrl: './analytics.component.css'
})
export class AnalyticsComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  data = signal<Dashboard | null>(null);
  exporting = signal(false);
  cases = signal<HealthCase[]>([]);
  loading = signal(true);
  error = signal('');
  fmt = formatNumber;
  money = formatMoney;
  month = formatMonth;
  signed = signedPercent;

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.dashboard().subscribe({
      next: data => {
        this.data.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Unable to load data.');
      }
    });
    this.api.healthCases().subscribe({
      next: cases => this.cases.set(cases),
      error: () => this.cases.set([])
    });
  }

  exportReport(): void {
    if (this.exporting()) {
      return;
    }
    this.exporting.set(true);
    this.api.exportDashboard().subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'shambanexa-dashboard.csv';
        link.click();
        URL.revokeObjectURL(url);
        this.exporting.set(false);
        this.toast.show('Operational report downloaded.');
      },
      error: () => {
        this.exporting.set(false);
        this.toast.show('Export failed.', 'err');
      }
    });
  }

  severityClass(severity: string): string {
    if (severity === 'CRITICAL') {
      return 'is-critical';
    }
    if (severity === 'WARNING') {
      return 'is-other';
    }
    return 'is-info';
  }
}
