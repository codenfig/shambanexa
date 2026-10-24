import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Settings, Species } from '../../core/models';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

@Component({
  selector: 'app-settings',
  imports: [PageHeaderComponent, RouterLink],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.css'
})
export class SettingsComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  settings = signal<Settings | null>(null);
  species = signal<Species[]>([]);
  loading = signal(true);
  error = signal('');
  session = this.auth.session;

  constructor() {
    this.load();
    this.api.species().subscribe({
      next: species => this.species.set(species),
      error: () => this.species.set([])
    });
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.settings().subscribe({
      next: settings => {
        this.settings.set(settings);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Unable to load data.');
      }
    });
  }

  initials(name: string): string {
    const parts = name.split(/\s+/).filter(Boolean);
    return ((parts[0]?.[0] || '') + (parts[1]?.[0] || '')).toUpperCase();
  }

  scopeLabel(scope: string): string {
    if (scope === 'ALL_FARMS') {
      return 'All farms';
    }
    if (scope === 'SELECTED_FARMS') {
      return 'Selected farms';
    }
    return 'Assigned farm';
  }

  scopeDetail(scope: string, count: number): string {
    if (scope === 'ALL_FARMS') {
      return 'Every farm in the workspace';
    }
    return `${count} assigned farm${count === 1 ? '' : 's'}`;
  }

  planDate(value: string): string {
    if (!value) {
      return '';
    }
    const day = value.slice(0, 10);
    return new Intl.DateTimeFormat('en-GB', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      timeZone: 'UTC'
    }).format(new Date(`${day}T00:00:00Z`));
  }
}
