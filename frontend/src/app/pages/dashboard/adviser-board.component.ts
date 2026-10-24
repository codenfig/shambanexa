import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { AdviceNote, AlertItem, Dashboard, Farm, TrendPoint, WorkTask } from '../../core/models';
import { formatMonth, formatNumber, relativeTime } from '../../core/format';
import { MapCallout, TanzaniaMapComponent, TanzaniaPin } from '../../shared/tanzania-map/tanzania-map.component';
import { healthAlert, healthLabel, healthNotice, healthPhrase } from '../../core/disease';

const ZONE = 'Africa/Dar_es_Salaam';

interface MilkPoint {
  x: number;
  y: number;
  label: string;
  milk: number;
}

@Component({
  selector: 'app-adviser-board',
  imports: [RouterLink, TanzaniaMapComponent],
  templateUrl: './adviser-board.component.html',
  styleUrl: './adviser-board.component.css'
})
export class AdviserBoardComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);

  data = signal<Dashboard | null>(null);
  farms = signal<Farm[]>([]);
  alerts = signal<AlertItem[] | null>(null);
  tasks = signal<WorkTask[] | null>(null);
  notes = signal<AdviceNote[] | null>(null);
  loading = signal(true);
  error = signal('');
  zoom = signal(1);
  selectedId = signal<string | null>(null);
  hoveredId = signal<string | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
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
    this.api.farms().subscribe({ next: rows => this.farms.set(rows), error: () => this.farms.set([]) });
    this.api.alerts().subscribe({ next: rows => this.alerts.set(rows), error: () => this.alerts.set([]) });
    this.api.tasks().subscribe({
      next: rows => this.tasks.set(rows.filter(task => task.status === 'OPEN').sort((a, b) => a.dueOn.localeCompare(b.dueOn))),
      error: () => this.tasks.set([])
    });
    this.api.advice().subscribe({ next: rows => this.notes.set(rows), error: () => this.notes.set([]) });
  }

  fmt = formatNumber;
  when = relativeTime;

  firstName(): string {
    const name = this.auth.session()?.fullName?.trim() || 'there';
    return name.split(/\s+/)[0];
  }

  greeting(): string {
    const hour = Number(new Intl.DateTimeFormat('en-GB', { hour: 'numeric', hourCycle: 'h23', timeZone: ZONE }).format(new Date()));
    if (hour < 12) {
      return 'Good morning';
    }
    if (hour < 17) {
      return 'Good afternoon';
    }
    return 'Good evening';
  }

  scopeLabel(): string {
    const count = this.farms().length;
    return count === 1 ? '1 Assigned Farm' : `${formatNumber(count)} Assigned Farms`;
  }

  openAlerts = computed(() => (this.alerts() ?? []).filter(alert => alert.status === 'OPEN'));

  urgentAlerts = computed(() => this.openAlerts().filter(alert =>
    alert.severity === 'CRITICAL' || alert.sourceType === 'DEATH' || alert.sourceType === 'DISEASE' || alert.sourceType === 'SICK'
  ));

  cases = computed(() => [...this.openAlerts()].sort((a, b) => this.rank(a) - this.rank(b) || b.occurredAt.localeCompare(a.occurredAt)).slice(0, 5));

  upcoming = computed(() => (this.tasks() ?? []).slice(0, 4));

  recommendations = computed(() => (this.notes() ?? []).slice(0, 3));

  activity = computed(() => (this.data()?.activity ?? []).slice(0, 4));

  legend = computed(() => {
    const pins = this.data()?.locations ?? [];
    const alerts = this.openAlerts();
    let active = 0;
    let attention = 0;
    let critical = 0;
    for (const pin of pins) {
      const tone = this.toneFor(pin.id, alerts);
      if (tone === 'alert') {
        critical += 1;
      } else if (tone === 'warn') {
        attention += 1;
      } else if (pin.status === 'ACTIVE') {
        active += 1;
      }
    }
    return [
      { tone: 'ok', label: 'Active', count: active },
      { tone: 'warn', label: 'Needs attention', count: attention },
      { tone: 'alert', label: 'Critical', count: critical }
    ];
  });

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
        notice: alert ? healthNotice(alert) : undefined,
        link: ['/farms', pin.id]
      };
    });
  });

  callout = computed<MapCallout | null>(() => {
    const id = this.hoveredId() || this.selectedId();
    const pin = this.data()?.locations.find(item => item.id === id);
    if (!pin) {
      return null;
    }
    const alert = healthAlert(this.alerts() ?? [], pin.id);
    const lines = [this.clip(pin.name, 22), this.clip(pin.region, 22)];
    if (alert) {
      lines.push(this.clip(healthLabel(alert), 28));
    }
    lines.push(`${formatNumber(pin.animalCount)} animals`);
    return { id: pin.id, lines };
  });

  chart = computed(() => {
    const trends = this.data()?.trends ?? [];
    if (trends.length < 2) {
      return null;
    }
    const width = 360;
    const height = 150;
    const left = 36;
    const right = 8;
    const top = 10;
    const bottom = 22;
    const innerW = width - left - right;
    const innerH = height - top - bottom;
    const milk = trends.map(point => Number(point.milkLitres));
    const max = Math.max(...milk, 1);
    const points: MilkPoint[] = trends.map((point, index) => ({
      x: left + (index * innerW) / (trends.length - 1),
      y: top + (1 - Number(point.milkLitres) / max) * innerH,
      label: formatMonth(point.month),
      milk: Number(point.milkLitres)
    }));
    const line = points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ');
    const area = `${line} L ${points[points.length - 1].x} ${top + innerH} L ${points[0].x} ${top + innerH} Z`;
    return { width, height, line, area, points, max, range: this.range(trends) };
  });

  focusPin(id: string): void {
    this.hoveredId.set(id || null);
  }

  pickPin(id: string): void {
    this.selectedId.set(id);
    this.hoveredId.set(id);
  }

  zoomIn(): void {
    this.zoom.set(Math.min(2.2, Number((this.zoom() + 0.15).toFixed(2))));
  }

  zoomOut(): void {
    this.zoom.set(Math.max(0.85, Number((this.zoom() - 0.15).toFixed(2))));
  }

  review(alert: AlertItem): string[] {
    return alert.farmId ? ['/farms', alert.farmId] : ['/alerts'];
  }

  activityLink(item: { eventType: string; farmId: string | null }): string[] {
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
    return ['/dashboard'];
  }

  lead(farmId: string | null): string {
    const farm = this.farms().find(item => item.id === farmId);
    return farm?.managerName || farm?.region || '';
  }

  age(iso: string): string {
    const days = Math.floor((Date.now() - new Date(iso).getTime()) / 864e5);
    if (days >= 1) {
      return days === 1 ? '1 day' : `${days} days`;
    }
    return relativeTime(iso);
  }

  due(iso: string): string {
    const today = new Intl.DateTimeFormat('en-CA', { timeZone: ZONE, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
    if (iso === today) {
      return 'Due today';
    }
    return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' }).format(new Date(`${iso}T00:00:00Z`));
  }

  severity(value: string): string {
    if (value === 'CRITICAL') {
      return 'Critical';
    }
    if (value === 'WARNING') {
      return 'Warning';
    }
    if (value === 'INFO') {
      return 'Info';
    }
    return value.charAt(0) + value.slice(1).toLowerCase();
  }

  status(value: string): string {
    if (value === 'OPEN') {
      return 'Open';
    }
    return value.charAt(0) + value.slice(1).toLowerCase();
  }

  private range(trends: TrendPoint[]): string {
    const first = formatMonth(trends[0].month);
    const last = formatMonth(trends[trends.length - 1].month);
    return first === last ? first : `${first} – ${last}`;
  }

  private rank(alert: AlertItem): number {
    if (alert.severity === 'CRITICAL' || alert.sourceType === 'DEATH') {
      return 0;
    }
    if (alert.severity === 'WARNING' || alert.sourceType === 'DISEASE' || alert.sourceType === 'SICK') {
      return 1;
    }
    return 2;
  }

  private toneFor(farmId: string, alerts: AlertItem[]): 'ok' | 'warn' | 'alert' {
    const related = alerts.filter(alert => alert.farmId === farmId && alert.status === 'OPEN');
    if (related.some(alert => alert.severity === 'CRITICAL' || alert.sourceType === 'DEATH')) {
      return 'alert';
    }
    if (related.some(alert => alert.severity === 'WARNING' || alert.sourceType === 'DISEASE' || alert.sourceType === 'SICK')) {
      return 'warn';
    }
    return 'ok';
  }

  private clip(value: string, max: number): string {
    return value.length > max ? `${value.slice(0, max - 1)}…` : value;
  }
}
