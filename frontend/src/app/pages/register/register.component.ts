import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { apiError } from '../../core/format';
import { MapPoint, TanzaniaMapComponent } from '../../shared/tanzania-map/tanzania-map.component';
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
  boundary = signal<MapPoint[]>([]);
  busy = signal(false);
  error = signal('');

  form = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(160)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(180)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(80)]],
    farmName: ['', [Validators.required, Validators.maxLength(160)]],
    farmCode: ['', [Validators.required, Validators.maxLength(40)]],
    region: ['', Validators.required],
    address: ['', [Validators.required, Validators.maxLength(300)]],
    contactPhone: ['', Validators.maxLength(40)]
  });

  addCorner(point: MapPoint): void {
    const corner = {
      latitude: Math.round(point.latitude * 1_000_000) / 1_000_000,
      longitude: Math.round(point.longitude * 1_000_000) / 1_000_000
    };
    const points = [...this.boundary(), corner];
    this.boundary.set(points);
    const region = regionContaining(
      points.reduce((sum, item) => sum + item.latitude, 0) / points.length,
      points.reduce((sum, item) => sum + item.longitude, 0) / points.length
    );
    if (region && !this.form.controls.region.value) {
      this.form.controls.region.setValue(region);
    }
  }

  undoCorner(): void {
    this.boundary.update(points => points.slice(0, -1));
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.boundary().length < 3) {
      this.error.set('Draw at least three corners of the farm boundary.');
      return;
    }
    if (this.form.invalid || this.busy()) {
      return;
    }
    const raw = this.form.getRawValue();
    const points = this.boundary();
    const latitude = points.reduce((sum, item) => sum + item.latitude, 0) / points.length;
    const longitude = points.reduce((sum, item) => sum + item.longitude, 0) / points.length;
    this.busy.set(true);
    this.error.set('');
    this.auth.register({
      fullName: raw.fullName.trim(),
      email: raw.email.trim(),
      password: raw.password,
      region: raw.region,
      address: raw.address.trim(),
      latitude,
      longitude,
      farmName: raw.farmName.trim(),
      farmCode: raw.farmCode.trim(),
      contactPhone: raw.contactPhone.trim(),
      boundary: points
    }).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: err => {
        this.busy.set(false);
        this.error.set(apiError(err, 'The account could not be created'));
      }
    });
  }
}
