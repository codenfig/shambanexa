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
}
