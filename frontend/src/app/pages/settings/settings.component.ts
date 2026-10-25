import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Settings, Species } from '../../core/models';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

interface WorkspaceDraft {
  name: string;
  code: string;
  currencyCode: string;
  timezone: string;
  seasonLabel: string;
  locale: string;
}

type SettingsSection = 'general' | 'master' | 'integrations' | 'security';

@Component({
  selector: 'app-settings',
  imports: [PageHeaderComponent, RouterLink],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.css'
})
export class SettingsComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  settings = signal<Settings | null>(null);
  species = signal<Species[]>([]);
  loading = signal(true);
  error = signal('');
  saving = signal(false);
  saveError = signal('');
  draft = signal<WorkspaceDraft | null>(null);
  private stored = signal<WorkspaceDraft | null>(null);
  section = signal<SettingsSection>('general');
  session = this.auth.session;
  dirty = computed(() => {
    const current = this.draft();
    const saved = this.stored();
    return !!current && !!saved && JSON.stringify(current) !== JSON.stringify(saved);
  });

  constructor() {
    this.route.fragment.subscribe(fragment => {
      if (fragment === 'master' || fragment === 'integrations' || fragment === 'security' || fragment === 'general') {
        this.section.set(fragment);
      }
    });
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
        this.apply(settings);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Unable to load data.');
      }
    });
  }

  canEdit(): boolean {
    return !!this.session()?.canManageAccess;
  }

  open(section: SettingsSection): void {
    this.section.set(section);
    this.router.navigate([], { fragment: section, replaceUrl: true });
  }

  edit(field: keyof WorkspaceDraft, value: string): void {
    const current = this.draft();
    if (!current || !this.canEdit()) {
      return;
    }
    this.saveError.set('');
    this.draft.set({ ...current, [field]: value });
  }

  revert(): void {
    const saved = this.stored();
    if (saved) {
      this.draft.set({ ...saved });
      this.saveError.set('');
    }
  }

  save(): void {
    const current = this.draft();
    if (!current || !this.dirty() || this.saving() || !this.canEdit()) {
      return;
    }
    this.saving.set(true);
    this.saveError.set('');
    this.api.updateSettings({
      name: current.name.trim(),
      code: current.code.trim(),
      currencyCode: current.currencyCode.trim(),
      timezone: current.timezone.trim(),
      seasonLabel: current.seasonLabel.trim(),
      locale: current.locale.trim()
    }).subscribe({
      next: settings => {
        this.apply(settings);
        this.saving.set(false);
      },
      error: (err) => {
        this.saving.set(false);
        this.saveError.set(err?.error?.message || 'The workspace settings were not saved.');
      }
    });
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

  private apply(settings: Settings): void {
    const draft: WorkspaceDraft = {
      name: settings.tenantName,
      code: settings.code,
      currencyCode: settings.currencyCode,
      timezone: settings.timezone,
      seasonLabel: settings.seasonLabel,
      locale: settings.locale
    };
    this.settings.set(settings);
    this.draft.set(draft);
    this.stored.set(draft);
  }
}
