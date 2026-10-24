import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ActivityBoardComponent } from './activity-board.component';
import { AdviserBoardComponent } from './adviser-board.component';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ActivityItem, AlertItem, Dashboard, Farm, FishCycle, HealthCase, InventoryItem, TrendPoint, WorkTask } from '../../core/models';
import { formatMoney, formatMonth, formatNumber, relativeTime, signedPercent } from '../../core/format';
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

  barWidth(count: number, species: { count: number }[]): string {
    const max = Math.max(...species.map(item => Number(item.count)), 1);
    return `${(Number(count) / max) * 100}%`;
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
