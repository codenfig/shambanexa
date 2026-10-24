import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { apiError } from '../../core/format';
import { MapPoint, TanzaniaMapComponent } from '../../shared/tanzania-map/tanzania-map.component';
import { regionContaining, TANZANIA_REGION_NAMES } from '../../shared/tanzania-map/region-lookup';
import { Farm } from '../../core/models';

@Component({
  selector: 'app-farm-form',
  imports: [ReactiveFormsModule, RouterLink, TanzaniaMapComponent],
  templateUrl: './farm-form.component.html',
  styleUrl: './farm-form.component.css'
})
export class FarmFormComponent {
  private fb = inject(FormBuilder);
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  farmId = this.route.snapshot.paramMap.get('id');
  editing = !!this.farmId;
  loading = signal(this.editing);
  busy = signal(false);
  error = signal('');
  status = signal('');
  establishedOn = signal<string | null>(null);
  knownFarms = signal<Farm[]>([]);
  boundary = signal<MapPoint[]>([]);
  private version: number | null = null;

  form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    name: ['', [Validators.required, Validators.maxLength(160)]],
    region: ['', [Validators.required, Validators.maxLength(80)]],
    address: ['', [Validators.required, Validators.maxLength(300)]],
    latitude: ['', [Validators.required, Validators.min(-90), Validators.max(90)]],
    longitude: ['', [Validators.required, Validators.min(-180), Validators.max(180)]],
    managerName: ['', Validators.maxLength(160)],
    contactPhone: ['', Validators.maxLength(40)]
  });

  constructor() {
    const session = this.auth.session();
    const needsSetup = !session?.allFarms && (session?.farmIds.length ?? 0) === 0;
    if (!this.editing && !session?.canManageAccess && !needsSetup) {
      this.router.navigate(['/farms']);
      return;
    }
    this.api.farms().subscribe(farms => this.knownFarms.set(farms));
    if (!this.farmId) {
      return;
    }
    this.form.controls.code.disable();
    this.api.farm(this.farmId).subscribe({
      next: detail => {
        const farm = detail.farm;
        this.version = farm.version;
        this.status.set(farm.status);
        this.establishedOn.set(farm.establishedOn);
        this.form.patchValue({
          code: farm.code,
          name: farm.name,
          region: farm.region,
          address: farm.address,
          latitude: String(farm.latitude),
          longitude: String(farm.longitude),
          managerName: farm.managerName || '',
          contactPhone: farm.contactPhone || ''
        });
        this.boundary.set(farm.boundary ?? []);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        this.error.set(apiError(err, 'This farm could not be loaded'));
      }
    });
  }

  coordinate(name: 'latitude' | 'longitude'): number | null {
    const raw = this.form.controls[name].value;
    if (raw === '') {
      return null;
    }
    const value = Number(raw);
    return Number.isFinite(value) ? value : null;
  }

  addCorner(point: MapPoint): void {
    const corner = {
      latitude: Math.round(point.latitude * 1_000_000) / 1_000_000,
      longitude: Math.round(point.longitude * 1_000_000) / 1_000_000
    };
    const points = [...this.boundary(), corner];
    this.boundary.set(points);
    const latitude = points.reduce((sum, item) => sum + item.latitude, 0) / points.length;
    const longitude = points.reduce((sum, item) => sum + item.longitude, 0) / points.length;
    const region = regionContaining(latitude, longitude);
    this.form.patchValue({
      latitude: latitude.toFixed(6),
      longitude: longitude.toFixed(6),
      ...(region ? { region } : {})
    });
    this.form.controls.latitude.markAsTouched();
    this.form.controls.longitude.markAsTouched();
    if (region) {
      this.form.controls.region.markAsTouched();
    }
  }

  undoCorner(): void {
    this.boundary.update(points => points.slice(0, -1));
  }

  clearBoundary(): void {
    this.boundary.set([]);
  }

  place(point: { latitude: number; longitude: number }): void {
    const region = regionContaining(point.latitude, point.longitude);
    this.form.patchValue({
      latitude: point.latitude.toFixed(6),
      longitude: point.longitude.toFixed(6),
      ...(region ? { region } : {})
    });
    this.form.controls.latitude.markAsTouched();
    this.form.controls.longitude.markAsTouched();
    if (region) {
      this.form.controls.region.markAsTouched();
    }
  }

  regionChoices(): string[] {
    const names = new Set<string>(TANZANIA_REGION_NAMES);
    for (const farm of this.knownFarms()) {
      if (farm.region) {
        names.add(farm.region);
      }
    }
    const current = this.form.controls.region.value.trim();
    if (current) {
      names.add(current);
    }
    return [...names].sort((left, right) => left.localeCompare(right));
  }

  regionHint(): string {
    const region = this.form.controls.region.value.trim();
    if (!region) {
      return 'More than one farm can share a region.';
    }
    const others = this.knownFarms().filter(farm => farm.region === region && farm.id !== this.farmId).length;
    if (others === 0) {
      return `${region} can hold this farm and others.`;
    }
    const count = others === 1 ? '1 farm is' : `${others} farms are`;
    return `${count} already in ${region}. This farm can share that region.`;
  }

  invalid(name: 'code' | 'name' | 'region' | 'address' | 'latitude' | 'longitude' | 'managerName' | 'contactPhone'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.busy());
  }

  submit(): void {
    this.form.markAllAsTouched();
    if ((!this.editing || this.boundary().length > 0) && this.boundary().length < 3) {
      this.error.set('Draw at least three corners of the farm boundary.');
      return;
    }
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set('');
    const raw = this.form.getRawValue();
    const body = {
      code: raw.code.trim(),
      name: raw.name.trim(),
      region: raw.region.trim(),
      address: raw.address.trim(),
      latitude: Number(raw.latitude),
      longitude: Number(raw.longitude),
      managerName: raw.managerName.trim(),
      contactPhone: raw.contactPhone.trim(),
      version: this.version,
      ...(this.boundary().length ? { boundary: this.boundary() } : {})
    };
    const request = this.farmId
      ? this.api.updateFarm(this.farmId, body)
      : this.api.createFarm(body);
    request.subscribe({
      next: farm => this.router.navigate(['/farms', farm.id]),
      error: err => {
        this.busy.set(false);
        this.error.set(apiError(err, 'Could not save the farm'));
      }
    });
  }
}
