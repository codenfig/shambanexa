import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { AlertItem, Facility, Farm } from '../../core/models';
import { healthAlert, healthNotice, healthPhrase, healthLabel } from '../../core/disease';
import { apiError, formatNumber } from '../../core/format';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { TanzaniaMapComponent, TanzaniaPin } from '../../shared/tanzania-map/tanzania-map.component';

type FarmSort = 'name' | 'region' | 'animals' | 'facilities' | 'status';

interface FacilityRow extends Facility {
  farmName: string;
}

@Component({
  selector: 'app-farms',
  imports: [RouterLink, FormsModule, TanzaniaMapComponent, PageHeaderComponent],
  templateUrl: './farms.component.html',
  styleUrl: './farms.component.css'
})
export class FarmsComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);

  farms = signal<Farm[]>([]);
  alerts = signal<AlertItem[]>([]);
  loading = signal(true);
  error = signal('');
  query = signal('');
  region = signal('');
  status = signal('');
  sort = signal<FarmSort>('name');
  view = signal('');
  facilityRows = signal<FacilityRow[]>([]);
  facilitiesLoading = signal(false);
  facilitiesError = signal('');
  private facilitiesLoaded = false;
  page = signal(0);
  readonly pageSize = 8;
  fmt = formatNumber;

  canCreate = computed(() => this.auth.session()?.allFarms === true);
  facilityView = computed(() => this.view() === 'facilities' || this.view() === 'types' || this.view() === 'maintenance');
  pageTitle = computed(() => {
    if (this.view() === 'types') return 'Facility types';
    if (this.view() === 'maintenance') return 'Maintenance';
    if (this.view() === 'facilities') return 'Facilities';
    return 'Farms';
  });
  pageDescription = computed(() => this.facilityView()
    ? 'Barns, ponds, stores, and milking units recorded on farms you can access.'
    : 'Manage your farms across locations.');

  regions = computed(() => [...new Set(this.farms().map(farm => farm.region))].sort((a, b) => a.localeCompare(b)));
  statuses = computed(() => [...new Set(this.farms().map(farm => farm.status))].sort((a, b) => a.localeCompare(b)));

  summary = computed(() => {
    const rows = this.farms();
    const active = rows.filter(farm => farm.status === 'ACTIVE').length;
    return {
      total: rows.length,
      active,
      inactive: rows.length - active,
      regions: new Set(rows.map(farm => farm.region)).size,
      animals: rows.reduce((sum, farm) => sum + Number(farm.animalCount || 0), 0)
    };
  });

  filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const region = this.region();
    const status = this.status();
    const rows = this.farms().filter(farm => {
      const haystack = [farm.name, farm.code, farm.address, farm.managerName || '', farm.region]
        .join(' ')
        .toLowerCase();
      if (q && !haystack.includes(q)) {
        return false;
      }
      if (region && farm.region !== region) {
        return false;
      }
      if (status && farm.status !== status) {
        return false;
      }
      return true;
    });
    const sort = this.sort();
    return [...rows].sort((a, b) => {
      if (sort === 'region') {
        return a.region.localeCompare(b.region) || a.name.localeCompare(b.name);
      }
      if (sort === 'animals') {
        return b.animalCount - a.animalCount || a.name.localeCompare(b.name);
      }
      if (sort === 'facilities') {
        return b.facilityCount - a.facilityCount || a.name.localeCompare(b.name);
      }
      if (sort === 'status') {
        return a.status.localeCompare(b.status) || a.name.localeCompare(b.name);
      }
      return a.name.localeCompare(b.name);
    });
  });

  pageCount = computed(() => Math.max(1, Math.ceil(this.filtered().length / this.pageSize)));

  visiblePage = computed(() => Math.min(this.page(), this.pageCount() - 1));

  paged = computed(() => {
    const start = this.visiblePage() * this.pageSize;
    return this.filtered().slice(start, start + this.pageSize);
  });

  rangeLabel = computed(() => {
    const total = this.filtered().length;
    if (total === 0) {
      return 'Showing 0 farms';
    }
    const start = this.visiblePage() * this.pageSize + 1;
    const end = Math.min(total, start + this.pageSize - 1);
    return `Showing ${start}–${end} of ${formatNumber(total)}`;
  });

  pages = computed(() => Array.from({ length: this.pageCount() }, (_, index) => index));

  facilityTypes = computed(() => {
    const groups = new Map<string, number>();
    for (const row of this.facilityRows()) {
      groups.set(row.facilityType, (groups.get(row.facilityType) ?? 0) + 1);
    }
    return [...groups.entries()]
      .map(([type, count]) => ({ type, count }))
      .sort((a, b) => a.type.localeCompare(b.type));
  });

  maintenanceRows = computed(() => this.facilityRows().filter(row => row.status !== 'ACTIVE'));

  constructor() {
    this.route.queryParamMap.subscribe(params => {
      const view = params.get('view') || '';
      this.view.set(view);
      if (view === 'facilities' || view === 'types' || view === 'maintenance') {
        this.sort.set('facilities');
        this.loadFacilities();
      }
    });
    this.load();
  }

  loadFacilities(): void {
    if (this.facilitiesLoaded || this.facilitiesLoading()) {
      return;
    }
    this.facilitiesLoading.set(true);
    this.facilitiesError.set('');
    this.api.farms().subscribe({
      next: farms => {
        if (!farms.length) {
          this.facilityRows.set([]);
          this.facilitiesLoading.set(false);
          this.facilitiesLoaded = true;
          return;
        }
        forkJoin(farms.map(farm => this.api.farm(farm.id).pipe(
          map(detail => detail.facilities.map(item => ({ ...item, farmName: farm.name }))),
          catchError(() => of([] as FacilityRow[]))
        ))).subscribe(groups => {
          this.facilityRows.set(groups.flat());
          this.facilitiesLoading.set(false);
          this.facilitiesLoaded = true;
        });
      },
      error: () => {
        this.facilitiesLoading.set(false);
        this.facilitiesError.set('Facilities could not be loaded.');
      }
    });
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    forkJoin({
      farms: this.api.farms(),
      alerts: this.api.alerts().pipe(catchError(() => of([] as AlertItem[])))
    }).subscribe({
      next: ({ farms, alerts }) => {
        this.farms.set(farms);
        this.alerts.set(alerts);
        this.loading.set(false);
      },
      error: err => {
        this.farms.set([]);
        this.loading.set(false);
        this.error.set(apiError(err, 'Farms could not be loaded'));
      }
    });
  }

  setQuery(value: string): void {
    this.query.set(value);
    this.page.set(0);
  }

  setRegion(value: string): void {
    this.region.set(value);
    this.page.set(0);
  }

  setStatus(value: string): void {
    this.status.set(value);
    this.page.set(0);
  }

  setSort(value: string): void {
    this.sort.set(value as FarmSort);
    this.page.set(0);
  }

  reset(): void {
    this.query.set('');
    this.region.set('');
    this.status.set('');
    this.sort.set('name');
    this.page.set(0);
  }

  go(page: number): void {
    this.page.set(Math.min(Math.max(page, 0), this.pageCount() - 1));
  }

  exportCsv(): void {
    const header = ['Code', 'Name', 'Region', 'Status', 'Address', 'Manager', 'Phone', 'Animals', 'Facilities', 'Latitude', 'Longitude', 'Established', 'Updated'];
    const lines = this.filtered().map(farm => [
      farm.code,
      farm.name,
      farm.region,
      farm.status,
      farm.address,
      farm.managerName || '',
      farm.contactPhone || '',
      String(farm.animalCount),
      String(farm.facilityCount),
      String(farm.latitude),
      String(farm.longitude),
      farm.establishedOn || '',
      farm.updatedAt || ''
    ]);
    const csv = [header, ...lines]
      .map(columns => columns.map(value => `"${String(value).replaceAll('"', '""')}"`).join(','))
      .join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'shambanexa-farms.csv';
    link.click();
    URL.revokeObjectURL(url);
  }

  boundaries = computed(() => this.filtered()
    .filter(farm => (farm.boundary?.length ?? 0) >= 3)
    .map(farm => ({ id: farm.id, points: farm.boundary })));

  mapPins = computed<TanzaniaPin[]>(() => this.filtered()
    .filter(farm => Number.isFinite(Number(farm.latitude)) && Number.isFinite(Number(farm.longitude)))
    .map(farm => {
      const health = healthAlert(this.alerts(), farm.id);
      const warning = this.alerts().some(alert => alert.farmId === farm.id && alert.status === 'OPEN' && alert.severity === 'WARNING');
      const critical = this.alerts().some(alert => alert.farmId === farm.id && alert.status === 'OPEN' && alert.severity === 'CRITICAL');
      return {
        id: farm.id,
        label: farm.code,
        title: health
          ? `${farm.name}. ${healthPhrase(health)}: ${healthLabel(health)}.`
          : `${farm.name} · ${farm.region} · ${Number(farm.latitude).toFixed(4)}, ${Number(farm.longitude).toFixed(4)}`,
        latitude: Number(farm.latitude),
        longitude: Number(farm.longitude),
        link: ['/farms', farm.id],
        tone: critical || health?.severity === 'CRITICAL' ? 'alert' : warning || health ? 'warn' : 'ok',
        notice: health ? healthNotice(health) : undefined
      };
    }));

  statusClass(status: string): string {
    if (status === 'ACTIVE') {
      return 'is-active';
    }
    if (status === 'INACTIVE') {
      return 'is-inactive';
    }
    return 'is-other';
  }

  updatedLabel(farm: Farm): string {
    if (!farm.updatedAt) {
      return '—';
    }
    return new Intl.DateTimeFormat('en-GB', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      timeZone: 'Africa/Dar_es_Salaam'
    }).format(new Date(farm.updatedAt));
  }
}
