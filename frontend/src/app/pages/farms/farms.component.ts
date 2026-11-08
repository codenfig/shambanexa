import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
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
  farmId: string;
}

type FacilityLayout = 'grid' | 'list';

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
  private router = inject(Router);

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
  facilityQuery = signal('');
  facilityFarm = signal('');
  facilityType = signal('');
  facilityStatus = signal('');
  facilityLayout = signal<FacilityLayout>('grid');
  facilityPage = signal(0);
  page = signal(0);
  readonly pageSize = 8;
  readonly facilityPageSize = 8;
  readonly maintenancePageSize = 6;
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

  facilityStats = computed(() => {
    const rows = this.facilityRows();
    const operational = rows.filter(row => row.status === 'ACTIVE').length;
    return {
      total: rows.length,
      operational,
      attention: rows.length - operational,
      farms: new Set(rows.map(row => row.farmName)).size
    };
  });

  facilityFarmOptions = computed(() => [...new Set(this.facilityRows().map(row => row.farmName))].sort((a, b) => a.localeCompare(b)));

  facilityStatusOptions = computed(() => [...new Set(this.facilityRows().map(row => row.status))].sort((a, b) => a.localeCompare(b)));

  filteredFacilities = computed(() => {
    const query = this.facilityQuery().trim().toLowerCase();
    const farm = this.facilityFarm();
    const type = this.facilityType();
    const status = this.facilityStatus();
    return this.facilityRows().filter(row => {
      if (farm && row.farmName !== farm) {
        return false;
      }
      if (type && row.facilityType !== type) {
        return false;
      }
      if (status && row.status !== status) {
        return false;
      }
      if (!query) {
        return true;
      }
      const haystack = [row.name, row.farmName, row.code, row.facilityType, this.typeLabel(row.facilityType), this.statusLabel(row.status)]
        .join(' ')
        .toLowerCase();
      return haystack.includes(query);
    }).sort((a, b) => a.name.localeCompare(b.name) || a.farmName.localeCompare(b.farmName));
  });

  // Maintenance is a facility-health overview, not only a list of flagged facilities.
  // Keep every accessible facility visible, including when all statuses are ACTIVE.
  visibleFacilities = computed(() => this.filteredFacilities());

  facilityPageLimit = computed(() => this.view() === 'maintenance' ? this.maintenancePageSize : this.facilityPageSize);

  facilityPageCount = computed(() => Math.max(1, Math.ceil(this.visibleFacilities().length / this.facilityPageLimit())));

  visibleFacilityPage = computed(() => Math.min(this.facilityPage(), this.facilityPageCount() - 1));

  pagedFacilities = computed(() => {
    const start = this.visibleFacilityPage() * this.facilityPageLimit();
    return this.visibleFacilities().slice(start, start + this.facilityPageLimit());
  });

  facilityPages = computed(() => Array.from({ length: this.facilityPageCount() }, (_, index) => index));

  facilityRange = computed(() => {
    const total = this.visibleFacilities().length;
    if (total === 0) {
      return 'Showing 0 facilities';
    }
    const start = this.visibleFacilityPage() * this.facilityPageLimit() + 1;
    const end = Math.min(total, start + this.facilityPageLimit() - 1);
    return `Showing ${start}–${end} of ${formatNumber(total)} facilities`;
  });

  visibleTypes = computed(() => {
    const farm = this.facilityFarm();
    const status = this.facilityStatus();
    const query = this.facilityQuery().trim().toLowerCase();
    const groups = new Map<string, number>();
    for (const row of this.facilityRows()) {
      if (farm && row.farmName !== farm) {
        continue;
      }
      if (status && row.status !== status) {
        continue;
      }
      const label = this.typeLabel(row.facilityType).toLowerCase();
      if (query && !label.includes(query) && !row.facilityType.toLowerCase().includes(query)) {
        continue;
      }
      groups.set(row.facilityType, (groups.get(row.facilityType) ?? 0) + 1);
    }
    return [...groups.entries()]
      .map(([type, count]) => ({ type, count }))
      .sort((a, b) => this.typeLabel(a.type).localeCompare(this.typeLabel(b.type)));
  });

  constructor() {
    this.route.queryParamMap.subscribe(params => {
      const view = params.get('view') || '';
      if (view !== this.view()) {
        this.facilityPage.set(0);
      }
      this.view.set(view);
      if (!view) this.loadFacilities();
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
          map(detail => detail.facilities.map(item => ({ ...item, farmName: farm.name, farmId: farm.id }))),
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

  setFacilityQuery(value: string): void {
    this.facilityQuery.set(value);
    this.facilityPage.set(0);
  }

  setFacilityFarm(value: string): void {
    this.facilityFarm.set(value);
    this.facilityPage.set(0);
  }

  setFacilityType(value: string): void {
    this.facilityType.set(value);
    this.facilityPage.set(0);
  }

  setFacilityStatus(value: string): void {
    this.facilityStatus.set(value);
    this.facilityPage.set(0);
  }

  setFacilityLayout(value: FacilityLayout): void {
    this.facilityLayout.set(value);
  }

  resetFacilities(): void {
    this.facilityQuery.set('');
    this.facilityFarm.set('');
    this.facilityType.set('');
    this.facilityStatus.set('');
    this.facilityPage.set(0);
  }

  goFacility(page: number): void {
    this.facilityPage.set(Math.min(Math.max(page, 0), this.facilityPageCount() - 1));
  }

  openType(type: string): void {
    this.facilityType.set(type);
    this.facilityQuery.set('');
    this.facilityPage.set(0);
    void this.router.navigate(['/farms'], { queryParams: { view: 'facilities' } });
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

  exportFacilities(): void {
    const rows = this.view() === 'types'
      ? [['Type', 'Facilities'], ...this.visibleTypes().map(item => [this.typeLabel(item.type), String(item.count)])]
      : [
          ['Name', 'Farm', 'Type', 'Status', 'Capacity', 'Unit', 'Added', 'Supervisor'],
          ...this.visibleFacilities().map(item => [
            item.name,
            item.farmName,
            this.typeLabel(item.facilityType),
            this.statusLabel(item.status),
            item.capacity === null ? '' : String(item.capacity),
            item.capacityUnit || '',
            this.addedLabel(item.createdAt),
            item.supervisorName || ''
          ])
        ];
    if (rows.length < 2) {
      return;
    }
    const csv = rows
      .map(columns => columns.map(value => `"${String(value).replaceAll('"', '""')}"`).join(','))
      .join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'shambanexa-facilities.csv';
    link.click();
    URL.revokeObjectURL(url);
  }

  facilitiesExportable(): boolean {
    if (this.facilitiesLoading() || this.facilitiesError()) {
      return false;
    }
    return this.view() === 'types' ? this.visibleTypes().length > 0 : this.visibleFacilities().length > 0;
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
      const farmFacility = this.facilityRows().find(row => row.farmId === farm.id);
      return {
        id: farm.id,
        label: farm.code,
        title: health
          ? `${farm.name}. ${healthPhrase(health)}: ${healthLabel(health)}.`
          : `${farm.name} · ${farm.region} · ${Number(farm.latitude).toFixed(4)}, ${Number(farm.longitude).toFixed(4)}`,
        latitude: Number(farm.latitude),
        longitude: Number(farm.longitude),
        link: ['/farms', farm.id],
        imageUrl: farmFacility ? this.facilityPhoto(farmFacility) : '/assets/facilities/landscape.png',
        imageCaption: farmFacility ? `${farmFacility.name} · ${this.typeLabel(farmFacility.facilityType)}` : `${farm.name} · Farm overview`,
        tone: critical || health?.severity === 'CRITICAL' ? 'alert' : warning || health ? 'warn' : 'ok',
        notice: health ? healthNotice(health) : undefined
      };
    }));

  typeLabel(type: string): string {
    const names: Record<string, string> = {
      BARN: 'Barn',
      GRAZING: 'Grazing',
      PEN: 'Pen',
      WAREHOUSE: 'Warehouse',
      CAGE: 'Cage',
      POND: 'Pond',
      MILKING: 'Milking unit'
    };
    return names[type] || type.charAt(0) + type.slice(1).toLowerCase();
  }

  typeDescription(type: string): string {
    const descriptions: Record<string, string> = {
      BARN: 'Sheltered spaces for livestock housing, care and daily farm operations.',
      CAGE: 'Secure, managed enclosures for smaller animals and poultry.',
      GRAZING: 'Open pasture areas where animals can roam, feed and thrive.',
      PEN: 'Dedicated enclosures for safe livestock handling and management.',
      WAREHOUSE: 'Protected storage for harvests, feed, equipment and supplies.',
      POND: 'Managed water facilities for aquaculture and farm operations.',
      MILKING: 'Purpose-built spaces for hygienic and efficient milk collection.'
    };
    return descriptions[type] || 'Facilities supporting the day-to-day needs of your farm.';
  }

  facilityPhoto(row: FacilityRow): string {
    const kind = this.photoKind(row.facilityType);
    if (!kind) {
      return '/assets/facilities/landscape.png';
    }
    const farm = row.farmName.trim().toLowerCase();
    if (farm === 'central farm') {
      return `/assets/facilities/${kind}-a.png`;
    }
    if (farm === 'coastal farm') {
      return `/assets/facilities/${kind}-b.png`;
    }
    const alternate = [...row.farmId].reduce((sum, ch) => sum + ch.charCodeAt(0), 0) % 2 === 0;
    return `/assets/facilities/${kind}-${alternate ? 'a' : 'b'}.png`;
  }

  typePhoto(type: string): string {
    const kind = this.photoKind(type);
    return kind ? `/assets/facilities/${kind}-a.png` : '/assets/facilities/landscape.png';
  }

  private photoKind(type: string): string | null {
    if (type === 'BARN' || type === 'MILKING') {
      return 'barn';
    }
    if (type === 'GRAZING') {
      return 'grazing';
    }
    if (type === 'PEN') {
      return 'pen';
    }
    if (type === 'WAREHOUSE') {
      return 'store';
    }
    return null;
  }

  statusLabel(status: string): string {
    if (status === 'ACTIVE') {
      return 'Operational';
    }
    if (status === 'MAINTENANCE' || status === 'NEEDS_ATTENTION') {
      return 'Needs attention';
    }
    if (!status) {
      return '—';
    }
    return status.charAt(0) + status.slice(1).toLowerCase().replaceAll('_', ' ');
  }

  capacityLabel(row: FacilityRow): string {
    if (row.capacity === null || row.capacity === undefined) {
      return '—';
    }
    const amount = formatNumber(Number(row.capacity));
    return row.capacityUnit ? `${amount} ${row.capacityUnit}` : amount;
  }

  addedLabel(value: string | null): string {
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
