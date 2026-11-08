import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ActivatedRoute, NavigationEnd, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Animal, Dashboard, Farm, FishCycle, HealthCase, HerdBatch, Species, WorkTask } from '../../core/models';
import { chartSlice, healthChartColor } from '../../core/chart-colors';
import { speciesPicture } from '../../core/catalog-images';
import { apiError, formatNumber, signedPercent } from '../../core/format';

type ViewMode = 'list' | 'cards' | 'herds';
type DrawerTab = 'overview' | 'health' | 'weights' | 'breeding' | 'treatments';
type AnimalScreen = 'overview' | 'registry' | 'species' | 'herds' | 'health' | 'detail';

const SEXES = ['FEMALE', 'MALE', 'UNKNOWN'];
const HEALTH = ['HEALTHY', 'WATCH', 'SICK', 'QUARANTINE'];
const HEALTH_COLORS: Record<string, string> = {
  HEALTHY: healthChartColor('HEALTHY'),
  WATCH: healthChartColor('WATCH'),
  SICK: healthChartColor('SICK'),
  QUARANTINE: healthChartColor('QUARANTINE')
};

@Component({
  selector: 'app-livestock',
  imports: [RouterLink, FormsModule, NgTemplateOutlet],
  templateUrl: './livestock.component.html',
  styleUrl: './livestock.component.css'
})
export class LivestockComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  readonly screen = signal<AnimalScreen>('overview');
  private readonly healthToday = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Dar_es_Salaam',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(new Date());
  private detailId = '';

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
  batchPage = signal(0);
  readonly pageSize = 8;
  readonly sexes = SEXES;
  readonly healthOptions = HEALTH;

  selected = signal<Animal | null>(null);

  weightRows = signal<Array<{id:string; measured_on:string; weight_kg:number; notes:string|null}>>([]);
  breedingRows = signal<Array<{id:string; event_type:string; event_on:string; expected_birth_on:string|null; notes:string|null}>>([]);
  treatmentRows = signal<Array<{id:string; started_on:string; diagnosis:string; treatment_status:string; veterinarian_name:string; withdrawal_ends_on:string|null; issued:boolean}>>([]);
  withdrawal = signal<{activeWithdrawal:boolean; asOf:string; records:unknown[]}|null>(null);
  lifecycleLoading = signal(false);
  lifecycleSaving = signal(false);
  lifecycleError = signal('');
  lifecycleSuccess = signal('');
  weightValue = signal<number|null>(null);
  weightDate = signal(new Date().toISOString().slice(0,10));
  weightNotes = signal('');
  breedingType = signal('MATING');
  breedingDate = signal(new Date().toISOString().slice(0,10));
  expectedBirthDate = signal('');
  breedingNotes = signal('');

  loadLifecycle(animalId:string):void {
    this.lifecycleLoading.set(true);
    this.lifecycleError.set('');
    this.weightRows.set([]); this.breedingRows.set([]); this.treatmentRows.set([]); this.withdrawal.set(null);
    let pending=4;
    const done=()=>{ if(--pending===0) this.lifecycleLoading.set(false); };
    this.api.animalWeights(animalId).subscribe({next:rows=>{if(this.selected()?.id===animalId)this.weightRows.set(rows);done();},error:()=>{this.lifecycleError.set('Some history could not be loaded.');done();}});
    this.api.animalBreeding(animalId).subscribe({next:rows=>{if(this.selected()?.id===animalId)this.breedingRows.set(rows);done();},error:()=>{this.lifecycleError.set('Some history could not be loaded.');done();}});
    this.api.animalTreatmentHistory(animalId).subscribe({next:rows=>{if(this.selected()?.id===animalId)this.treatmentRows.set(rows);done();},error:()=>{this.lifecycleError.set('Some history could not be loaded.');done();}});
    this.api.animalWithdrawalStatus(animalId).subscribe({next:row=>{if(this.selected()?.id===animalId)this.withdrawal.set(row);done();},error:()=>{this.lifecycleError.set('Some history could not be loaded.');done();}});
  }

  saveWeight():void {
    const animal=this.selected(), weight=this.weightValue();
    if(!animal||!this.canWrite()||weight===null||!Number.isFinite(weight)||weight<=0||weight>=100000||!this.weightDate())return;
    this.lifecycleSaving.set(true);this.lifecycleError.set('');this.lifecycleSuccess.set('');
    this.api.addAnimalWeight(animal.id,{weightKg:weight,measuredOn:this.weightDate(),notes:this.weightNotes()}).subscribe({
      next:()=>{this.lifecycleSaving.set(false);this.weightValue.set(null);this.weightNotes.set('');this.lifecycleSuccess.set('Weight measurement saved.');this.loadLifecycle(animal.id);},
      error:err=>{this.lifecycleSaving.set(false);this.lifecycleError.set(apiError(err));}
    });
  }

  saveBreeding():void {
    const animal=this.selected();
    if(!animal||!this.canWrite()||!this.breedingDate())return;
    this.lifecycleSaving.set(true);this.lifecycleError.set('');this.lifecycleSuccess.set('');
    this.api.addAnimalBreeding(animal.id,{eventType:this.breedingType(),eventOn:this.breedingDate(),expectedBirthOn:this.expectedBirthDate()||null,notes:this.breedingNotes()}).subscribe({
      next:()=>{this.lifecycleSaving.set(false);this.breedingNotes.set('');this.expectedBirthDate.set('');this.lifecycleSuccess.set('Breeding event saved.');this.loadLifecycle(animal.id);},
      error:err=>{this.lifecycleSaving.set(false);this.lifecycleError.set(apiError(err));}
    });
  }

  actionMenu = signal<string | null>(null);
  menuTop = 0;
  menuRight = 0;
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
  openCaseRows = computed(() => this.cases().filter(item => item.caseStatus === 'OPEN'));
  dueTasks = computed(() => this.tasks().filter(task => task.status === 'OPEN' && (task.taskType === 'TREATMENT' || /vaccin/i.test(task.title))));
  overdueTreatments = computed(() => this.dueTasks().filter(task => this.treatmentLate(task)).length);
  attentionAnimals = computed(() => this.animals()
    .filter(animal => animal.status === 'ACTIVE' && animal.healthStatus !== 'HEALTHY')
    .sort((a, b) => (a.name || a.tag).localeCompare(b.name || b.tag)));
  breedMix = computed(() => {
    const tab = this.speciesTab();
    const rows = this.animals().filter(animal => animal.status === 'ACTIVE' && animal.speciesName === tab);
    const total = rows.length || 1;
    const counts = new Map<string, number>();
    for (const animal of rows) {
      const name = animal.breedName || 'Breed not recorded';
      counts.set(name, (counts.get(name) || 0) + 1);
    }
    return [...counts.entries()]
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
      .map(([name, count], index) => ({
        name,
        count,
        share: Math.round((count / total) * 100),
        color: chartSlice(index)
      }));
  });
  breedDonut = computed(() => {
    const items = this.breedMix();
    const total = items.reduce((sum, item) => sum + item.count, 0);
    if (!total) {
      return 'conic-gradient(#e7ece8 0 100%)';
    }
    let cursor = 0;
    return `conic-gradient(${items.map(item => {
      const start = cursor;
      cursor += (item.count / total) * 100;
      return `${item.color} ${start}% ${cursor}%`;
    }).join(',')})`;
  });
  speciesFocus = computed(() => {
    const name = this.speciesTab();
    const row = this.distribution().find(item => item.name.toLowerCase() === name.toLowerCase());
    const tagged = this.breedMix().reduce((sum, item) => sum + item.count, 0);
    const herds = this.speciesBatches();
    const heads = herds.reduce((sum, batch) => sum + Number(batch.quantity || 0), 0);
    const count = row ? Number(row.count) : tagged + heads;
    const livestock = this.summary().total || 1;
    return {
      count,
      share: row ? Math.round(row.share) : Math.round((count / livestock) * 100),
      tagged,
      heads,
      herds: herds.length,
      breeds: this.breedMix().length,
      color: row?.color || '#1e8330'
    };
  });
  herdBars = computed(() => {
    const herds = this.speciesBatches();
    const max = Math.max(...herds.map(batch => Number(batch.quantity || 0)), 1);
    return herds.map(batch => ({
      ...batch,
      share: Math.round((Number(batch.quantity || 0) / max) * 100)
    }));
  });
  speciesBatches = computed(() => {
    const tab = this.speciesTab();
    return this.batches().filter(batch => batch.status === 'ACTIVE' && batch.speciesName === tab);
  });
  herdStats = computed(() => {
    const rows = this.filteredBatches().filter(batch => batch.status === 'ACTIVE');
    return {
      batches: rows.length,
      heads: rows.reduce((sum, batch) => sum + Number(batch.quantity || 0), 0),
      species: new Set(rows.map(batch => batch.speciesName)).size,
      farms: new Set(rows.map(batch => batch.farmId)).size
    };
  });
  herdSpecies = computed(() => {
    const rows = this.filteredBatches().filter(batch => batch.status === 'ACTIVE');
    const total = rows.reduce((sum, batch) => sum + Number(batch.quantity || 0), 0) || 1;
    const counts = new Map<string, number>();
    for (const batch of rows) {
      counts.set(batch.speciesName, (counts.get(batch.speciesName) || 0) + Number(batch.quantity || 0));
    }
    const colors = new Map(this.distribution().map(item => [item.name, item.color]));
    return [...counts.entries()]
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
      .map(([name, count], index) => ({
        name,
        count,
        share: Math.round((count / total) * 100),
        color: colors.get(name) || chartSlice(index)
      }));
  });
  herdDonut = computed(() => {
    const items = this.herdSpecies();
    const total = items.reduce((sum, item) => sum + item.count, 0);
    if (!total) {
      return 'conic-gradient(#e7ece8 0 100%)';
    }
    let cursor = 0;
    return `conic-gradient(${items.map(item => {
      const start = cursor;
      cursor += (item.count / total) * 100;
      return `${item.color} ${start}% ${cursor}%`;
    }).join(',')})`;
  });

  archivedCount = computed(() => this.animals().filter(animal => animal.status !== 'ACTIVE').length);

  distribution = computed(() => {
    const items = this.board()?.species ?? [];
    const total = items.reduce((sum, item) => sum + Number(item.count), 0) || 1;
    return items.map((item, index) => ({ ...item, share: (Number(item.count) / total) * 100, color: chartSlice(index) }));
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
    const colors = HEALTH_COLORS;
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

  preview = computed(() => this.filteredAnimals().slice(0, 5));
  pageCount = computed(() => Math.max(1, Math.ceil(this.filteredAnimals().length / this.pageSize)));
  visiblePage = computed(() => Math.min(this.page(), this.pageCount() - 1));
  pagedAnimals = computed(() => {
    const start = this.visiblePage() * this.pageSize;
    return this.filteredAnimals().slice(start, start + this.pageSize);
  });
  pages = computed(() => this.pageWindow(this.pageCount(), this.visiblePage()));
  rangeLabel = computed(() => this.rangeText(this.filteredAnimals().length, this.visiblePage(), 'tagged'));
  batchPageCount = computed(() => Math.max(1, Math.ceil(this.filteredBatches().length / this.pageSize)));
  batchVisiblePage = computed(() => Math.min(this.batchPage(), this.batchPageCount() - 1));
  pagedBatches = computed(() => {
    const start = this.batchVisiblePage() * this.pageSize;
    return this.filteredBatches().slice(start, start + this.pageSize);
  });
  batchPages = computed(() => this.pageWindow(this.batchPageCount(), this.batchVisiblePage()));
  batchRangeLabel = computed(() => this.rangeText(this.filteredBatches().length, this.batchVisiblePage(), 'herds'));

  filtersActive = computed(() => !!(this.query() || this.farmId() || this.sex() || this.health() || this.breed() || (this.screen() === 'registry' && this.speciesTab() !== 'all')));

  destinationFarms = computed(() => this.farms().filter(farm => farm.id !== this.transferAnimal()?.farmId));

  farmCases = computed(() => {
    const animal = this.selected();
    if (!animal) {
      return [];
    }
    return this.cases().filter(item => item.farmId === animal.farmId && item.caseStatus === 'OPEN');
  });

  constructor() {
    this.applyRoute();
    this.router.events.subscribe(event => {
      if (!(event instanceof NavigationEnd)) {
        return;
      }
      this.applyRoute();
      this.selected.set(this.detailId ? this.animals().find(animal => animal.id === this.detailId) || null : null);
      if(this.detailId && this.selected()) this.loadLifecycle(this.detailId);
    });
    this.api.farms().subscribe({ next: farms => this.farms.set(farms), error: () => this.farms.set([]) });
    this.api.dashboard().subscribe({ next: board => this.board.set(board), error: () => this.board.set(null) });
    this.api.tasks().subscribe({ next: tasks => this.tasks.set(tasks), error: () => this.tasks.set([]) });
    this.api.healthCases().subscribe({ next: cases => this.cases.set(cases), error: () => this.cases.set([]) });
    this.api.fishCycles().subscribe({ next: cycles => this.cycles.set(cycles), error: () => this.cycles.set([]) });
    this.api.species().subscribe({
      next: species => {
        this.species.set(species);
        if (this.screen() === 'herds') {
          this.speciesTab.set('herds');
        }
        const match = species.find(item => item.name.toLowerCase() === this.speciesQuery.toLowerCase());
        if (this.screen() !== 'herds' && match?.name.toLowerCase() === 'fish') {
          this.speciesTab.set('fish');
        } else if (this.screen() !== 'herds' && match) {
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
        if (this.detailId) {
          this.selected.set(items.find(animal => animal.id === this.detailId) || null);
        }
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

  private applyRoute(): void {
    const url = this.router.url;
    const path = url.split(/[?#]/)[0];
    const parts = path.split('/').filter(Boolean);
    const query = new URLSearchParams((url.split('?')[1] || '').split('#')[0]);
    const speciesParam = query.get('species');
    const fragment = url.includes('#') ? decodeURIComponent(url.split('#')[1] || '') : '';
    const farmId = query.get('farmId') || '';
    this.detailId = '';
    if (parts[1] === 'registry') {
      this.screen.set('registry');
      this.view.set('list');
      this.speciesTab.set(query.get('tab') === 'archived' ? 'archived' : 'all');
      return;
    }
    if (parts[1] === 'herds') {
      this.screen.set('herds');
      this.speciesTab.set('herds');
      this.view.set('herds');
      return;
    }
    if (parts[1] === 'health') {
      this.screen.set('health');
      return;
    }
    if (parts[1] === 'species' && parts[2]) {
      this.screen.set('species');
      this.view.set('list');
      this.speciesQuery = decodeURIComponent(parts[2]);
      this.speciesTab.set(this.speciesQuery.toLowerCase() === 'fish' ? 'fish' : this.speciesQuery);
      return;
    }
    if (parts[1] && parts[1] !== 'new') {
      this.screen.set('detail');
      this.detailId = parts[1];
      return;
    }
    this.screen.set('overview');
    if (speciesParam) {
      this.router.navigate(['/livestock', 'species', speciesParam], {
        queryParams: farmId ? { farmId } : {},
        replaceUrl: true
      });
      return;
    }
    if (fragment === 'ledger' || fragment === 'transfer') {
      this.router.navigate(['/livestock', 'registry'], { replaceUrl: true });
    }
  }

  namedSpecies(): boolean {
    const tab = this.speciesTab();
    return tab !== 'all' && tab !== 'herds' && tab !== 'fish' && tab !== 'archived';
  }

  pageTitle(): string {
    const screen = this.screen();
    if (screen === 'registry') {
      return this.speciesTab() === 'archived' ? 'Archived animals' : 'Animal registry';
    }
    if (screen === 'herds') {
      return 'Herd batches';
    }
    if (screen === 'health') {
      return 'Health records';
    }
    if (screen === 'detail') {
      return this.selected()?.name || this.selected()?.tag || 'Animal';
    }
    if (screen === 'species') {
      if (this.speciesTab() === 'fish') {
        return 'Fish batches';
      }
      return this.speciesTab() === 'all' ? this.speciesQuery : this.speciesTab();
    }
    return 'Animal overview';
  }

  pageBlurb(): string {
    const screen = this.screen();
    if (screen === 'registry') {
      return 'Tagged animals in your scope. Herd headcount stays on the herd page.';
    }
    if (screen === 'herds') {
      if (this.loading() && this.batches().length === 0) {
        return 'Headcount stored on each herd batch.';
      }
      const stats = this.herdStats();
      return `${formatNumber(stats.heads)} head across ${formatNumber(stats.batches)} herds.`;
    }
    if (screen === 'health') {
      if (this.loading() && this.animals().length === 0 && this.cases().length === 0) {
        return 'Open health cases and due treatment tasks.';
      }
      const cases = this.summary().openCases;
      const due = this.summary().due;
      return `${formatNumber(cases)} open ${cases === 1 ? 'case' : 'cases'} and ${formatNumber(due)} due ${due === 1 ? 'treatment' : 'treatments'}.`;
    }
    if (screen === 'detail') {
      return 'Identity and health recorded for this animal.';
    }
    if (screen === 'species') {
      if (this.speciesTab() === 'fish') {
        return 'Fish cycles stored for farms in your scope.';
      }
      if (!this.board() && this.batches().length === 0) {
        return `Tagged ${this.speciesTab().toLowerCase()} and the herd batches stored for this species.`;
      }
      const focus = this.speciesFocus();
      return `${formatNumber(focus.count)} head across ${formatNumber(focus.herds)} herds, with ${formatNumber(focus.tagged)} tagged.`;
    }
    return 'Tagged animals and herd headcount for farms in your scope.';
  }

  chip(): string {
    const id = this.farmId();
    if (!id) {
      return 'All farms';
    }
    return this.farms().find(farm => farm.id === id)?.name || 'Selected farm';
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
    this.batchPage.set(0);
    this.reloadTagged();
  }

  setFarm(value: string): void {
    this.farmId.set(value);
    this.page.set(0);
    this.batchPage.set(0);
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
    this.breed.set('');
    this.page.set(0);
    if (tab === 'herds') {
      this.router.navigate(['/livestock', 'herds']);
      return;
    }
    if (tab === 'fish') {
      this.router.navigate(['/livestock', 'species', 'Fish']);
      return;
    }
    if (tab === 'archived') {
      this.router.navigate(['/livestock', 'registry'], { queryParams: { tab: 'archived' } });
      return;
    }
    if (tab === 'all') {
      this.router.navigate(['/livestock', 'registry']);
      return;
    }
    this.router.navigate(['/livestock', 'species', tab]);
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
    this.page.set(0);
    this.batchPage.set(0);
    if (this.screen() === 'registry') {
      this.speciesTab.set('all');
      this.view.set('list');
      this.router.navigate(['/livestock', 'registry']);
    }
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

  goBatches(page: number): void {
    this.batchPage.set(Math.min(Math.max(page, 0), this.batchPageCount() - 1));
  }

  private pageWindow(count: number, current: number): Array<number | 'gap'> {
    if (count <= 7) {
      return Array.from({ length: count }, (_, index) => index);
    }
    const wanted = [0, current - 1, current, current + 1, count - 1]
      .filter((value, index, list) => value >= 0 && value < count && list.indexOf(value) === index)
      .sort((a, b) => a - b);
    const window: Array<number | 'gap'> = [];
    wanted.forEach((value, index) => {
      if (index > 0 && value - wanted[index - 1] > 1) {
        window.push('gap');
      }
      window.push(value);
    });
    return window;
  }

  private rangeText(total: number, page: number, noun: string): string {
    if (total === 0) {
      return `Showing 0 ${noun}`;
    }
    const start = page * this.pageSize + 1;
    const end = Math.min(total, start + this.pageSize - 1);
    return `Showing ${start}–${end} of ${formatNumber(total)} ${noun}`;
  }

  open(animal: Animal): void {
    this.router.navigate(['/livestock', animal.id]);
  }

  closeDetail(): void {
    this.selected.set(null);
  }

  @HostListener('document:click')
  closeActionMenu(): void {
    this.actionMenu.set(null);
  }

  @HostListener('document:keydown.escape')
  closeOnEscape(): void {
    this.actionMenu.set(null);
    this.closeTransfer();
    this.closeDetail();
  }

  toggleActions(animal: Animal, event: MouseEvent): void {
    event.stopPropagation();
    if (this.actionMenu() === animal.id) {
      this.actionMenu.set(null);
      return;
    }
    const box = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const menuHeight = animal.status === 'ACTIVE' ? 84 : 46;
    const spaceBelow = window.innerHeight - box.bottom;
    this.menuTop = spaceBelow < menuHeight + 8 ? Math.max(8, box.top - menuHeight - 4) : box.bottom + 4;
    this.menuRight = Math.max(8, window.innerWidth - box.right);
    this.actionMenu.set(animal.id);
  }

  viewAnimal(animal: Animal): void {
    this.actionMenu.set(null);
    this.open(animal);
  }

  transferFromMenu(animal: Animal): void {
    this.actionMenu.set(null);
    this.startTransfer(animal);
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

  treatmentLate(task: WorkTask): boolean {
    return !!task.dueOn && task.dueOn.slice(0, 10) < this.healthToday && task.status === 'OPEN';
  }

  speciesPhoto(name: string | null | undefined): string | null {
    return speciesPicture(name);
  }

  herdColor(name: string): string {
    return this.herdSpecies().find(item => item.name === name)?.color || '#1e8330';
  }

  healthColor(status: string): string {
    return healthChartColor(status);
  }

  severityClass(severity: string): string {
    const value = severity.toUpperCase();
    if (value === 'LOW') {
      return 'is-watch';
    }
    if (value === 'HIGH' || value === 'CRITICAL' || value === 'URGENT') {
      return 'is-sick';
    }
    return 'is-other';
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
