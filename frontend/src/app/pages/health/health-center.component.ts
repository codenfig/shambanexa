import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { speciesPicture } from '../../core/catalog-images';
import { apiError, formatNumber } from '../../core/format';
import { Animal, Farm, HealthBoard, HealthCase, HealthTreatment, HealthVaccination, HerdBatch, InventoryItem, MedicalSupplier, Species, StockBatch, WorkTask } from '../../core/models';
import { TanzaniaMapComponent, TanzaniaPin } from '../../shared/tanzania-map/tanzania-map.component';

interface Notice {
  tone: 'bad' | 'warn' | 'ok';
  title: string;
  detail: string;
}

interface CalEvent {
  date: string;
  kind: string;
  title: string;
  farmId: string;
  subjectId: string;
  vet: string;
}

interface ShopRow {
  shop: MedicalSupplier;
  standing: string;
  tone: 'ok' | 'warn' | 'bad' | 'muted';
  distance: number | null;
}

@Component({
  selector: 'app-health-center',
  imports: [RouterLink, TanzaniaMapComponent],
  templateUrl: './health-center.component.html',
  styleUrl: './health-center.component.css'
})
export class HealthCenterComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);

  loading = signal(true);
  loadError = signal('');
  board = signal<HealthBoard | null>(null);
  animals = signal<Animal[]>([]);
  herds = signal<HerdBatch[]>([]);
  farms = signal<Farm[]>([]);
  species = signal<Species[]>([]);
  cases = signal<HealthCase[]>([]);
  tasks = signal<WorkTask[]>([]);
  items = signal<InventoryItem[]>([]);
  batches = signal<StockBatch[]>([]);
  shops = signal<MedicalSupplier[]>([]);
  pendingShops = signal<MedicalSupplier[]>([]);
  totalAnimals = signal(0);
  speciesRows = signal<{ name: string; count: number; color: string }[]>([]);

  sheet = signal('');
  saving = signal(false);
  formError = signal('');
  query = signal('');
  farmFilter = signal('');
  subjectFilter = signal('');
  vetFilter = signal('');
  activityFilter = signal('ALL');
  shopQuery = signal('');
  standingFilter = signal('ALL');
  originFarmId = signal('');
  maxKm = signal('ANY');
  pickedShopId = signal('');
  calendarView = signal<'month' | 'week' | 'day'>('month');
  cursor = signal(this.today().slice(0, 7));
  selectedDay = signal(this.today());
  drafts = signal<Record<string, string>>({});

  tFarm = signal('');
  tSubjectKind = signal('animal');
  tSubject = signal('');
  tSymptoms = signal('');
  tDiagnosis = signal('');
  tDescription = signal('');
  tVet = signal('');
  tStatus = signal('PLANNED');
  tStarted = signal(this.today());
  tOutcome = signal('');
  tFollow = signal('');
  tItem = signal('');
  tBatch = signal('');
  tQuantity = signal('');
  tWithdrawal = signal('');
  tOverride = signal('');

  vFarm = signal('');
  vSubjectKind = signal('herd');
  vSubject = signal('');
  vProtocol = signal('');
  vSpecies = signal('');
  vAge = signal('');
  vVaccine = signal('');
  vVet = signal('');
  vStatus = signal('PLANNED');
  vGiven = signal('');
  vDue = signal('');
  vNotes = signal('');
  vItem = signal('');
  vBatch = signal('');
  vQuantity = signal('');
  vWithdrawal = signal('');
  vOverride = signal('');

  pSpecies = signal('');
  pName = signal('');
  pAge = signal('');
  pInterval = signal('');
  pItem = signal('');
  pNotes = signal('');

  readonly speciesPhoto = speciesPicture;

  constructor() {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.loadError.set('');
    forkJoin({
      board: this.api.healthCenter(),
      dashboard: this.api.dashboard(),
      cases: this.api.healthCases(),
      tasks: this.api.tasks(),
      items: this.api.inventory(),
      batches: this.api.inventoryBatches(),
      shops: this.api.medicalSuppliers(),
      animals: this.api.animals({ size: 100 }),
      herds: this.api.batches(),
      farms: this.api.farms(),
      species: this.api.species()
    }).subscribe({
      next: result => {
        this.board.set(result.board);
        this.cases.set(result.cases);
        this.tasks.set(result.tasks);
        this.items.set(result.items);
        this.batches.set(result.batches);
        this.animals.set(result.animals.items);
        this.herds.set(result.herds);
        this.farms.set(result.farms);
        this.species.set(result.species);
        this.totalAnimals.set(result.dashboard.totalAnimals);
        this.speciesRows.set(result.dashboard.species);
        const listed = result.shops.suppliers;
        const own = result.shops.ownShop;
        const pending = result.shops.pendingReview;
        const merged = [...listed];
        if (own && !merged.some(shop => shop.id === own.id)) {
          merged.push(own);
        }
        for (const shop of pending) {
          if (!merged.some(row => row.id === shop.id)) {
            merged.push(shop);
          }
        }
        this.shops.set(merged);
        this.pendingShops.set(pending);
        this.loading.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.loadError.set(apiError(error, 'Animal health could not be loaded.'));
      }
    });
  }

  canRecord(): boolean {
    return this.board()?.canRecord ?? this.auth.session()?.canWriteOperations ?? false;
  }

  canApprove(): boolean {
    return this.board()?.canApproveProtocols ?? !!(this.auth.session()?.canAdvise || this.auth.session()?.canManageAccess);
  }

  count(value: number): string {
    return formatNumber(value);
  }

  money(value: number | null): string {
    if (value === null || value === undefined) {
      return 'No unit cost recorded';
    }
    return `US$${formatNumber(value, 2)}`;
  }

  dayLabel(value: string | null): string {
    if (!value) {
      return '—';
    }
    return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${value}T00:00:00Z`));
  }

  statusLabel(value: string): string {
    const labels: Record<string, string> = {
      PLANNED: 'Planned',
      IN_PROGRESS: 'In progress',
      COMPLETED: 'Completed',
      CANCELLED: 'Cancelled',
      MISSED: 'Missed',
      OVERDUE: 'Overdue',
      OPEN: 'Open'
    };
    return labels[value] || value;
  }

  openCases(): number {
    return this.cases().filter(row => row.caseStatus === 'OPEN').length;
  }

  underTreatment(): number {
    return this.openCases() + (this.board()?.report.inProgress ?? 0);
  }

  medicinesInStock(): number {
    return this.items().filter(item => item.category === 'MEDICINE' && item.available > 0).length;
  }

  medicineLines(): number {
    return this.items().filter(item => item.category === 'MEDICINE').length;
  }

  speciesSlices() {
    const rows = this.speciesRows();
    const total = rows.reduce((sum, row) => sum + row.count, 0) || 1;
    return rows.map(row => ({
      ...row,
      share: Math.round(row.count / total * 100),
      photo: speciesPicture(row.name)
    }));
  }

  donut(): string {
    const rows = this.speciesRows();
    const total = rows.reduce((sum, row) => sum + row.count, 0);
    if (!total) {
      return '#e7efe9';
    }
    let cursor = 0;
    const parts = rows.map(row => {
      const start = cursor;
      cursor += row.count / total * 100;
      return `${row.color || '#1e8330'} ${start}% ${cursor}%`;
    });
    return `conic-gradient(${parts.join(',')})`;
  }

  upcoming = computed(() => (this.board()?.vaccinations ?? [])
    .filter(row => row.displayStatus === 'PLANNED' || row.displayStatus === 'OVERDUE')
    .filter(row => this.matchesRecord(row.farmId, row.animalId || row.herdId || '', row.veterinarianName, 'Vaccination'))
    .sort((a, b) => (a.nextDueOn || '').localeCompare(b.nextDueOn || '')));

  recent = computed(() => this.filteredTreatments().slice(0, 8));

  filteredTreatments = computed(() => (this.board()?.treatments ?? []).filter(row => {
    const text = `${row.subject} ${row.diagnosis} ${row.farmName} ${row.veterinarianName}`.toLowerCase();
    return this.matchesRecord(row.farmId, row.animalId || row.herdId || '', row.veterinarianName, 'Treatment')
      && text.includes(this.query().trim().toLowerCase());
  }));

  filteredVaccinations = computed(() => (this.board()?.vaccinations ?? []).filter(row => {
    const text = `${row.subject} ${row.vaccineName} ${row.farmName} ${row.veterinarianName}`.toLowerCase();
    return this.matchesRecord(row.farmId, row.animalId || row.herdId || '', row.veterinarianName, 'Vaccination')
      && text.includes(this.query().trim().toLowerCase());
  }));

  notices = computed(() => this.buildNotices());

  medicineRows = computed(() => {
    const items = new Map(this.items().filter(item => item.category === 'MEDICINE').map(item => [item.id, item]));
    return this.batches()
      .filter(batch => items.has(batch.itemId) && batch.status !== 'DISPOSED')
      .map(batch => {
        const item = items.get(batch.itemId)!;
        const held = !this.canAdminister(batch);
        const out = item.available <= 0;
        return {
          batch,
          item,
          blocked: held || out,
          label: held ? 'Blocked' : out ? 'Out of stock' : 'Usable',
          tone: held ? 'bad' : out ? 'warn' : 'ok'
        };
      });
  });

  shopRows = computed(() => this.decorateShops());

  pins = computed<TanzaniaPin[]>(() => this.shopRows().map(row => ({
    id: row.shop.id,
    label: row.shop.name,
    title: `${row.shop.name} · ${row.standing}`,
    latitude: row.shop.latitude,
    longitude: row.shop.longitude,
    tone: row.tone === 'ok' ? 'ok' : row.tone === 'bad' ? 'alert' : 'warn'
  })));

  pickedShop = computed(() => this.shopRows().find(row => row.shop.id === this.pickedShopId()) ?? null);

  supplierRegions = computed(() => {
    const counts = new Map<string, number>();
    for (const row of this.shopRows()) {
      if (row.standing === 'Verified') {
        counts.set(row.shop.region, (counts.get(row.shop.region) ?? 0) + 1);
      }
    }
    return [...counts.entries()].map(([region, count]) => ({ region, count }));
  });

  monthTitle(): string {
    return new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${this.cursor()}-01T00:00:00Z`));
  }

  weekDays(): string[] {
    const start = this.shift(this.selectedDay(), -new Date(`${this.selectedDay()}T00:00:00Z`).getUTCDay());
    return Array.from({ length: 7 }, (_, index) => this.shift(start, index));
  }

  monthCells(): { date: string; inMonth: boolean }[] {
    const [year, month] = this.cursor().split('-').map(Number);
    const first = new Date(Date.UTC(year, month - 1, 1));
    const count = new Date(Date.UTC(year, month, 0)).getUTCDate();
    const cells: { date: string; inMonth: boolean }[] = [];
    for (let index = 0; index < first.getUTCDay(); index++) {
      cells.push({ date: this.shift(`${this.cursor()}-01`, index - first.getUTCDay()), inMonth: false });
    }
    for (let day = 1; day <= count; day++) {
      const date = `${this.cursor()}-${String(day).padStart(2, '0')}`;
      cells.push({ date, inMonth: true });
    }
    while (cells.length % 7 !== 0) {
      const last = cells[cells.length - 1].date;
      cells.push({ date: this.shift(last, 1), inMonth: false });
    }
    return cells;
  }

  eventsOn(date: string): CalEvent[] {
    return this.calendarEvents().filter(event => event.date === date);
  }

  chips(date: string): CalEvent[] {
    return this.eventsOn(date).slice(0, 3);
  }

  calendarCells(): { date: string; inMonth: boolean }[] {
    if (this.calendarView() === 'week') {
      return this.weekDays().map(date => ({ date, inMonth: true }));
    }
    return this.monthCells();
  }

  shiftView(amount: number): void {
    if (this.calendarView() === 'month') {
      const [year, month] = this.cursor().split('-').map(Number);
      const next = new Date(Date.UTC(year, month - 1 + amount, 1));
      this.cursor.set(next.toISOString().slice(0, 7));
      return;
    }
    const step = this.calendarView() === 'week' ? 7 : 1;
    const day = this.shift(this.selectedDay(), amount * step);
    this.selectedDay.set(day);
    this.cursor.set(day.slice(0, 7));
  }

  pickDay(date: string): void {
    this.selectedDay.set(date);
    this.cursor.set(date.slice(0, 7));
    if (this.calendarView() === 'month') {
      this.calendarView.set('day');
    }
  }

  coverage(): string {
    const report = this.board()?.report;
    if (!report) {
      return '—';
    }
    const total = report.completedVaccinations + report.plannedVaccinations + report.overdue + report.missed;
    if (!total) {
      return 'No vaccination records yet';
    }
    return `${Math.round(report.completedVaccinations / total * 100)}% completed`;
  }

  activeProtocols() {
    return (this.board()?.protocols ?? []).filter(row => row.active);
  }

  hasWithdrawal(): boolean {
    return this.filteredTreatments().some(row => !!row.dose?.withdrawalEndsOn)
      || this.filteredVaccinations().some(row => !!row.withdrawalEndsOn);
  }

  treatmentsThisMonth(): number {
    const month = this.today().slice(0, 7);
    return (this.board()?.treatments ?? []).filter(row => row.status !== 'CANCELLED' && row.startedOn.startsWith(month)).length;
  }

  openSheet(name: string): void {
    this.formError.set('');
    const vet = this.auth.session()?.fullName || '';
    if (name === 'treatment') {
      this.tFarm.set(this.farms()[0]?.id || '');
      this.tSubjectKind.set('animal');
      this.tSubject.set('');
      this.tSymptoms.set('');
      this.tDiagnosis.set('');
      this.tDescription.set('');
      this.tVet.set(vet);
      this.tStatus.set('PLANNED');
      this.tStarted.set(this.today());
      this.tOutcome.set('');
      this.tFollow.set('');
      this.tItem.set('');
      this.tBatch.set('');
      this.tQuantity.set('');
      this.tWithdrawal.set('');
      this.tOverride.set('');
    }
    if (name === 'vaccination') {
      this.vFarm.set(this.farms()[0]?.id || '');
      this.vSubjectKind.set('herd');
      this.vSubject.set('');
      this.vProtocol.set('');
      this.vSpecies.set(this.species()[0]?.name || '');
      this.vAge.set('');
      this.vVaccine.set('');
      this.vVet.set(vet);
      this.vStatus.set('PLANNED');
      this.vGiven.set('');
      this.vDue.set('');
      this.vNotes.set('');
      this.vItem.set('');
      this.vBatch.set('');
      this.vQuantity.set('');
      this.vWithdrawal.set('');
      this.vOverride.set('');
    }
    if (name === 'protocol') {
      this.pSpecies.set(this.species()[0]?.name || '');
      this.pName.set('');
      this.pAge.set('');
      this.pInterval.set('');
      this.pItem.set('');
      this.pNotes.set('');
    }
    this.sheet.set(name);
  }

  closeSheet(): void {
    if (!this.saving()) {
      this.sheet.set('');
    }
  }

  subjects(kind: string, farmId: string): { id: string; label: string }[] {
    if (kind === 'herd') {
      return this.herds().filter(row => row.farmId === farmId).map(row => ({ id: row.id, label: `${row.name} · ${row.quantity}` }));
    }
    return this.animals().filter(row => row.farmId === farmId).map(row => ({ id: row.id, label: row.name ? `${row.tag} · ${row.name}` : row.tag }));
  }

  farmMedicines(farmId: string): InventoryItem[] {
    return this.items().filter(item => item.category === 'MEDICINE' && item.farmId === farmId);
  }

  usableBatches(itemId: string): StockBatch[] {
    const item = this.items().find(row => row.id === itemId);
    if (!item || item.available <= 0) {
      return [];
    }
    return this.batches().filter(batch => batch.itemId === itemId && this.canAdminister(batch));
  }

  needsOverride(itemId: string, batchId: string): boolean {
    const rows = this.usableBatches(itemId);
    return !!batchId && rows.length > 0 && rows[0].id !== batchId;
  }

  applyProtocol(id: string): void {
    this.vProtocol.set(id);
    const protocol = this.board()?.protocols.find(row => row.id === id);
    if (!protocol) {
      return;
    }
    this.vSpecies.set(protocol.speciesName);
    this.vAge.set(protocol.ageGroup);
    this.vVaccine.set(protocol.name);
  }

  saveTreatment(event: Event): void {
    event.preventDefault();
    const dose = this.tItem() ? {
      itemId: this.tItem(),
      batchId: this.tBatch() || null,
      quantity: Number(this.tQuantity()),
      withdrawalDays: this.tWithdrawal().trim() ? Number(this.tWithdrawal()) : null,
      overrideReason: this.tOverride().trim() || null
    } : null;
    this.submit(this.api.recordTreatment({
      farmId: this.tFarm(),
      animalId: this.tSubjectKind() === 'animal' ? this.tSubject() : null,
      herdId: this.tSubjectKind() === 'herd' ? this.tSubject() : null,
      symptoms: this.tSymptoms(),
      diagnosis: this.tDiagnosis(),
      description: this.tDescription(),
      veterinarianName: this.tVet(),
      status: this.tStatus(),
      startedOn: this.tStarted(),
      outcome: this.tOutcome().trim() || null,
      followUpOn: this.tFollow() || null,
      dose
    }));
  }

  saveVaccination(event: Event): void {
    event.preventDefault();
    this.submit(this.api.recordVaccination({
      farmId: this.vFarm(),
      animalId: this.vSubjectKind() === 'animal' ? this.vSubject() : null,
      herdId: this.vSubjectKind() === 'herd' ? this.vSubject() : null,
      protocolId: this.vProtocol() || null,
      speciesName: this.vSpecies(),
      ageGroup: this.vAge(),
      vaccineName: this.vVaccine(),
      itemId: this.vItem() || null,
      batchId: this.vBatch() || null,
      quantity: this.vItem() ? Number(this.vQuantity()) : null,
      withdrawalDays: this.vWithdrawal().trim() ? Number(this.vWithdrawal()) : null,
      overrideReason: this.vOverride().trim() || null,
      administeredOn: this.vGiven() || null,
      nextDueOn: this.vDue() || null,
      veterinarianName: this.vVet(),
      notes: this.vNotes(),
      status: this.vStatus()
    }));
  }

  saveProtocol(event: Event): void {
    event.preventDefault();
    this.submit(this.api.saveVaccinationProtocol({
      speciesName: this.pSpecies(),
      name: this.pName(),
      ageGroup: this.pAge(),
      intervalDays: Number(this.pInterval()),
      itemId: this.pItem() || null,
      notes: this.pNotes()
    }));
  }

  draftStatus(id: string, current: string): string {
    return this.drafts()[id] || current;
  }

  setDraft(id: string, value: string): void {
    this.drafts.update(rows => ({ ...rows, [id]: value }));
  }

  updateTreatment(row: HealthTreatment): void {
    const status = this.draftStatus(row.id, row.status);
    this.saving.set(true);
    this.api.updateTreatment(row.id, { status, outcome: row.outcome, followUpOn: row.followUpOn }).subscribe({
      next: () => {
        this.saving.set(false);
        this.load();
      },
      error: error => {
        this.saving.set(false);
        this.formError.set(apiError(error, 'The treatment could not be updated.'));
      }
    });
  }

  updateVaccination(row: HealthVaccination): void {
    const status = this.draftStatus(row.id, row.displayStatus === 'OVERDUE' ? 'PLANNED' : row.status);
    this.saving.set(true);
    this.api.updateVaccination(row.id, { status, administeredOn: row.administeredOn, nextDueOn: row.nextDueOn }).subscribe({
      next: () => {
        this.saving.set(false);
        this.load();
      },
      error: error => {
        this.saving.set(false);
        this.formError.set(apiError(error, 'The vaccination could not be updated.'));
      }
    });
  }

  text(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).value;
  }

  private submit(request: Observable<unknown>): void {
    this.saving.set(true);
    this.formError.set('');
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.sheet.set('');
        this.load();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.formError.set(apiError(error, 'The health record could not be saved.'));
      }
    });
  }

  private matchesRecord(farmId: string, subjectId: string, vet: string, kind: string): boolean {
    if (this.farmFilter() && this.farmFilter() !== farmId) {
      return false;
    }
    if (this.subjectFilter() && this.subjectFilter() !== subjectId) {
      return false;
    }
    if (this.vetFilter() && !vet.toLowerCase().includes(this.vetFilter().trim().toLowerCase())) {
      return false;
    }
    return this.activityFilter() === 'ALL' || this.activityFilter() === kind;
  }

  private calendarEvents(): CalEvent[] {
    const events: CalEvent[] = [];
    for (const row of this.board()?.treatments ?? []) {
      if (row.status === 'CANCELLED') {
        continue;
      }
      events.push({ date: row.startedOn, kind: 'Treatment', title: row.diagnosis, farmId: row.farmId, subjectId: row.animalId || row.herdId || '', vet: row.veterinarianName });
      if (row.followUpOn) {
        events.push({ date: row.followUpOn, kind: 'Follow-up', title: `${row.subject} follow-up`, farmId: row.farmId, subjectId: row.animalId || row.herdId || '', vet: row.veterinarianName });
      }
    }
    for (const row of this.board()?.vaccinations ?? []) {
      if (row.status === 'CANCELLED') {
        continue;
      }
      const date = row.nextDueOn || row.administeredOn;
      if (date) {
        events.push({ date, kind: 'Vaccination', title: row.vaccineName, farmId: row.farmId, subjectId: row.animalId || row.herdId || '', vet: row.veterinarianName });
      }
    }
    for (const row of this.cases()) {
      events.push({ date: row.openedOn, kind: 'Examination', title: row.title, farmId: row.farmId, subjectId: '', vet: '' });
    }
    for (const row of this.tasks()) {
      if (row.status === 'DONE' || !this.healthTask(row)) {
        continue;
      }
      events.push({ date: row.dueOn, kind: 'Appointment', title: row.title, farmId: row.farmId, subjectId: '', vet: row.assigneeName || '' });
    }
    return events.filter(event => {
      if (this.farmFilter() && event.farmId !== this.farmFilter()) {
        return false;
      }
      if (this.subjectFilter() && event.subjectId !== this.subjectFilter()) {
        return false;
      }
      if (this.vetFilter() && !event.vet.toLowerCase().includes(this.vetFilter().trim().toLowerCase())) {
        return false;
      }
      return this.activityFilter() === 'ALL' || this.activityFilter() === event.kind;
    });
  }

  private buildNotices(): Notice[] {
    const today = this.today();
    const soon = this.shift(today, 14);
    const notices: Notice[] = [];
    for (const row of this.cases()) {
      if (row.caseStatus === 'OPEN') {
        notices.push({ tone: 'warn', title: row.title, detail: `${row.farmName} · health case · opened ${this.dayLabel(row.openedOn)}` });
      }
    }
    for (const row of this.board()?.vaccinations ?? []) {
      if (row.displayStatus === 'OVERDUE') {
        notices.push({ tone: 'bad', title: `Overdue vaccination · ${row.vaccineName}`, detail: `${row.subject} · due ${this.dayLabel(row.nextDueOn)}` });
      } else if (row.displayStatus === 'PLANNED' && row.nextDueOn && row.nextDueOn <= soon) {
        notices.push({ tone: 'warn', title: `Upcoming vaccination · ${row.vaccineName}`, detail: `${row.subject} · due ${this.dayLabel(row.nextDueOn)}` });
      }
      if (row.withdrawalEndsOn && row.withdrawalEndsOn <= soon) {
        notices.push({ tone: row.withdrawalEndsOn < today ? 'ok' : 'warn', title: 'Withdrawal period', detail: `${row.subject} · ${row.vaccineName} · ends ${this.dayLabel(row.withdrawalEndsOn)}` });
      }
    }
    for (const row of this.board()?.treatments ?? []) {
      if ((row.status === 'PLANNED' || row.status === 'IN_PROGRESS') && row.followUpOn && row.followUpOn <= soon) {
        notices.push({ tone: row.followUpOn < today ? 'bad' : 'warn', title: 'Treatment follow-up', detail: `${row.subject} · ${row.diagnosis} · ${this.dayLabel(row.followUpOn)}` });
      }
      if (row.dose?.withdrawalEndsOn && row.dose.withdrawalEndsOn <= soon) {
        notices.push({ tone: row.dose.withdrawalEndsOn < today ? 'ok' : 'warn', title: 'Withdrawal period', detail: `${row.subject} · ${row.dose.itemName} · ends ${this.dayLabel(row.dose.withdrawalEndsOn)}` });
      }
    }
    for (const row of this.medicineRows()) {
      if (this.canAdminister(row.batch) && row.batch.daysLeft != null && row.batch.daysLeft <= 30) {
        notices.push({ tone: 'warn', title: `Medicine expiry · ${row.batch.productName}`, detail: `${row.batch.lotCode} · ${row.batch.farmName} · ${row.batch.daysLeft} days` });
      }
    }
    for (const row of this.tasks()) {
      if (row.status !== 'DONE' && this.healthTask(row)) {
        notices.push({ tone: row.dueOn < today ? 'bad' : 'warn', title: `Task · ${row.title}`, detail: `${row.farmName} · due ${this.dayLabel(row.dueOn)}` });
      }
    }
    return notices;
  }

  private decorateShops(): ShopRow[] {
    const origin = this.farms().find(farm => farm.id === this.originFarmId());
    const max = this.maxKm() === 'ANY' ? null : Number(this.maxKm());
    const query = this.shopQuery().trim().toLowerCase();
    return this.shops()
      .map(shop => {
        const standing = this.standing(shop);
        const distance = origin ? this.km(origin.latitude, origin.longitude, shop.latitude, shop.longitude) : null;
        return { shop, standing: standing.label, tone: standing.tone, distance };
      })
      .filter(row => {
        if (this.standingFilter() !== 'ALL' && row.standing !== this.standingFilter()) {
          return false;
        }
        if (max != null && (row.distance == null || row.distance > max)) {
          return false;
        }
        const products = `${row.shop.productsOffered} ${row.shop.medicines.map(item => item.name).join(' ')}`.toLowerCase();
        const text = `${row.shop.name} ${row.shop.region} ${row.shop.district} ${row.shop.licenseNumber} ${products}`;
        return text.toLowerCase().includes(query);
      })
      .sort((a, b) => (a.distance ?? 99999) - (b.distance ?? 99999));
  }

  private standing(shop: MedicalSupplier): { label: string; tone: 'ok' | 'warn' | 'bad' | 'muted' } {
    if (shop.licenseExpiresOn && shop.licenseExpiresOn < this.today()) {
      return { label: 'Expired licence', tone: 'bad' };
    }
    if (shop.approval === 'APPROVED') {
      return { label: 'Verified', tone: 'ok' };
    }
    if (shop.approval === 'PENDING') {
      return { label: 'Pending verification', tone: 'warn' };
    }
    return { label: 'Unverified', tone: 'muted' };
  }

  private canAdminister(batch: StockBatch): boolean {
    return batch.quantityRemaining > 0
      && (batch.daysLeft == null || batch.daysLeft >= 0)
      && batch.status !== 'DISPOSED'
      && batch.status !== 'QUARANTINED'
      && batch.status !== 'EXPIRED';
  }

  private healthTask(task: WorkTask): boolean {
    return /vaccin|health|treatment|hoof/i.test(`${task.title} ${task.taskType}`);
  }

  private km(lat1: number, lon1: number, lat2: number, lon2: number): number {
    const radius = 6371;
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon / 2) ** 2;
    return Math.round(2 * radius * Math.asin(Math.sqrt(a)) * 10) / 10;
  }

  private today(): string {
    return new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Dar_es_Salaam', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  }

  private shift(day: string, amount: number): string {
    const date = new Date(`${day}T00:00:00Z`);
    date.setUTCDate(date.getUTCDate() + amount);
    return date.toISOString().slice(0, 10);
  }
}
