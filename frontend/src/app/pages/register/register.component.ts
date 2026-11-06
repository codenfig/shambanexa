import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { apiError } from '../../core/format';
import { TanzaniaMapComponent } from '../../shared/tanzania-map/tanzania-map.component';
import { locationIssue, roundCoord } from '../../shared/tanzania-map/boundary';
import { regionContaining, TANZANIA_REGION_NAMES } from '../../shared/tanzania-map/region-lookup';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, TanzaniaMapComponent],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css'
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  regions = TANZANIA_REGION_NAMES;
  busy = signal(false);
  locating = signal(false);
  error = signal('');
  notice = signal('');
  showPassword = signal(false);
  agreed = signal(false);
  accuracy = signal<number | null>(null);
  query = signal('');
  searchOpen = signal(false);
  zoom = signal(1);

  form = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(160)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(180)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(80)]],
    farmName: ['', [Validators.required, Validators.maxLength(160)]],
    farmCode: ['', [Validators.required, Validators.maxLength(40)]],
    region: ['', Validators.required],
    address: ['', [Validators.required, Validators.maxLength(300)]],
    contactPhone: ['', Validators.maxLength(40)],
    latitude: ['', [Validators.required, Validators.min(-90), Validators.max(90)]],
    longitude: ['', [Validators.required, Validators.min(-180), Validators.max(180)]]
  });

  coordinate(name: 'latitude' | 'longitude'): number | null {
    const raw = this.form.controls[name].value;
    if (raw === '') {
      return null;
    }
    const value = Number(raw);
    return Number.isFinite(value) ? value : null;
  }

  accountReady(): boolean {
    const controls = this.form.controls;
    return controls.fullName.valid && controls.email.valid && controls.password.valid;
  }

  farmReady(): boolean {
    const controls = this.form.controls;
    return controls.farmName.valid && controls.farmCode.valid && controls.region.valid && controls.address.valid;
  }

  locationReady(): boolean {
    const latitude = this.coordinate('latitude');
    const longitude = this.coordinate('longitude');
    return latitude !== null && longitude !== null && !locationIssue(latitude, longitude);
  }

  currentStep(): 1 | 2 | 3 {
    if (!this.accountReady()) {
      return 1;
    }
    if (!this.farmReady()) {
      return 2;
    }
    return 3;
  }

  matches(): Place[] {
    const query = this.query().trim().toLowerCase();
    if (query.length < 2) {
      return [];
    }
    return PLACES.filter(place => place.name.toLowerCase().includes(query)).slice(0, 6);
  }

  search(value: string): void {
    this.query.set(value);
    this.searchOpen.set(true);
  }

  searchFrom(event: Event): void {
    this.search((event.target as HTMLInputElement).value);
  }

  setAgreed(event: Event): void {
    this.agreed.set((event.target as HTMLInputElement).checked);
  }

  closeSearch(): void {
    setTimeout(() => this.searchOpen.set(false), 160);
  }

  choose(place: Place): void {
    this.query.set(place.name);
    this.searchOpen.set(false);
    this.place(place, place.region);
  }

  zoomBy(step: number): void {
    const next = Math.round((this.zoom() + step) * 100) / 100;
    this.zoom.set(Math.min(2.2, Math.max(1, next)));
  }

  place(point: { latitude: number; longitude: number }, regionName?: string): void {
    const latitude = roundCoord(point.latitude);
    const longitude = roundCoord(point.longitude);
    const detected = regionContaining(latitude, longitude);
    const named = regionName && this.regions.includes(regionName) ? regionName : '';
    const region = detected || named;
    this.accuracy.set(null);
    this.form.patchValue({
      latitude: latitude.toFixed(6),
      longitude: longitude.toFixed(6),
      ...(region && !this.form.controls.region.value ? { region } : {})
    });
    this.form.controls.latitude.markAsTouched();
    this.form.controls.longitude.markAsTouched();
    this.error.set(locationIssue(latitude, longitude));
  }

  clearLocation(): void {
    this.accuracy.set(null);
    this.query.set('');
    this.searchOpen.set(false);
    this.zoom.set(1);
    this.form.controls.latitude.reset('');
    this.form.controls.longitude.reset('');
    this.error.set('');
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
        const metres = Math.round(position.coords.accuracy);
        this.accuracy.set(Number.isFinite(metres) ? metres : null);
      },
      () => {
        this.locating.set(false);
        this.error.set('The GPS position could not be read. Click the map or type the latitude and longitude.');
      },
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 0 }
    );
  }

  invalid(name: 'fullName' | 'email' | 'password' | 'farmName' | 'farmCode' | 'region' | 'address' | 'latitude' | 'longitude'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.busy());
  }

  explainPrivacy(): void {
    this.notice.set('Registration creates your tenant account and this farm. The session stays on this browser until you sign out.');
  }

  explainTerms(): void {
    this.notice.set('A new account starts with Own Farm Only access. You see this farm until an administrator changes the grant.');
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (!this.agreed()) {
      this.error.set('Agree to the access terms and privacy note before creating the account.');
      return;
    }
    const latitude = this.coordinate('latitude');
    const longitude = this.coordinate('longitude');
    const issue = latitude == null || longitude == null ? 'Enter the farm’s latitude and longitude.' : locationIssue(latitude, longitude);
    if (issue) {
      this.error.set(issue);
    }
    if (this.form.invalid || issue || this.busy()) {
      return;
    }
    const raw = this.form.getRawValue();
    this.busy.set(true);
    this.error.set('');
    this.auth.register({
      fullName: raw.fullName.trim(),
      email: raw.email.trim(),
      password: raw.password,
      region: raw.region,
      address: raw.address.trim(),
      latitude: Number(raw.latitude),
      longitude: Number(raw.longitude),
      farmName: raw.farmName.trim(),
      farmCode: raw.farmCode.trim(),
      contactPhone: raw.contactPhone.trim()
    }).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: err => {
        this.busy.set(false);
        this.error.set(apiError(err, 'The account could not be created'));
      }
    });
  }
}

