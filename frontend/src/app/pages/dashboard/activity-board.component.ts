import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ActivityItem } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { formatNumber } from '../../core/format';

type RangeKey = '7' | '30' | 'all';

interface DayGroup {
  key: string;
  label: string;
  date: string;
  items: ActivityItem[];
}

interface ShareRow {
  key: string;
  label: string;
  count: number;
  share: number;
  tone: string;
}

@Component({
  selector: 'app-activity-board',
  imports: [RouterLink],
  templateUrl: './activity-board.component.html',
  styleUrl: './activity-board.component.css'
})
export class ActivityBoardComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  events = signal<ActivityItem[]>([]);
  loading = signal(true);
  error = signal('');
  query = signal('');
  farmId = signal('');
  module = signal('');
  range = signal<RangeKey>('30');
  pinned = signal<string | null>(null);
  live = signal(true);
  fmt = formatNumber;

  constructor() {
    this.load();
    const timer = window.setInterval(() => {
      if (this.live()) {
        this.load(true);
      }
    }, 15000);
    inject(DestroyRef).onDestroy(() => window.clearInterval(timer));
  }

  windowed = computed(() => {
    const days = this.range() === 'all' ? null : Number(this.range());
    const cutoff = days == null ? null : Date.now() - days * 24 * 60 * 60 * 1000;
    return this.events().filter(item => cutoff == null || new Date(item.occurredAt).getTime() >= cutoff);
  });

  filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const farmId = this.farmId();
    const module = this.module();
    return this.windowed().filter(item => {
      if (farmId === 'workspace' && item.farmId) {
        return false;
      }
      if (farmId && farmId !== 'workspace' && item.farmId !== farmId) {
        return false;
      }
      if (module && item.eventType !== module) {
        return false;
      }
      if (!q) {
        return true;
      }
      const haystack = `${item.title} ${item.detail} ${item.farmName ?? ''} ${item.farmCode ?? ''} ${this.moduleLabel(item.eventType)}`.toLowerCase();
      return haystack.includes(q);
    });
  });

  chosen = computed(() => {
    const rows = this.filtered();
    if (this.pinned() === 'closed') {
      return null;
    }
    return rows.find(item => item.id === this.pinned()) ?? rows[0] ?? null;
  });

  farmOptions = computed(() => {
    const seen = new Map<string, string>();
    let workspace = false;
    for (const item of this.windowed()) {
      if (!item.farmId) {
        workspace = true;
      } else if (!seen.has(item.farmId)) {
        seen.set(item.farmId, this.farmLabel(item));
      }
    }
    return { farms: [...seen.entries()].map(([id, label]) => ({ id, label })).sort((a, b) => a.label.localeCompare(b.label)), workspace };
  });

  moduleOptions = computed(() => {
    const types = new Set(this.windowed().map(item => item.eventType));
    return [...types].sort().map(type => ({ type, label: this.moduleLabel(type) }));
  });

  stats = computed(() => {
    const rows = this.windowed();
    const today = this.dayKey(new Date().toISOString());
    const farms = new Set(rows.map(item => item.farmId).filter((id): id is string => !!id));
    const modules = new Set(rows.map(item => item.eventType));
    return {
      total: rows.length,
      today: rows.filter(item => this.dayKey(item.occurredAt) === today).length,
      modules: modules.size,
      farms: farms.size
    };
  });

  groups = computed<DayGroup[]>(() => {
    const today = this.dayKey(new Date().toISOString());
    const yesterday = this.dayKey(new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString());
    const buckets = new Map<string, ActivityItem[]>();
    for (const item of this.filtered()) {
      const key = this.dayKey(item.occurredAt);
      const list = buckets.get(key) ?? [];
      list.push(item);
      buckets.set(key, list);
    }
    return [...buckets.entries()].map(([key, items]) => ({
      key,
      label: key === today ? 'Today' : key === yesterday ? 'Yesterday' : 'Earlier',
      date: this.longDate(items[0].occurredAt),
      items
    }));
  });

  breakdown = computed<ShareRow[]>(() => this.shares(
    this.filtered(),
    item => item.eventType,
    item => this.moduleLabel(item.eventType)
  ));

  activeFarms = computed<ShareRow[]>(() => this.shares(
    this.filtered().filter(item => !!item.farmId),
    item => item.farmId || '',
    item => this.farmLabel(item)
  ));

  scopeLabel(): string {
    const session = this.auth.session();
    if (!session || session.allFarms || session.accessScope === 'ALL_FARMS') {
      return 'All farms';
    }
    if (session.accessScope === 'OWN_FARM') {
      return 'Own farm only';
    }
    return 'Selected farms';
  }

  moduleLabel(type: string): string {
    const labels: Record<string, string> = {
      LIVESTOCK: 'Animals',
      PRODUCTION: 'Production',
      INVENTORY: 'Inventory',
      FARM: 'Farms',
      FINANCE: 'Finance',
      STAFF: 'Staff',
      ADVICE: 'Advice'
    };
    return labels[type] ?? type.charAt(0) + type.slice(1).toLowerCase();
  }

  farmLabel(item: ActivityItem): string {
    if (!item.farmName) {
      return 'Not tied to one farm';
    }
    return item.farmCode ? `${item.farmName} (${item.farmCode})` : item.farmName;
  }

  clock(iso: string): string {
    return new Intl.DateTimeFormat('en-US', {
      hour: 'numeric',
      minute: '2-digit',
      hour12: true,
      timeZone: 'Africa/Dar_es_Salaam'
    }).format(new Date(iso));
  }

  stamp(iso: string): string {
    return new Intl.DateTimeFormat('en-GB', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
      hour12: true,
      timeZone: 'Africa/Dar_es_Salaam'
    }).format(new Date(iso));
  }

  kind(item: ActivityItem): string {
    const text = `${item.title} ${item.detail}`.toLowerCase();
    if (item.eventType === 'LIVESTOCK' && /transfer|moved/.test(text)) {
      return 'move';
    }
    if (item.eventType === 'LIVESTOCK') {
      return 'add';
    }
    if (item.eventType === 'PRODUCTION') {
      return 'milk';
    }
    if (item.eventType === 'INVENTORY') {
      return 'stock';
    }
    if (item.eventType === 'FARM') {
      return 'farm';
    }
    if (item.eventType === 'FINANCE') {
      return 'money';
    }
    if (item.eventType === 'STAFF' || /task|completed/.test(text)) {
      return 'task';
    }
    return 'people';
  }

  destination(item: ActivityItem): { commands: string[]; label: string } {
    if (item.eventType === 'LIVESTOCK') {
      return { commands: ['/livestock'], label: 'Open livestock' };
    }
    if (item.eventType === 'PRODUCTION') {
      return { commands: ['/production'], label: 'Open production' };
    }
    if (item.eventType === 'INVENTORY') {
      return { commands: ['/inventory'], label: 'Open inventory' };
    }
    if (item.eventType === 'STAFF') {
      return { commands: ['/staff'], label: 'Open staff' };
    }
    if (item.eventType === 'ADVICE') {
      return { commands: ['/advice'], label: 'Open advice' };
    }
    if (item.eventType === 'FINANCE') {
      return { commands: ['/analytics'], label: 'Open reports' };
    }
    if (item.farmId) {
      return { commands: ['/farms', item.farmId], label: 'Open farm' };
    }
    return { commands: ['/farms'], label: 'Open farms' };
  }

  clearFilters(): void {
    this.query.set('');
    this.farmId.set('');
    this.module.set('');
  }

  filtersActive(): boolean {
    return !!this.query().trim() || !!this.farmId() || !!this.module();
  }

  toggleLive(): void {
    this.live.update(on => !on);
    if (this.live()) {
      this.load(true);
    }
  }

  exportActivity(): void {
    const rows = this.filtered();
    if (!rows.length) {
      return;
    }
    const header = ['Occurred at', 'Title', 'Detail', 'Farm', 'Module'];
    const body = rows.map(item => [
      this.stamp(item.occurredAt),
      item.title,
      item.detail,
      this.farmLabel(item),
      this.moduleLabel(item.eventType)
    ]);
    const csv = [header, ...body].map(cols => cols.map(value => `"${value.replaceAll('"', '""')}"`).join(',')).join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'shambanexa-activity.csv';
    link.click();
    URL.revokeObjectURL(url);
    this.toast.show('Activity export downloaded.');
  }

  load(silent = false): void {
    if (!silent) {
      this.loading.set(true);
      this.error.set('');
    }
    this.api.activity().subscribe({
      next: rows => {
        this.events.set(rows);
        this.loading.set(false);
      },
      error: () => {
        if (!silent) {
          this.loading.set(false);
          this.error.set('The activity feed could not be loaded.');
        }
      }
    });
  }

  private shares(rows: ActivityItem[], keyOf: (item: ActivityItem) => string, labelOf: (item: ActivityItem) => string): ShareRow[] {
    const buckets = new Map<string, { label: string; count: number }>();
    for (const item of rows) {
      const key = keyOf(item);
      const current = buckets.get(key);
      if (current) {
        current.count += 1;
      } else {
        buckets.set(key, { label: labelOf(item), count: 1 });
      }
    }
    const total = rows.length;
    return [...buckets.entries()]
      .map(([key, row]) => ({
        key,
        label: row.label,
        count: row.count,
        share: total ? Math.round((row.count / total) * 100) : 0,
        tone: this.kind({ eventType: key, title: '', detail: '' } as ActivityItem)
      }))
      .sort((a, b) => b.count - a.count || a.label.localeCompare(b.label));
  }

  private dayKey(iso: string): string {
    return new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Africa/Dar_es_Salaam',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).format(new Date(iso));
  }

  private longDate(iso: string): string {
    return new Intl.DateTimeFormat('en-GB', {
      weekday: 'long',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      timeZone: 'Africa/Dar_es_Salaam'
    }).format(new Date(iso));
  }
}
