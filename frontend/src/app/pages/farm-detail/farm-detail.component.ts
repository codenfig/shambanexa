import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AlertItem, Facility, Farm } from '../../core/models';
import { healthAlert, healthLabel, healthNotice, healthPhrase } from '../../core/disease';
import { apiError, formatNumber } from '../../core/format';
import { TanzaniaMapComponent, TanzaniaPin } from '../../shared/tanzania-map/tanzania-map.component';

type DetailTab = 'overview' | 'location' | 'facilities';

@Component({
  selector: 'app-farm-detail',
  imports: [RouterLink, TanzaniaMapComponent],
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
