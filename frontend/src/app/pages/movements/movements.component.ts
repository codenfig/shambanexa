import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Farm, MovementRecord } from '../../core/models';
import { chartSlice } from '../../core/chart-colors';
import { formatNumber } from '../../core/format';

@Component({
  selector: 'app-movements',
  imports: [RouterLink],
  templateUrl: './movements.component.html',
  styleUrl: './movements.component.css'
})
export class MovementsComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  rows = signal<MovementRecord[]>([]);
  farms = signal<Farm[]>([]);
  loading = signal(true);
  loadError = signal('');
  query = signal('');
  farmId = signal('');
  species = signal('');
  kind = signal('');
  subject = signal('');
  page = signal(0);
  selectedId = signal('');
  readonly pageSize = 10;

  fmt = formatNumber;
  canWrite = computed(() => this.auth.session()?.canWriteOperations === true);

  speciesChoices = computed(() => [...new Set(this.rows().map(row => row.speciesName).filter(Boolean))].sort());
  filtered = computed(() => {
    const query = this.query().trim().toLowerCase();
    return this.rows().filter(row => {
      if (this.farmId() && row.farmId !== this.farmId()) return false;
      if (this.species() && row.speciesName !== this.species()) return false;
      if (this.subject() && row.subjectType !== this.subject()) return false;
      if (this.kind() === 'REGISTRATION' && row.eventType !== 'REGISTRATION') return false;
      if (this.kind() === 'TRANSFER' && !row.eventType.startsWith('TRANSFER')) return false;
      if (!query) return true;
      return [row.subjectCode, row.subjectName, row.speciesName, row.farmName, row.reason || '', row.actorName || '']
        .join(' ').toLowerCase().includes(query);
    });
  });
  pageCount = computed(() => Math.max(1, Math.ceil(this.filtered().length / this.pageSize)));
  visiblePage = computed(() => Math.min(this.page(), this.pageCount() - 1));
  paged = computed(() => {
    const start = this.visiblePage() * this.pageSize;
    return this.filtered().slice(start, start + this.pageSize);
  });
  pages = computed(() => Array.from({ length: this.pageCount() }, (_, index) => index));
  byFarm = computed(() => {
    const counts = new Map<string, number>();
    for (const row of this.rows()) {
      const name = row.farmName || 'Unassigned';
      counts.set(name, (counts.get(name) ?? 0) + 1);
    }
    return [...counts.entries()]
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
      .map(([name, count], index) => ({ name, count, color: chartSlice(index) }));
  });
  ribbonLabel = computed(() => this.byFarm().map(item => `${item.name} ${formatNumber(item.count)}`).join(', '));
  footerNote = computed(() => {
    if (this.loading()) return '';
    const select = this.filtered().length && !this.selected() ? 'Select a row to open the stored record.' : '';
    const transfer = this.transfers() === 0
      ? 'A transfer is stored as one line leaving a farm and one line arriving. None are stored yet.'
      : '';
    return [select, transfer].filter(Boolean).join(' ');
  });
  rangeLabel = computed(() => {
    const total = this.filtered().length;
    if (total === 0) return 'Showing 0 records';
    const start = this.visiblePage() * this.pageSize + 1;
    const end = Math.min(total, start + this.pageSize - 1);
    return `Showing ${start}–${end} of ${formatNumber(total)} records`;
  });
  animalRegistrations = computed(() => this.rows().filter(row => row.eventType === 'REGISTRATION' && row.subjectType === 'ANIMAL').length);
  herdRegistrations = computed(() => this.rows().filter(row => row.eventType === 'REGISTRATION' && row.subjectType === 'BATCH').length);
  transfers = computed(() => this.rows().filter(row => row.eventType.startsWith('TRANSFER')).length);
  selected = computed(() => this.rows().find(row => row.id === this.selectedId()) ?? null);
  summary = computed(() => {
    if (this.loading() && this.rows().length === 0) {
      return 'Registrations and transfers recorded for farms in your scope.';
    }
    const transfers = this.transfers();
    const registrations = this.animalRegistrations() + this.herdRegistrations();
    if (registrations === 0 && transfers === 0) return 'No movement is stored.';
    const parts = [
      registrations ? `${formatNumber(registrations)} registration${registrations === 1 ? '' : 's'}` : '',
      transfers ? `${formatNumber(transfers)} transfer line${transfers === 1 ? '' : 's'}` : 'no transfer is stored'
    ].filter(Boolean);
    return parts.join(', ') + '.';
  });

  constructor() {
    this.api.farms().subscribe({
      next: farms => this.farms.set(farms),
      error: () => this.farms.set([])
    });
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.loadError.set('');
    this.api.movements().subscribe({
      next: rows => {
        this.rows.set(rows);
        this.loading.set(false);
        if (!this.selectedId() || !rows.some(row => row.id === this.selectedId())) {
          this.selectedId.set(rows[0]?.id ?? '');
        }
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set('Movements could not be loaded.');
      }
    });
  }

  setQuery(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
    this.page.set(0);
    this.keepVisibleSelection();
  }

  setFarm(event: Event): void {
    this.farmId.set((event.target as HTMLSelectElement).value);
    this.page.set(0);
    this.keepVisibleSelection();
  }

  setSpecies(event: Event): void {
    this.species.set((event.target as HTMLSelectElement).value);
    this.page.set(0);
    this.keepVisibleSelection();
  }

  setKind(event: Event): void {
    this.kind.set((event.target as HTMLSelectElement).value);
    this.page.set(0);
    this.keepVisibleSelection();
  }

  setSubject(event: Event): void {
    this.subject.set((event.target as HTMLSelectElement).value);
    this.page.set(0);
    this.keepVisibleSelection();
  }

  clearFilters(): void {
    this.query.set('');
    this.farmId.set('');
    this.species.set('');
    this.kind.set('');
    this.subject.set('');
    this.page.set(0);
  }

  filtersActive(): boolean {
    return !!(this.query() || this.farmId() || this.species() || this.kind() || this.subject());
  }

  go(index: number): void {
    this.page.set(Math.min(Math.max(index, 0), this.pageCount() - 1));
  }

  pick(row: MovementRecord): void {
    this.selectedId.set(row.id);
  }

  eventLabel(type: string): string {
    if (type === 'REGISTRATION') return 'Registration';
    if (type === 'TRANSFER_OUT') return 'Transfer out';
    if (type === 'TRANSFER_IN') return 'Transfer in';
    return type ? type.charAt(0) + type.slice(1).toLowerCase().replaceAll('_', ' ') : '—';
  }

  subjectLabel(type: string): string {
    return type === 'BATCH' ? 'Herd batch' : type === 'ANIMAL' ? 'Animal' : 'Record';
  }

  dateLabel(value: string): string {
    return new Intl.DateTimeFormat('en-GB', {
      day: 'numeric', month: 'short', year: 'numeric', timeZone: 'Africa/Dar_es_Salaam'
    }).format(new Date(value));
  }

  dayMonth(value: string): string {
    return new Intl.DateTimeFormat('en-GB', {
      day: 'numeric', month: 'short', timeZone: 'Africa/Dar_es_Salaam'
    }).format(new Date(value));
  }

  yearOf(value: string): string {
    return new Intl.DateTimeFormat('en-GB', {
      year: 'numeric', timeZone: 'Africa/Dar_es_Salaam'
    }).format(new Date(value));
  }

  lineMeta(row: MovementRecord): string {
    return [row.subjectCode, this.subjectLabel(row.subjectType), row.reason || 'No reason stored'].filter(Boolean).join(' · ');
  }

  speciesMark(name: string): string {
    const key = (name || '').toLowerCase();
    if (key.includes('cattle')) return 'cattle';
    if (key.includes('goat')) return 'goats';
    if (key.includes('sheep')) return 'sheep';
    if (key.includes('poultry') || key.includes('chicken')) return 'poultry';
    if (key.includes('pig')) return 'pigs';
    if (key.includes('fish') || key.includes('tilapia')) return 'fish';
    return '';
  }

  private keepVisibleSelection(): void {
    const id = this.selectedId();
    if (id && !this.filtered().some(row => row.id === id)) this.selectedId.set('');
  }

  print(): void {
    window.print();
  }

  exportCsv(): void {
    const header = ['Date', 'Code', 'Name', 'Kind', 'Species', 'Quantity', 'Farm', 'Record', 'Reason', 'Recorded by'];
    const body = this.filtered().map(row => [
      this.dateLabel(row.effectiveAt), row.subjectCode, row.subjectName, this.subjectLabel(row.subjectType),
      row.speciesName, String(row.quantity), row.farmName, this.eventLabel(row.eventType),
      row.reason || '', row.actorName || ''
    ]);
    const csv = [header, ...body].map(columns => columns.map(value => `"${String(value).replaceAll('"', '""')}"`).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'shambanexa-transfer-history.csv';
    link.click();
    URL.revokeObjectURL(url);
  }
}
