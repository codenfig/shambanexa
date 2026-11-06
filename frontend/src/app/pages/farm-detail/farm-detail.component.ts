import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AlertItem, Facility, Farm, FarmClimate, FarmForecast } from '../../core/models';
import { healthAlert, healthLabel, healthNotice, healthPhrase } from '../../core/disease';
import { apiError, formatNumber } from '../../core/format';
import { TanzaniaMapComponent, TanzaniaPin } from '../../shared/tanzania-map/tanzania-map.component';
import { FarmDiagramComponent } from '../../shared/farm-diagram/farm-diagram.component';

type DetailTab = 'overview' | 'location' | 'facilities';

@Component({
  selector: 'app-farm-detail',
  imports: [RouterLink, TanzaniaMapComponent, FarmDiagramComponent],
  templateUrl: './farm-detail.component.html',
  styleUrl: './farm-detail.component.css'
})
export class FarmDetailComponent {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  farm = signal<Farm | null>(null);
  alerts = signal<AlertItem[]>([]);
  facilities = signal<Facility[]>([]);
  loading = signal(true);
  error = signal('');
  climate = signal<FarmClimate | null>(null);
  climateLoading = signal(false);
  forecast = signal<FarmForecast | null>(null);
  forecastLoading = signal(false);
  tab = signal<DetailTab>('overview');
  fmt = formatNumber;

  constructor() {
    this.route.queryParamMap.subscribe(params => {
      const tab = params.get('tab');
      if (tab === 'overview' || tab === 'location' || tab === 'facilities') {
        this.tab.set(tab);
      }
    });
    this.load();
  }

  load(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.loading.set(false);
      this.error.set('Farm not found');
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.climate.set(null);
    this.climateLoading.set(true);
    this.forecast.set(null);
    this.forecastLoading.set(true);
    this.api.farmForecast(id).subscribe({
      next: forecast => {
        this.forecast.set(forecast);
        this.forecastLoading.set(false);
      },
      error: () => {
        this.forecastLoading.set(false);
        this.forecast.set(null);
      }
    });
    this.api.farmClimate(id).subscribe({
      next: climate => {
        this.climate.set(climate);
        this.climateLoading.set(false);
      },
      error: () => {
        this.climateLoading.set(false);
        this.climate.set(null);
      }
    });
    this.api.alerts().subscribe({ next: alerts => this.alerts.set(alerts), error: () => this.alerts.set([]) });
    this.api.farm(id).subscribe({
      next: detail => {
        this.farm.set(detail.farm);
        this.facilities.set(detail.facilities);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        this.error.set(apiError(err, 'This farm could not be loaded'));
      }
    });
  }

  show(tab: DetailTab): void {
    this.tab.set(tab);
  }

  statusClass(status: string): string {
    if (status === 'ACTIVE') {
      return 'is-active';
    }
    if (status === 'INACTIVE') {
      return 'is-inactive';
    }
    return 'is-other';
  }

  weekday(date: string): string {
    return new Intl.DateTimeFormat('en-GB', { weekday: 'short', timeZone: 'UTC' }).format(new Date(`${date}T00:00:00Z`));
  }

  illnessLabel(sourceType: string): string {
    if (sourceType === 'SICK') {
      return 'Reported sick animals';
    }
    if (sourceType === 'CASE') {
      return 'Open health case';
    }
    return 'Reported disease';
  }

  pct(value: number | null | undefined, scale: number): number {
    if (value == null || !Number.isFinite(value) || scale <= 0) {
      return 0;
    }
    return Math.max(0, Math.min(100, (value / scale) * 100));
  }

  waterScale(report: FarmClimate): number {
    return Math.max(1, report.rainfallMm ?? 0, report.rainfallNormalMm ?? 0, report.evapotranspirationMm ?? 0, report.evapotranspirationNormalMm ?? 0);
  }

  tempScale(report: FarmClimate): number {
    const high = Math.max(report.averageMaxC ?? 0, report.averageWetBulbC ?? 0, report.averageDewPointC ?? 0, 30);
    return Math.ceil(high / 5) * 5;
  }

  lightScale(report: FarmClimate): number {
    return Math.max(30, report.averageSunlightMj ?? 0, report.averageParMj ?? 0, report.parNormalMj ?? 0);
  }

  windScale(report: FarmClimate): number {
    return Math.max(8, report.averageWindMs ?? 0);
  }

  ring(value: number | null, color: string): string {
    const share = Math.max(0, Math.min(100, value ?? 0));
    return `conic-gradient(${color} ${share}%, #e6eee8 ${share}%)`;
  }

  waterLabel(report: FarmClimate): string {
    return `Rainfall ${report.rainfallMm ?? 'unavailable'} millimetres, normal ${report.rainfallNormalMm ?? 'unavailable'} millimetres. Evapotranspiration ${report.evapotranspirationMm ?? 'unavailable'} millimetres.`;
  }

  daysLabel(report: FarmClimate): string {
    return `${report.dryDays ?? 0} dry days, ${report.hotDays ?? 0} hot days, ${report.coldNights ?? 0} cold nights, ${report.heatStressDays ?? 0} heat-stress days out of ${report.days}.`;
  }

  balanceLabel(code: string | null): string {
    if (code === 'above') {
      return 'above the long-term normal';
    }
    if (code === 'below') {
      return 'below the long-term normal';
    }
    if (code === 'near') {
      return 'near the long-term normal';
    }
    return '';
  }

  climateRange(climate: FarmClimate): string {
    if (!climate.start || !climate.end) {
      return '';
    }
    const format = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
    return `${format.format(new Date(climate.start))} – ${format.format(new Date(climate.end))}`;
  }

  dateLabel(value: string | null | undefined): string {
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

  updatedLabel(value: string | null | undefined): string {
    if (!value) {
      return '—';
    }
    return new Intl.DateTimeFormat('en-GB', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      timeZone: 'Africa/Dar_es_Salaam'
    }).format(new Date(value));
  }

  boundaryRing = computed(() => {
    const farm = this.farm();
    const points = farm?.boundary ?? [];
    return farm && points.length >= 3 ? [{ id: farm.id, points }] : [];
  });

  locationPin = computed<TanzaniaPin[]>(() => {
    const farm = this.farm();
    if (!farm) {
      return [];
    }
    const health = healthAlert(this.alerts(), farm.id);
    const open = this.alerts().filter(alert => alert.farmId === farm.id && alert.status === 'OPEN');
    const tone = open.some(alert => alert.severity === 'CRITICAL') ? 'alert'
      : open.some(alert => alert.severity === 'WARNING') ? 'warn' : 'ok';
    return [{
      id: farm.id,
      label: farm.code,
      title: health
        ? `${farm.name}. ${healthPhrase(health)}: ${healthLabel(health)}.`
        : `${farm.name} · ${Number(farm.latitude).toFixed(6)}, ${Number(farm.longitude).toFixed(6)}`,
      latitude: Number(farm.latitude),
      longitude: Number(farm.longitude),
      tone,
      notice: health ? healthNotice(health) : undefined
    }];
  });
}
