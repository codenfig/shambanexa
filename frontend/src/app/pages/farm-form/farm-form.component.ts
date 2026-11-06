import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { apiError } from '../../core/format';
import { MapPoint, TanzaniaMapComponent } from '../../shared/tanzania-map/tanzania-map.component';
import { BoundaryPoint, boundaryIssue, locationIssue, roundPoint } from '../../shared/tanzania-map/boundary';
import { regionContaining, TANZANIA_REGION_NAMES } from '../../shared/tanzania-map/region-lookup';
import { FarmDiagramComponent } from '../../shared/farm-diagram/farm-diagram.component';
import { Farm } from '../../core/models';

@Component({
  selector: 'app-farm-form',
  imports: [ReactiveFormsModule, RouterLink, TanzaniaMapComponent, FarmDiagramComponent],
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
  boundary = signal<BoundaryPoint[]>([]);
  mode = signal<'pin' | 'boundary'>('pin');
  locating = signal(false);
  history = signal<BoundaryPoint[][]>([]);
  private savedBoundary: BoundaryPoint[] = [];
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
        const points = (farm.boundary ?? []).map(point => roundPoint(point));
        this.savedBoundary = points.map(point => ({ ...point }));
        this.boundary.set(points);
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
    if (!this.editing) {
      this.place(point);
      return;
    }
    this.remember();
    this.boundary.update(points => [...points, roundPoint(point)]);
  }

  place(point: { latitude: number; longitude: number }): void {
    const placed = roundPoint(point);
    const region = regionContaining(placed.latitude, placed.longitude);
    this.form.patchValue({
      latitude: placed.latitude.toFixed(6),
      longitude: placed.longitude.toFixed(6),
      ...(region && !this.form.controls.region.value ? { region } : {})
    });
    this.form.controls.latitude.markAsTouched();
    this.form.controls.longitude.markAsTouched();
    const issue = locationIssue(placed.latitude, placed.longitude);
    if (issue) {
      this.error.set(issue);
    }
  }

  locate(): void {
    if (!navigator.geolocation) {
      this.error.set('This browser cannot read a GPS position. Click the map or type the coordinates.');
      return;
    }
    this.locating.set(true);
    this.error.set('');
    navigator.geolocation.getCurrentPosition(
      position => {
        this.locating.set(false);
        this.place({ latitude: position.coords.latitude, longitude: position.coords.longitude });
      },
      () => {
        this.locating.set(false);
        this.error.set('The GPS position could not be read. Click the map or type the latitude and longitude.');
      },
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 0 }
    );
  }

  cornerValue(event: Event): string {
    const target = event.target;
    return target instanceof HTMLInputElement ? target.value : '';
  }

  editPoint(index: number, field: 'latitude' | 'longitude', raw: string): void {
    const value = Number(raw);
    if (!Number.isFinite(value)) {
      return;
    }
    this.remember();
    this.boundary.update(points => points.map((point, item) => item === index ? roundPoint({ ...point, [field]: value }) : point));
  }

  movePoint(index: number, delta: number): void {
    const next = index + delta;
    const points = this.boundary();
    if (next < 0 || next >= points.length) {
      return;
    }
    this.remember();
    const copy = points.map(point => ({ ...point }));
    const [moved] = copy.splice(index, 1);
    copy.splice(next, 0, moved);
    this.boundary.set(copy);
  }

  removePoint(index: number): void {
    this.remember();
    this.boundary.update(points => points.filter((_, item) => item !== index));
  }

  undoBoundary(): void {
    const stack = this.history();
    const previous = stack[stack.length - 1];
    if (!previous) {
      return;
    }
    this.history.set(stack.slice(0, -1));
    this.boundary.set(previous.map(point => ({ ...point })));
  }

  resetBoundary(): void {
    this.boundary.set(this.savedBoundary.map(point => ({ ...point })));
    this.history.set([]);
  }

  boundaryNote(): string {
    const issue = boundaryIssue(this.boundary());
    if (issue) {
      return issue;
    }
    if (this.boundary().length === 0) {
      return 'No boundary yet. Choose Boundary, then click the map to add corners around the property.';
    }
    return `${this.boundary().length} corners. The outline is ready to save.`;
  }

  private remember(): void {
    const snapshot = this.boundary().map(point => ({ ...point }));
    this.history.update(stack => [...stack, snapshot].slice(-30));
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
    const latitude = this.coordinate('latitude');
    const longitude = this.coordinate('longitude');
    const pinIssue = latitude == null || longitude == null
      ? 'Enter the farm’s latitude and longitude.'
      : locationIssue(latitude, longitude);
    const ringIssue = this.editing ? boundaryIssue(this.boundary()) : '';
    if (pinIssue || ringIssue) {
      this.error.set(pinIssue || ringIssue);
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
      ...(this.editing ? { boundary: this.boundary() } : {})
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
