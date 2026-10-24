import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Breed, Farm, Species } from '../../core/models';
import { apiError } from '../../core/format';

@Component({
  selector: 'app-animal-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './animal-form.component.html',
  styleUrl: './animal-form.component.css'
})
export class AnimalFormComponent {
  private fb = inject(FormBuilder);
  private api = inject(ApiService);
  private router = inject(Router);

  species = signal<Species[]>([]);
  breeds = signal<Breed[]>([]);
  farms = signal<Farm[]>([]);
  error = signal('');
  busy = signal(false);
  readonly sexes = ['FEMALE', 'MALE', 'UNKNOWN'];
  readonly healthOptions = ['HEALTHY', 'WATCH', 'SICK', 'QUARANTINE'];

  form = this.fb.nonNullable.group({
    tag: ['', [Validators.required, Validators.maxLength(40)]],
    name: ['', Validators.maxLength(120)],
    speciesId: ['', Validators.required],
    breedId: [''],
    farmId: ['', Validators.required],
    sex: ['', Validators.required],
    birthDate: [''],
    color: ['', Validators.maxLength(80)],
    healthStatus: ['HEALTHY', Validators.required]
  });

  constructor() {
    this.api.species().subscribe({
      next: items => this.species.set(items),
      error: err => this.error.set(apiError(err, 'Species could not be loaded'))
    });
    this.api.farms().subscribe({
      next: items => this.farms.set(items),
      error: err => this.error.set(apiError(err, 'Farms could not be loaded'))
    });
  }

  loadBreeds(): void {
    const speciesId = this.form.controls.speciesId.value;
    this.form.controls.breedId.setValue('');
    if (!speciesId) {
      this.breeds.set([]);
      return;
    }
    this.api.breeds(speciesId).subscribe({
      next: items => this.breeds.set(items),
      error: err => this.error.set(apiError(err, 'Breeds could not be loaded'))
    });
  }

  invalid(name: 'tag' | 'speciesId' | 'farmId' | 'sex' | 'healthStatus'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.busy());
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set('');
    const raw = this.form.getRawValue();
    this.api.createAnimal({
      tag: raw.tag.trim(),
      name: raw.name.trim(),
      speciesId: raw.speciesId,
      breedId: raw.breedId || null,
      farmId: raw.farmId,
      sex: raw.sex,
      birthDate: raw.birthDate || null,
      color: raw.color.trim(),
      healthStatus: raw.healthStatus,
      idempotencyKey: crypto.randomUUID()
    }).subscribe({
      next: () => this.router.navigate(['/livestock']),
      error: err => {
        this.busy.set(false);
        this.error.set(apiError(err, 'Could not register the animal'));
      }
    });
  }
}
