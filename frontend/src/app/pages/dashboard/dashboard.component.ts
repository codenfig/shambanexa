import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ActivityBoardComponent } from './activity-board.component';
import { AdviserBoardComponent } from './adviser-board.component';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ActivityItem, AlertItem, Dashboard, Farm, FishCycle, HealthCase, InventoryItem, TrendPoint, WorkTask } from '../../core/models';
import { CHART_CAUTION, CHART_DANGER, CHART_SAFE, chartSlice as sliceColor } from '../../core/chart-colors';
import { formatMoney, formatMonth, formatNumber, relativeTime, signedPercent } from '../../core/format';
import { statusOf } from '../inventory/inventory.format';
import { MapCallout, TanzaniaMapComponent, TanzaniaPin } from '../../shared/tanzania-map/tanzania-map.component';
import { healthAlert, healthLabel, healthNotice, healthPhrase } from '../../core/disease';
import { ToastService } from '../../core/toast.service';

interface ChartPoint {
  x: number;
  yMilk: number;
  yRevenue: number;
  month: string;
  milk: number;
  revenue: number;
}

interface ChartModel {
  width: number;
  height: number;
  milkPath: string;
  revenuePath: string;
  area: string;
  points: ChartPoint[];
  ticks: { y: number; milk: string; revenue: string }[];
}

interface PieSlice {
  label: string;
  count: number;
  share: number;
  color: string;
  shade: string;
  d: string;
  lx: number;
  ly: number;
  nx: number;
  ny: number;
  lead: boolean;
}