interface Place {
  name: string;
  kind: string;
  latitude: number;
  longitude: number;
  region?: string;
}

const PLACES: Place[] = [
  { name: 'Arusha', kind: 'Town', latitude: -3.3869, longitude: 36.683, region: 'Arusha' },
  { name: 'Babati', kind: 'Town', latitude: -4.2117, longitude: 35.7477, region: 'Manyara' },
  { name: 'Bukoba', kind: 'Town', latitude: -1.3317, longitude: 31.8122, region: 'Kagera' },
  { name: 'Dar es Salaam', kind: 'City', latitude: -6.7924, longitude: 39.2083, region: 'Dar es Salaam' },
  { name: 'Dodoma', kind: 'City', latitude: -6.163, longitude: 35.7516, region: 'Dodoma' },
  { name: 'Geita', kind: 'Town', latitude: -2.8724, longitude: 32.232, region: 'Geita' },
  { name: 'Iringa', kind: 'Town', latitude: -7.77, longitude: 35.69, region: 'Iringa' },
  { name: 'Kigoma', kind: 'Town', latitude: -4.8769, longitude: 29.6267, region: 'Kigoma' },
  { name: 'Kilimanjaro', kind: 'Region', latitude: -3.35, longitude: 37.34, region: 'Kilimanjaro' },
  { name: 'Lindi', kind: 'Town', latitude: -9.9971, longitude: 39.7145, region: 'Lindi' },
  { name: 'Mbeya', kind: 'City', latitude: -8.9094, longitude: 33.4608, region: 'Mbeya' },
  { name: 'Morogoro', kind: 'Town', latitude: -6.8278, longitude: 37.6591, region: 'Morogoro' },
  { name: 'Moshi', kind: 'Town', latitude: -3.35, longitude: 37.34, region: 'Kilimanjaro' },
  { name: 'Mpanda', kind: 'Town', latitude: -6.3438, longitude: 31.0694, region: 'Katavi' },
  { name: 'Mtwara', kind: 'Town', latitude: -10.2667, longitude: 40.1833, region: 'Mtwara' },
  { name: 'Musoma', kind: 'Town', latitude: -1.5, longitude: 33.8, region: 'Mara' },
  { name: 'Mwanza', kind: 'City', latitude: -2.5164, longitude: 32.9175, region: 'Mwanza' },
  { name: 'Njombe', kind: 'Town', latitude: -9.3333, longitude: 34.7667, region: 'Njombe' },
  { name: 'Rombo', kind: 'Town', latitude: -3.15, longitude: 37.52, region: 'Kilimanjaro' },
  { name: 'Shinyanga', kind: 'Town', latitude: -3.6639, longitude: 33.4211, region: 'Shinyanga' },
  { name: 'Singida', kind: 'Town', latitude: -4.8163, longitude: 34.7436, region: 'Singida' },
  { name: 'Songea', kind: 'Town', latitude: -10.6833, longitude: 35.65, region: 'Ruvuma' },
  { name: 'Sumbawanga', kind: 'Town', latitude: -7.9667, longitude: 31.6167, region: 'Rukwa' },
  { name: 'Tabora', kind: 'Town', latitude: -5.0167, longitude: 32.8, region: 'Tabora' },
  { name: 'Tanga', kind: 'Town', latitude: -5.0689, longitude: 39.0988, region: 'Tanga' },
  { name: 'Zanzibar', kind: 'City', latitude: -6.1659, longitude: 39.2026 }
];
