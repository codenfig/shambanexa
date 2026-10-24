import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Animal, Dashboard, Farm, FishCycle, HealthCase, HerdBatch, Species, WorkTask } from '../../core/models';
import { apiError, formatNumber, signedPercent } from '../../core/format';

type ViewMode = 'list' | 'cards' | 'herds';
type DrawerTab = 'overview' | 'health';

const SEXES = ['FEMALE', 'MALE', 'UNKNOWN'];
const HEALTH = ['HEALTHY', 'WATCH', 'SICK', 'QUARANTINE'];

@Component({
  selector: 'app-livestock',
  imports: [RouterLink, FormsModule],
  templateUrl: './livestock.component.html',
  styleUrl: './livestock.component.css'
})
export class LivestockComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);

  animals = signal<Animal[]>([]);
  batches = signal<HerdBatch[]>([]);
  farms = signal<Farm[]>([]);
  species = signal<Species[]>([]);
  board = signal<Dashboard | null>(null);
  tasks = signal<WorkTask[]>([]);
  cases = signal<HealthCase[]>([]);
  cycles = signal<FishCycle[]>([]);
  loading = signal(true);
  canWrite = computed(() => this.auth.session()?.canWriteOperations === true);
  counts = signal<Record<string, number>>({});
  countError = signal('');
  error = signal('');
  query = signal(this.route.snapshot.queryParamMap.get('q') || '');
  farmId = signal(this.route.snapshot.queryParamMap.get('farmId') || '');
  speciesId = signal('');
  private speciesQuery = this.route.snapshot.queryParamMap.get('species') || '';
  sex = signal('');
  health = signal('');
  breed = signal('');
  speciesTab = signal('all');
  view = signal<ViewMode>('list');
  drawerTab = signal<DrawerTab>('overview');
  page = signal(0);
  readonly pageSize = 8;
  readonly sexes = SEXES;
  readonly healthOptions = HEALTH;

  selected = signal<Animal | null>(null);
  transferAnimal = signal<Animal | null>(null);
  destination = signal('');
  reason = signal('');
  transferError = signal('');
  transferring = signal(false);
  fmt = formatNumber;
  signed = signedPercent;

  filteredAnimals = computed(() => {
    const sex = this.sex();
    const health = this.health();
    const breed = this.breed();
    const tab = this.speciesTab();
    const rows = this.animals().filter(animal => {
      if (tab === 'archived') {
        if (animal.status === 'ACTIVE') {
          return false;
        }
      } else if (tab !== 'all' && tab !== 'herds' && tab !== 'fish' && animal.speciesName !== tab) {
        return false;
      } else if (animal.status !== 'ACTIVE' && tab !== 'archived') {
        return false;
      }
      if (sex && animal.sex !== sex) {
        return false;
      }
      if (health && animal.healthStatus !== health) {
        return false;
      }
      if (breed && animal.breedName !== breed) {
        return false;
      }
      return true;
    });
    return [...rows].sort((a, b) => (a.name || a.tag).localeCompare(b.name || b.tag));
  });

  filteredBatches = computed(() => {
    const q = this.query().trim().toLowerCase();
    const farmId = this.farmId();
    const tab = this.speciesTab();
    return this.batches().filter(batch => {
      if (farmId && batch.farmId !== farmId) {
        return false;
      }
      if (tab !== 'all' && tab !== 'herds' && tab !== 'fish' && tab !== 'archived' && batch.speciesName !== tab) {
        return false;
      }
      if (!q) {
        return true;
      }
      return [batch.name, batch.code, batch.farmName, batch.speciesName].join(' ').toLowerCase().includes(q);
    });
  });

  breeds = computed(() => {
    const names = new Set<string>();
    for (const animal of this.animals()) {
      if (this.speciesTab() !== 'all' && this.speciesTab() !== 'archived' && animal.speciesName !== this.speciesTab()) {
        continue;
      }
      if (animal.breedName) {
        names.add(animal.breedName);
      }
    }
    return [...names].sort();
  });

  summary = computed(() => {
    const tagged = this.animals().filter(animal => animal.status === 'ACTIVE');
    const heads = this.batches().filter(batch => batch.status === 'ACTIVE').reduce((sum, batch) => sum + Number(batch.quantity || 0), 0);
    const healthy = tagged.filter(animal => animal.healthStatus === 'HEALTHY').length;
    const attention = tagged.filter(animal => animal.healthStatus !== 'HEALTHY').length;
    const openCases = this.cases().filter(item => item.caseStatus === 'OPEN');
    const due = this.tasks().filter(task => task.status === 'OPEN' && (task.taskType === 'TREATMENT' || /vaccin/i.test(task.title)));
    const total = this.board()?.totalAnimals ?? tagged.length + heads;
    return { total, healthy, attention, heads, openCases: openCases.length, due: due.length, tagged: tagged.length, change: this.board()?.animalChangePercent ?? null };
  });

  speciesTabs = computed(() => this.board()?.species ?? []);

  archivedCount = computed(() => this.animals().filter(animal => animal.status !== 'ACTIVE').length);

  distribution = computed(() => {
    const items = this.board()?.species ?? [];
    const total = items.reduce((sum, item) => sum + Number(item.count), 0) || 1;
    return items.map(item => ({ ...item, share: (Number(item.count) / total) * 100 }));
  });

  donut = computed(() => {
    const items = this.distribution();
    if (items.length === 0) {
      return 'conic-gradient(#e7ece8 0 100%)';
    }
    let cursor = 0;
    const stops = items.map(item => {
      const start = cursor;
      cursor += item.share;
      return `${item.color} ${start}% ${cursor}%`;
    });
    return `conic-gradient(${stops.join(',')})`;
  });

  healthMix = computed(() => {
    const tagged = this.animals().filter(animal => animal.status === 'ACTIVE');
    const total = tagged.length || 1;
    return HEALTH.map(status => {
      const count = tagged.filter(animal => animal.healthStatus === status).length;
      return { status, count, share: (count / total) * 100 };
    }).filter(item => item.count > 0);
  });

  healthDonut = computed(() => {
    const colors: Record<string, string> = { HEALTHY: '#2f9e44', WATCH: '#d4a72c', SICK: '#e23b3b', QUARANTINE: '#c2780a' };
    const items = this.healthMix();
    if (items.length === 0) {
      return 'conic-gradient(#e7ece8 0 100%)';
    }
    let cursor = 0;
    return `conic-gradient(${items.map(item => {
      const start = cursor;
      cursor += item.share;
      return `${colors[item.status] || '#94a3b8'} ${start}% ${cursor}%`;
    }).join(',')})`;
  });

  upcoming = computed(() => this.tasks().filter(task => task.status === 'OPEN').slice(0, 5));

  pageCount = computed(() => Math.max(1, Math.ceil(this.filteredAnimals().length / this.pageSize)));
  visiblePage = computed(() => Math.min(this.page(), this.pageCount() - 1));
  pagedAnimals = computed(() => {
    const start = this.visiblePage() * this.pageSize;
    return this.filteredAnimals().slice(start, start + this.pageSize);
  });
  pages = computed(() => Array.from({ length: this.pageCount() }, (_, index) => index));
  rangeLabel = computed(() => {
    const total = this.filteredAnimals().length;
    if (total === 0) {
      return 'Showing 0 tagged animals';
    }
    const start = this.visiblePage() * this.pageSize + 1;
    const end = Math.min(total, start + this.pageSize - 1);
    return `Showing ${start}–${end} of ${formatNumber(total)} tagged`;
  });

  filtersActive = computed(() => !!(this.query() || this.farmId() || this.sex() || this.health() || this.breed() || this.speciesTab() !== 'all'));

  destinationFarms = computed(() => this.farms().filter(farm => farm.id !== this.transferAnimal()?.farmId));

  farmCases = computed(() => {
    const animal = this.selected();
    if (!animal) {
      return [];
    }
    return this.cases().filter(item => item.farmId === animal.farmId && item.caseStatus === 'OPEN');
  });

  constructor() {
    this.api.farms().subscribe({ next: farms => this.farms.set(farms), error: () => this.farms.set([]) });
    this.api.dashboard().subscribe({ next: board => this.board.set(board), error: () => this.board.set(null) });
    this.api.tasks().subscribe({ next: tasks => this.tasks.set(tasks), error: () => this.tasks.set([]) });
    this.api.healthCases().subscribe({ next: cases => this.cases.set(cases), error: () => this.cases.set([]) });
    this.api.fishCycles().subscribe({ next: cycles => this.cycles.set(cycles), error: () => this.cycles.set([]) });
    this.api.species().subscribe({
      next: species => {
        this.species.set(species);
        const match = species.find(item => item.name.toLowerCase() === this.speciesQuery.toLowerCase());
        if (match?.name.toLowerCase() === 'fish') {
          this.speciesTab.set('fish');
        } else if (match) {
          this.speciesTab.set(match.name);
        }
        this.load();
      },
      error: () => {
        this.species.set([]);
        this.load();
      }
    });
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.batches().subscribe({
      next: batches => this.batches.set(batches),
      error: err => {
        this.batches.set([]);
        this.fail(err);
      }
    });
    this.loadAnimals(0, []);
  }

  private loadAnimals(page: number, accumulated: Animal[]): void {
    this.api.animals({
      q: this.query().trim(),
      farmId: this.farmId(),
      speciesId: this.speciesId(),
      page,
      size: 100
    }).subscribe({
      next: result => {
        const items = accumulated.concat(result.items);
        if (result.items.length > 0 && items.length < result.total) {
          this.loadAnimals(page + 1, items);
          return;
        }
        this.animals.set(items);
        this.loading.set(false);
      },
      error: err => this.fail(err)
    });
  }

  private fail(err: unknown): void {
    this.animals.set([]);
    this.loading.set(false);
    this.error.set(apiError(err, 'Livestock could not be loaded'));
  }

  today(): string {
    return new Intl.DateTimeFormat('en-GB', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
      timeZone: 'Africa/Dar_es_Salaam'
    }).format(new Date());
  }

  setQuery(value: string): void {
    this.query.set(value);
    this.page.set(0);
    this.reloadTagged();
  }

  setFarm(value: string): void {
    this.farmId.set(value);
    this.page.set(0);
    this.reloadTagged();
  }

  setSex(value: string): void {
    this.sex.set(value);
    this.page.set(0);
  }

  setHealth(value: string): void {
    this.health.set(value);
    this.page.set(0);
  }

  setBreed(value: string): void {
    this.breed.set(value);
    this.page.set(0);
  }

  chooseTab(tab: string): void {
    this.speciesTab.set(tab);
    this.breed.set('');
    this.page.set(0);
    if (tab === 'herds') {
      this.view.set('herds');
    } else if (tab === 'fish') {
      this.view.set('list');
    } else if (this.view() === 'herds') {
      this.view.set('list');
    }
  }

  setView(view: ViewMode): void {
    this.view.set(view);
  }

  reset(): void {
    this.query.set('');
    this.farmId.set('');
    this.speciesId.set('');
    this.sex.set('');
    this.health.set('');
    this.breed.set('');
    this.speciesTab.set('all');
    this.view.set('list');
    this.page.set(0);
    this.reloadTagged();
  }

  private reloadTagged(): void {
    this.loading.set(true);
    this.error.set('');
    this.loadAnimals(0, []);
  }

  go(page: number): void {
    this.page.set(Math.min(Math.max(page, 0), this.pageCount() - 1));
  }

  open(animal: Animal): void {
    this.selected.set(animal);
    this.drawerTab.set('overview');
  }

  closeDetail(): void {
    this.selected.set(null);
  }

  @HostListener('document:keydown.escape')
  closeOnEscape(): void {
    this.closeTransfer();
    this.closeDetail();
  }

  startTransfer(animal: Animal): void {
    this.transferAnimal.set(animal);
    this.destination.set(this.farms().find(farm => farm.id !== animal.farmId)?.id || '');
    this.reason.set('');
    this.transferError.set('');
  }

  closeTransfer(): void {
    if (!this.transferring()) {
      this.transferAnimal.set(null);
    }
  }

  transfer(): void {
    const animal = this.transferAnimal();
    const destination = this.destination();
    const reason = this.reason().trim();
    if (!animal || !destination || !reason || this.transferring()) {
      this.transferError.set('Choose a destination farm and enter a reason.');
      return;
    }
    this.transferring.set(true);
    this.transferError.set('');
    this.api.transferAnimal(animal.id, {
      destinationFarmId: destination,
      reason,
      idempotencyKey: crypto.randomUUID()
    }).subscribe({
      next: updated => {
        this.transferring.set(false);
        this.transferAnimal.set(null);
        if (this.selected()?.id === updated.id) {
          this.selected.set(updated);
        }
        this.reloadTagged();
      },
      error: err => {
        this.transferring.set(false);
        this.transferError.set(apiError(err, 'Transfer was not posted'));
      }
    });
  }

  exportCsv(): void {
    const rows = this.view() === 'herds'
      ? [['Code', 'Herd', 'Species', 'Farm', 'Purpose', 'Head', 'Status'],
        ...this.filteredBatches().map(batch => [batch.code, batch.name, batch.speciesName, batch.farmName, batch.purpose, String(batch.quantity), batch.status])]
      : [['Tag', 'Name', 'Species', 'Breed', 'Sex', 'Farm', 'Birth date', 'Health', 'Status', 'Color'],
        ...this.filteredAnimals().map(animal => [
          animal.tag, animal.name || '', animal.speciesName, animal.breedName || '', animal.sex,
          animal.farmName, animal.birthDate || '', animal.healthStatus, animal.status, animal.color || ''
        ])];
    const csv = rows.map(columns => columns.map(value => `"${String(value).replaceAll('"', '""')}"`).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = this.view() === 'herds' ? 'shambanexa-herds.csv' : 'shambanexa-livestock.csv';
    link.click();
    URL.revokeObjectURL(url);
  }

  healthClass(status: string): string {
    if (status === 'HEALTHY') {
      return 'is-healthy';
    }
    if (status === 'WATCH') {
      return 'is-watch';
    }
    if (status === 'SICK' || status === 'QUARANTINE') {
      return 'is-sick';
    }
    return 'is-other';
  }

  label(value: string | null | undefined): string {
    if (!value) {
      return '—';
    }
    return value.charAt(0) + value.slice(1).toLowerCase();
  }

  initial(animal: Animal): string {
    return (animal.name || animal.tag || '?').charAt(0).toUpperCase();
  }

  dateLabel(value: string | null): string {
    if (!value) {
      return '—';
    }
    const day = value.slice(0, 10);
    return new Intl.DateTimeFormat('en-GB', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      timeZone: 'UTC'
    }).format(new Date(`${day}T00:00:00Z`));
  }

  ageLabel(value: string | null): string {
    if (!value) {
      return 'Age not recorded';
    }
    const [year, month, day] = value.slice(0, 10).split('-').map(Number);
    if (!year || !month || !day) {
      return 'Age not recorded';
    }
    const today = new Date();
    let years = today.getFullYear() - year;
    let months = today.getMonth() + 1 - month;
    if (today.getDate() < day) {
      months -= 1;
    }
    if (months < 0) {
      years -= 1;
      months += 12;
    }
    if (years < 1) {
      return months <= 0 ? 'Under 1 month' : `${months} month${months === 1 ? '' : 's'}`;
    }
    return months > 0 ? `${years} year${years === 1 ? '' : 's'} ${months} mo` : `${years} year${years === 1 ? '' : 's'}`;
  }

  dueLabel(iso: string): string {
    return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' }).format(new Date(`${iso}T00:00:00Z`));
  }

  countValue(batch: HerdBatch): number {
    return Object.hasOwn(this.counts(), batch.id) ? this.counts()[batch.id] : batch.quantity;
  }

  draftCount(id: string, value: string): void {
    const quantity = Number(value);
    if (Number.isInteger(quantity) && quantity >= 0 && quantity <= 1_000_000) {
      this.counts.update(current => ({ ...current, [id]: quantity }));
    }
  }

  saveCount(batch: HerdBatch): void {
    const quantity = this.counts()[batch.id] ?? batch.quantity;
    this.countError.set('');
    this.api.updateBatchQuantity(batch.id, quantity).subscribe({
      next: saved => this.batches.update(rows => rows.map(row => row.id === saved.id ? saved : row)),
      error: err => this.countError.set(apiError(err, 'The herd count was not saved'))
    });
  }
}