interface OverviewPie {
  id: string;
  title: string;
  href: string;
  center: string;
  unit: string;
  note: string;
  aria: string;
  slices: PieSlice[];
  gradient: string;
}

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, TanzaniaMapComponent, ActivityBoardComponent, AdviserBoardComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);

  data = signal<Dashboard | null>(null);
  farms = signal<Farm[]>([]);
  tasks = signal<WorkTask[] | null>(null);
  inventory = signal<InventoryItem[] | null>(null);
  cycles = signal<FishCycle[] | null>(null);
  cases = signal<HealthCase[] | null>(null);
  alerts = signal<AlertItem[] | null>(null);
  error = signal('');
  loading = signal(true);
  zoom = signal(1);
  exporting = signal(false);
  selectedId = signal<string | null>(null);
  hoveredId = signal<string | null>(null);
  chartIndex = signal<number | null>(null);
  hovered = signal<{ pie: string; label: string } | null>(null);
  private lastSlice = new Map<string, string>();
  showActivity = signal(false);

  constructor() {
    this.route.fragment.subscribe(fragment => this.showActivity.set(fragment === 'activity'));
    this.load();
  }

  canAddFarm(): boolean {
    return !!this.auth.session()?.allFarms;
  }

  canReport = computed(() => this.auth.session()?.canWriteOperations === true);
  adviser = computed(() => this.auth.session()?.canAdvise === true);

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.tasks.set(null);
    this.inventory.set(null);
    this.cycles.set(null);
    this.cases.set(null);
    this.alerts.set(null);
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
    this.api.farms().subscribe({ next: farms => this.farms.set(farms), error: () => this.farms.set([]) });
    this.api.tasks().subscribe({
      next: tasks => this.tasks.set(tasks.filter(task => task.status === 'OPEN').sort((a, b) => a.dueOn.localeCompare(b.dueOn))),
      error: () => this.tasks.set([])
    });
    this.api.inventory().subscribe({ next: items => this.inventory.set(items), error: () => this.inventory.set([]) });
    this.api.fishCycles().subscribe({ next: cycles => this.cycles.set(cycles), error: () => this.cycles.set([]) });
    this.api.healthCases().subscribe({
      next: cases => this.cases.set(cases.filter(item => item.caseStatus === 'OPEN')),
      error: () => this.cases.set([])
    });
    this.api.alerts().subscribe({ next: alerts => this.alerts.set(alerts), error: () => this.alerts.set(null) });
  }

  fmt = formatNumber;
  money = formatMoney;
  when = relativeTime;
  signed = signedPercent;

  pies = computed(() => {
    const data = this.data();
    if (!data) {
      return [];
    }
    return [this.speciesPie(data), this.farmPie(data), this.alertPie(data), this.stockPie()];
  });
  openTasks = computed(() => this.tasks() ?? []);
  lowStock = computed(() => (this.inventory() ?? []).filter(item => item.lowStock));
  openCases = computed(() => this.cases() ?? []);
  mortalityCycles = computed(() => (this.cycles() ?? []).filter(cycle => Number(cycle.mortality) > 0));

  activeFarmCount(data: Dashboard): number {
    return data.locations.filter(pin => pin.status === 'ACTIVE').length;
  }

  headerDate(iso: string): string {
    return new Intl.DateTimeFormat('en-GB', {
      weekday: 'long',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      timeZone: 'Africa/Dar_es_Salaam'
    }).format(new Date(iso));
  }

  signedAmount(value: number): string {
    const sign = value > 0 ? '+' : '';
    return `${sign}${formatNumber(value)}`;
  }

  severitySummary(data: Dashboard): string {
    const source = this.alerts();
    if (!source || source.length !== data.openAlertCount) {
      return 'Open records';
    }
    const count = (severity: string) => source.filter(alert => alert.severity === severity).length;
    return `${count('CRITICAL')} critical · ${count('WARNING')} warning · ${count('INFO')} info`;
  }

  milkNote(data: Dashboard): string {
    const delta = Number(data.milkDeltaVsYesterday);
    if (delta === 0) {
      return 'Milk recorded today matches yesterday.';
    }
    const direction = delta > 0 ? 'above' : 'below';
    return `Milk today is ${formatNumber(Math.abs(delta))} L ${direction} yesterday.`;
  }

  trendLabel(trends: TrendPoint[]): string {
    if (trends.length === 0) {
      return 'Production';
    }
    const first = formatMonth(trends[0].month);
    const last = formatMonth(trends[trends.length - 1].month);
    return first === last ? `Production · ${first}` : `Production · ${first} – ${last}`;
  }

  share(count: number, species: { count: number }[]): number {
    const total = species.reduce((sum, item) => sum + Number(item.count), 0);
    return total ? (Number(count) / total) * 100 : 0;
  }

  spark(trends: TrendPoint[], key: 'milkLitres' | 'revenue'): string {
    const values = trends.map(point => Number(point[key]));
    if (values.length < 2) {
      return '';
    }
    const width = 84;
    const height = 28;
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min || 1;
    return values.map((value, index) => {
      const x = (index / (values.length - 1)) * width;
      const y = height - ((value - min) / span) * (height - 2) - 1;
      return `${index === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`;
    }).join(' ');
  }

  visibleAlerts(data: Dashboard): AlertItem[] {
    return this.alerts() ?? data.alerts;
  }

  boundaries = computed(() => this.farms()
    .filter(farm => (farm.boundary?.length ?? 0) >= 3)
    .map(farm => ({ id: farm.id, points: farm.boundary })));

  mapPins = computed<TanzaniaPin[]>(() => {
    const board = this.data();
    if (!board) {
      return [];
    }
    const source = this.alerts() ?? board.alerts;
    return board.locations.map(pin => {
      const alert = healthAlert(source, pin.id);
      return {
        id: pin.id,
        label: pin.name,
        title: alert
          ? `${pin.name}, ${pin.region}. ${healthPhrase(alert)}: ${healthLabel(alert)}.`
          : `${pin.name}, ${pin.region}. ${formatNumber(pin.animalCount)} animals. ${pin.status}.`,
        latitude: Number(pin.latitude),
        longitude: Number(pin.longitude),
        weight: Number(pin.animalCount),
        tone: this.toneFor(pin.id, source),
        notice: alert ? healthNotice(alert) : undefined
      };
    });
  });

  callout = computed<MapCallout | null>(() => {
    const id = this.hoveredId() || this.selectedId();
    const pin = this.data()?.locations.find(item => item.id === id);
    if (!pin) {
      return null;
    }
    const farm = this.farms().find(item => item.id === pin.id);
    const alert = healthAlert(this.alerts() ?? this.data()?.alerts ?? [], pin.id);
    const lines = [this.clip(pin.name, 22)];
    if (alert) {
      lines.push(this.clip(healthLabel(alert), 28));
      lines.push(healthPhrase(alert));
    }
    lines.push(`${formatNumber(pin.animalCount)} animals`);
    lines.push(farm?.managerName ? `Manager: ${this.clip(farm.managerName, 18)}` : this.clip(pin.region, 22));
    return { id: pin.id, lines };
  });

  selectedPin = computed(() => this.data()?.locations.find(pin => pin.id === this.selectedId()) ?? null);
  selectedDisease = computed(() => {
    const pin = this.selectedPin();
    return pin ? healthAlert(this.alerts() ?? this.data()?.alerts ?? [], pin.id) : undefined;
  });
  diseaseLabel = healthLabel;
  selectedFarm = computed(() => this.farms().find(farm => farm.id === this.selectedId()) ?? null);

  chart = computed<ChartModel | null>(() => {
    const trends = this.data()?.trends ?? [];
    if (trends.length < 2) {
      return null;
    }
    const width = 640;
    const height = 236;
    const left = 46;
    const right = 72;
    const top = 12;
    const bottom = 28;
    const innerW = width - left - right;
    const innerH = height - top - bottom;
    const milk = trends.map(point => Number(point.milkLitres));
    const revenue = trends.map(point => Number(point.revenue));
    const milkMax = this.axisMax(Math.max(...milk, 1));
    const revenueMax = this.axisMax(Math.max(...revenue, 1));
    const x = (index: number) => left + (index * innerW) / (trends.length - 1);
    const yOf = (value: number, max: number) => top + (1 - value / max) * innerH;
    const points = trends.map((point, index) => ({
      x: x(index),
      yMilk: yOf(Number(point.milkLitres), milkMax),
      yRevenue: yOf(Number(point.revenue), revenueMax),
      month: formatMonth(point.month),
      milk: Number(point.milkLitres),
      revenue: Number(point.revenue)
    }));
    const line = (key: 'yMilk' | 'yRevenue') => points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point[key]}`).join(' ');
    const ticks = [0, 0.5, 1].map(step => ({
      y: top + (1 - step) * innerH,
      milk: formatNumber(milkMax * step),
      revenue: formatNumber(revenueMax * step)
    }));
    const area = `${line('yMilk')} L ${points[points.length - 1].x} ${top + innerH} L ${points[0].x} ${top + innerH} Z`;
    return { width, height, milkPath: line('yMilk'), revenuePath: line('yRevenue'), area, points, ticks };
  });

  focusPin(id: string): void {
    this.hoveredId.set(id || null);
  }

  pickPin(id: string): void {
    this.selectedId.set(id);
    this.hoveredId.set(id);
  }

  clearSelection(): void {
    this.selectedId.set(null);
    this.hoveredId.set(null);
  }

  alertDetail(alert: AlertItem): string {
    const farm = alert.farmName || 'All farms';
    const body = alert.farmName && alert.body.startsWith(alert.farmName)
      ? alert.body.slice(alert.farmName.length).replace(/^\s*·\s*/, '')
      : alert.body;
    return `${farm} · ${body} · ${relativeTime(alert.occurredAt)}`;
  }

  activityKind(item: ActivityItem): string {
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
    if (item.eventType === 'INVENTORY' || item.eventType === 'MEDICAL') {
      return 'stock';
    }
    if (item.eventType === 'FARM' || item.eventType === 'SETTINGS') {
      return 'farm';
    }
    if (item.eventType === 'FINANCE' || item.eventType === 'MARKET') {
      return 'money';
    }
    if (item.eventType === 'STAFF' || /task|completed/.test(text)) {
      return 'task';
    }
    return 'people';
  }

  activityLine(item: ActivityItem): string {
    const title = item.title.trim();
    const detail = item.detail.trim();
    if (!detail || title.toLowerCase().includes(detail.toLowerCase())) {
      return title;
    }
    if (item.eventType === 'FARM') {
      return `${title} at ${detail}`;
    }
    return `${title} · ${detail}`;
  }

  alertLink(alert: AlertItem): string[] {
    return alert.farmId ? ['/farms', alert.farmId] : ['/alerts'];
  }

  activityLink(item: ActivityItem): string[] {
    if (item.eventType === 'LIVESTOCK') {
      return ['/livestock'];
    }
    if (item.eventType === 'PRODUCTION') {
      return ['/production'];
    }
    if (item.eventType === 'INVENTORY') {
      return ['/inventory'];
    }
    if (item.eventType === 'STAFF') {
      return ['/staff'];
    }
    if (item.eventType === 'ADVICE') {
      return ['/advice'];
    }
    if (item.eventType === 'ADVISER') {
      return ['/advisers'];
    }
    if (item.eventType === 'MEDICAL') {
      return ['/suppliers'];
    }
    if (item.eventType === 'MARKET') {
      return ['/market'];
    }
    if (item.eventType === 'ACCESS') {
      return ['/access'];
    }
    if (item.eventType === 'SETTINGS') {
      return ['/settings'];
    }
    if (item.eventType === 'FINANCE') {
      return ['/analytics'];
    }
    if (item.farmId) {
      return ['/farms', item.farmId];
    }
    return ['/analytics'];
  }

  dueLabel(iso: string): string {
    return new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', timeZone: 'UTC' }).format(new Date(`${iso}T00:00:00Z`));
  }

  typeLabel(value: string): string {
    if (!value) {
      return 'Task';
    }
    return value.charAt(0) + value.slice(1).toLowerCase();
  }

  hoveredPoint(model: ChartModel): ChartPoint | null {
    const index = this.chartIndex();
    return index == null ? null : model.points[index] ?? null;
  }

  tipTransform(point: ChartPoint): string {
    const x = point.x > 430 ? point.x - 168 : point.x + 12;
    const y = Math.max(8, point.yMilk - 62);
    return `translate(${x} ${y})`;
  }

  zoomIn(): void {
    this.zoom.set(Math.min(2.2, Number((this.zoom() + 0.15).toFixed(2))));
  }

  zoomOut(): void {
    this.zoom.set(Math.max(0.85, Number((this.zoom() - 0.15).toFixed(2))));
  }

  exportReport(): void {
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
        this.toast.show('Dashboard export downloaded.');
      },
      error: () => {
        this.exporting.set(false);
        this.toast.show('Export failed.', 'err');
      }
    });
  }

  focusSlice(pieId: string, label: string): void {
    this.lastSlice.set(pieId, label);
    this.hovered.set({ pie: pieId, label });
  }

  clearSlice(): void {
    this.hovered.set(null);
  }

  isOn(pieId: string, label: string): boolean {
    const hit = this.hovered();
    return hit?.pie === pieId && hit.label === label;
  }

  cardSlice(pie: OverviewPie): PieSlice | null {
    const label = this.hovered()?.pie === pie.id ? this.hovered()?.label : this.lastSlice.get(pie.id);
    return pie.slices.find(slice => slice.label === label) ?? null;
  }

  sliceLine(pie: OverviewPie, slice: PieSlice): string {
    if (slice.count === 0) {
      return 'None recorded in this part';
    }
    const count = `${formatNumber(slice.count)} of ${pie.center}`;
    if (pie.id === 'animals') {
      return `${count} recorded animals`;
    }
    if (pie.id === 'farms' && slice.label === 'Clear') {
      return `${count} farms with no warning or critical alert`;
    }
    if (pie.id === 'farms' && slice.label === 'Warning') {
      return `${count} farms with an open warning`;
    }
    if (pie.id === 'farms') {
      return `${count} farms with an open critical alert`;
    }
    if (pie.id === 'alerts' && slice.label === 'Info') {
      return `${count} informational alerts`;
    }
    if (pie.id === 'alerts' && slice.label === 'Warning') {
      return `${count} warnings`;
    }
    if (pie.id === 'alerts') {
      return `${count} critical alerts`;
    }
    if (slice.label === 'In stock') {
      return `${count} items above the reorder point`;
    }
    if (slice.label === 'Out of stock' || slice.label === 'Expired' || slice.label === 'Out or expired') {
      return `${count} items out of stock or past their date`;
    }
    return `${count} items low or expiring within 30 days`;
  }

  /** Stable, accessible species palette independent of API ordering.
   * Species are categories, not health/severity states.
   */
  private livestockColor(name: string, index: number): string {
    const palette: Record<string, string> = {
      cattle: '#176B4A',  // forest green
      goats: '#2563EB',   // blue
      sheep: '#D97706',   // amber
      poultry: '#9333EA'  // violet
    };
    const key = name.trim().toLowerCase();
    const extra = ['#0891B2', '#BE185D', '#4F46E5', '#B45309', '#0F766E'];
    return palette[key] ?? extra[index % extra.length];
  }

  private speciesPie(data: Dashboard): OverviewPie {
    const counts = data.species.map(item => Number(item.count));
    const shares = this.shares(counts);
    const slices = this.arcs(data.species.map((item, index) => ({
      label: item.name,
      count: Number(item.count),
      share: shares[index] ?? 0,
      color: this.livestockColor(item.name, index)
    })));
    const counted = counts.reduce((sum, count) => sum + count, 0);
    const change = data.animalChangePercent !== null
      ? `${signedPercent(data.animalChangePercent)} from last month. `
      : 'No earlier herd count. ';
    const outside = data.totalAnimals > counted ? `${formatNumber(data.totalAnimals - counted)} are outside this mix. ` : '';
    return {
      id: 'animals',
      title: 'Livestock',
      href: '/livestock',
      center: formatNumber(counted || data.totalAnimals),
      unit: 'animals',
      note: slices.length ? `${outside}${formatNumber(data.totalAnimals)} animals. ${change}` : 'No livestock counts recorded.',
      aria: slices.length ? this.pieLabel('Livestock', slices) : 'No livestock counts recorded.',
      slices,
      gradient: this.ring(slices)
    };
  }

  private farmPie(data: Dashboard): OverviewPie {
    const alerts = this.alerts();
    const quarter = `${formatNumber(data.newFarmsThisQuarter)} new this quarter`;
    if (!alerts) {
      return this.pendingPie('farms', 'Farms', '/farms', formatNumber(data.farmCount), 'farms', `${quarter}. Attention split is still loading.`);
    }
    const tones = { ok: 0, warn: 0, alert: 0 };
    let inactive = 0;
    for (const pin of data.locations) {
      const tone = this.toneFor(pin.id, alerts);
      if (pin.status !== 'ACTIVE' && tone === 'ok') {
        inactive += 1;
      } else {
        tones[tone] += 1;
      }
    }
    const slices = this.statusSlices([
      { label: 'Clear', count: tones.ok, color: CHART_SAFE },
      { label: 'Warning', count: tones.warn, color: CHART_CAUTION },
      { label: 'Critical', count: tones.alert, color: CHART_DANGER }
    ]);
    const quiet = inactive ? `${formatNumber(inactive)} not active. ` : '';
    return {
      id: 'farms',
      title: 'Farms',
      href: '/farms',
      center: formatNumber(data.farmCount),
      unit: 'farms',
      note: `${quiet}${quarter}. Clear means no warning or critical alert.`,
      aria: this.pieLabel('Farms', slices),
      slices,
      gradient: this.ring(slices)
    };
  }

  private alertPie(data: Dashboard): OverviewPie {
    const alerts = this.alerts();
    if (!alerts) {
      return this.pendingPie('alerts', 'Open alerts', '/alerts', formatNumber(data.openAlertCount), 'open', 'Severity split is still loading.');
    }
    const open = alerts.filter(alert => alert.status === 'OPEN');
    const count = (severity: string) => open.filter(alert => alert.severity === severity).length;
    const slices = this.statusSlices([
      { label: 'Info', count: count('INFO'), color: CHART_SAFE },
      { label: 'Warning', count: count('WARNING'), color: CHART_CAUTION },
      { label: 'Critical', count: count('CRITICAL'), color: CHART_DANGER }
    ]);
    return {
      id: 'alerts',
      title: 'Open alerts',
      href: '/alerts',
      center: formatNumber(open.length),
      unit: 'open',
      note: 'Green is informational. Red is critical.',
      aria: this.pieLabel('Open alerts', slices),
      slices,
      gradient: this.ring(slices)
    };
  }

  private stockPie(): OverviewPie {
    const items = this.inventory();
    if (!items) {
      return this.pendingPie('stock', 'Stock', '/inventory', '—', 'items', 'Stock status is still loading.');
    }
    const counts = { safe: 0, low: 0, soon: 0, out: 0, expired: 0 };
    for (const item of items) {
      const status = statusOf(item);
      if (status === 'Low stock') counts.low += 1;
      else if (status === 'Expiring') counts.soon += 1;
      else if (status === 'Out of stock') counts.out += 1;
      else if (status === 'Expired') counts.expired += 1;
      else counts.safe += 1;
    }
    const caution = counts.low + counts.soon;
    const danger = counts.out + counts.expired;
    const cautionLabel = counts.low > 0 && counts.soon > 0 ? 'Needs attention' : counts.soon > 0 ? 'Expiring' : 'Low stock';
    const dangerLabel = counts.out > 0 && counts.expired > 0 ? 'Out or expired' : counts.expired > 0 ? 'Expired' : 'Out of stock';
    const slices = items.length
      ? this.statusSlices([
        { label: 'In stock', count: counts.safe, color: CHART_SAFE },
        { label: cautionLabel, count: caution, color: CHART_CAUTION },
        { label: dangerLabel, count: danger, color: CHART_DANGER }
      ])
      : [];
    return {
      id: 'stock',
      title: 'Stock',
      href: '/inventory',
      center: formatNumber(items.length),
      unit: 'items',
      note: items.length
        ? 'Green is in stock. Orange needs attention. Red is out of stock or expired.'
        : 'No stock items are stored.',
      aria: items.length ? this.pieLabel('Stock', slices) : 'No stock items are stored.',
      slices,
      gradient: this.ring(slices)
    };
  }

  private pendingPie(id: string, title: string, href: string, center: string, unit: string, note: string): OverviewPie {
    return {
      id,
      title,
      href,
      center,
      unit,
      note,
      aria: `${title}. ${note}`,
      slices: [],
      gradient: 'conic-gradient(#e7ece8 0 100%)'
    };
  }

  private statusSlices(rows: { label: string; count: number; color: string }[]): PieSlice[] {
    const shares = this.shares(rows.map(row => row.count));
    return this.arcs(rows.map((row, index) => ({ ...row, share: shares[index] ?? 0 })));
  }

  private shares(counts: number[]): number[] {
    const total = counts.reduce((sum, count) => sum + count, 0);
    if (!total) {
      return counts.map(() => 0);
    }
    const raw = counts.map(count => (count / total) * 100);
    const whole = raw.map(value => Math.floor(value));
    let leftover = 100 - whole.reduce((sum, value) => sum + value, 0);
    const order = raw
      .map((value, index) => ({ index, fraction: value - whole[index], count: counts[index] }))
      .sort((a, b) => b.fraction - a.fraction || a.index - b.index);
    for (const item of order) {
      if (leftover <= 0 || item.count <= 0) {
        continue;
      }
      whole[item.index] += 1;
      leftover -= 1;
    }
    return whole;
  }

  private arcs(slices: { label: string; count: number; share: number; color: string }[]): PieSlice[] {
    const total = slices.reduce((sum, slice) => sum + slice.count, 0);
    const leadCount = Math.max(...slices.map(slice => slice.count), 0);
    const parts = slices.filter(slice => slice.count > 0).length;
    let cursor = 0;
    let leadGiven = false;
    return slices.map(slice => {
      const start = cursor;
      const span = total ? slice.count / total : 0;
      cursor += span;
      const mid = start + span / 2;
      const angle = mid * Math.PI * 2 - Math.PI / 2;
      const lead = parts > 1 && !leadGiven && slice.count === leadCount && slice.count > 0;
      if (lead) {
        leadGiven = true;
      }
      return {
        ...slice,
        shade: this.shade(slice.color),
        d: span > 0 ? this.wedge(start, start + span) : '',
        lx: 100 + Math.cos(angle) * 36,
        ly: 78 + Math.sin(angle) * 36,
        nx: Math.cos(angle),
        ny: Math.sin(angle),
        lead
      };
    });
  }

  private shade(hex: string): string {
    const value = Number.parseInt(hex.slice(1), 16);
    const mix = (channel: number) => Math.round(channel * 0.62);
    const red = mix((value >> 16) & 255);
    const green = mix((value >> 8) & 255);
    const blue = mix(value & 255);
    return `#${[red, green, blue].map(channel => channel.toString(16).padStart(2, '0')).join('')}`;
  }

  private wedge(start: number, end: number): string {
    if (end - start >= 0.999) {
      return `${this.fan(start, start + 0.5)} ${this.fan(start + 0.5, end)}`;
    }
    return this.fan(start, end);
  }

  private fan(start: number, end: number): string {
    const cx = 100;
    const cy = 78;
    const radius = 68;
    const point = (turn: number) => {
      const angle = turn * Math.PI * 2 - Math.PI / 2;
      return [cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius];
    };
    const [x0, y0] = point(start);
    const [x1, y1] = point(end);
    const large = end - start > 0.5 ? 1 : 0;
    return `M ${cx} ${cy} L ${x0} ${y0} A ${radius} ${radius} 0 ${large} 1 ${x1} ${y1} Z`;
  }

  private ring(slices: PieSlice[]): string {
    const total = slices.reduce((sum, slice) => sum + slice.count, 0);
    if (!total) {
      return 'conic-gradient(#e7ece8 0 100%)';
    }
    let cursor = 0;
    const stops = slices.filter(slice => slice.count > 0).map(slice => {
      const start = cursor;
      cursor += (slice.count / total) * 100;
      return `${slice.color} ${start}% ${cursor}%`;
    });
    return `conic-gradient(${stops.join(', ')})`;
  }

  private pieLabel(title: string, slices: PieSlice[]): string {
    const parts = slices.map(slice => `${slice.label} ${formatNumber(slice.count)}, ${slice.share} percent`);
    return `${title}. ${parts.join('. ')}.`;
  }

  private toneFor(farmId: string, alerts: AlertItem[]): 'ok' | 'warn' | 'alert' {
    const related = alerts.filter(alert => alert.farmId === farmId);
    if (related.some(alert => alert.severity === 'CRITICAL')) {
      return 'alert';
    }
    if (related.some(alert => alert.severity === 'WARNING')) {
      return 'warn';
    }
    return 'ok';
  }

  private clip(value: string, max: number): string {
    return value.length > max ? `${value.slice(0, max - 1)}…` : value;
  }

  private axisMax(value: number): number {
    const step = Math.pow(10, Math.floor(Math.log10(value)));
    return Math.ceil(value / step) * step;
  }
}
