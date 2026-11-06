import { Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { AlertItem, Animal, Farm, HerdBatch, Species } from '../../core/models';
import { apiError } from '../../core/format';
import { ToastService } from '../../core/toast.service';

type AlertPage = 'active' | 'history' | 'rules' | 'report';

@Component({
  selector: 'app-alerts',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './alerts.component.html',
  styleUrl: './alerts.component.css'
})
export class AlertsComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);
  private router = inject(Router);

  alerts = signal<AlertItem[]>([]);
  farms = signal<Farm[]>([]);
  farmsReady = signal(false);
  page = signal<AlertPage>('active');
  species = signal<Species[]>([]);
  batches = signal<HerdBatch[]>([]);
  tagged = signal<Animal[]>([]);
  farmId = signal('');
  outcome = signal('SICK');
  loading = signal(true);
  error = signal('');
  busy = signal(false);
  formError = signal('');
  canReport = computed(() => this.auth.session()?.canWriteOperations === true);
  openAlerts = computed(() => this.alerts().filter(alert => alert.status === 'OPEN'));
  closedAlerts = computed(() => this.alerts().filter(alert => alert.status !== 'OPEN'));
  selected = signal<AlertItem | null>(null);
  farmBatches = computed(() => this.batches().filter(batch => batch.farmId === this.farmId() && batch.status === 'ACTIVE'));
  counts = computed(() => {
    const open = this.openAlerts();
    return {
      critical: open.filter(alert => alert.severity === 'CRITICAL').length,
      warning: open.filter(alert => alert.severity === 'WARNING').length,
      info: open.filter(alert => alert.severity !== 'CRITICAL' && alert.severity !== 'WARNING').length,
      disease: open.filter(alert => alert.sourceType === 'DISEASE' || alert.sourceType === 'SICK' || alert.sourceType === 'DEATH').length,
      stored: this.alerts().length
    };
  });
  readonly conditions = [
    'Foot-and-mouth disease',
    'Lumpy skin disease',
    'East Coast fever',
    'Newcastle disease',
    'Peste des petits ruminants',
    'African swine fever',
    'Anthrax',
    'Contagious bovine pleuropneumonia',
    'Mastitis',
    'Trypanosomiasis'
  ];
  form = this.fb.nonNullable.group({
    farmId: ['', Validators.required],
    outcome: ['SICK', Validators.required],
    diseaseName: ['', Validators.maxLength(120)],
    status: ['SUSPECTED'],
    animalsAffected: [1, [Validators.required, Validators.min(1), Validators.max(1_000_000)]],
    species: [''],
    batchId: [''],
    animalId: [''],
    signs: ['', [Validators.required, Validators.maxLength(280)]],
    noticedOn: [new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Dar_es_Salaam' }).format(new Date())]
  });

  constructor() {
    this.applyRoute();
    this.router.events.subscribe(event => {
      if (event instanceof NavigationEnd) {
        this.applyRoute();
      }
    });
    this.api.farms().subscribe({
      next: farms => {
        this.farms.set(farms);
        this.farmsReady.set(true);
        if (farms[0] && !this.form.controls.farmId.value) {
          this.form.controls.farmId.setValue(farms[0].id);
        }
      },
      error: () => {
        this.farms.set([]);
        this.farmsReady.set(true);
      }
    });
    this.api.species().subscribe({ next: rows => this.species.set(rows), error: () => this.species.set([]) });
    this.api.batches().subscribe({ next: rows => this.batches.set(rows), error: () => this.batches.set([]) });
    this.form.controls.farmId.valueChanges.subscribe(id => {
      this.farmId.set(id);
      this.form.controls.batchId.setValue('');
      this.form.controls.animalId.setValue('');
      this.loadAnimals(id);
    });
    this.form.controls.outcome.valueChanges.subscribe(value => {
      this.outcome.set(value);
      const disease = this.form.controls.diseaseName;
      if (value === 'DISEASE') {
        disease.addValidators(Validators.required);
      } else {
        disease.removeValidators(Validators.required);
      }
      disease.updateValueAndValidity();
    });
    this.form.controls.batchId.valueChanges.subscribe(id => {
      if (id) {
        this.form.controls.animalId.setValue('', { emitEvent: false });
      }
    });
    this.form.controls.animalId.valueChanges.subscribe(id => {
      if (id) {
        this.form.controls.batchId.setValue('', { emitEvent: false });
        this.form.controls.animalsAffected.setValue(1);
      }
    });
    this.load();
  }

  private loadAnimals(farmId: string): void {
    if (!farmId) {
      this.tagged.set([]);
      return;
    }
    this.api.animals({ farmId, size: 100 }).subscribe({
      next: page => this.tagged.set(page.items.filter(animal => animal.status !== 'DEAD')),
      error: () => this.tagged.set([])
    });
  }

  private applyRoute(): void {
    const path = this.router.url.split(/[?#]/)[0];
    if (path.endsWith('/history')) {
      this.page.set('history');
    } else if (path.endsWith('/rules')) {
      this.page.set('rules');
    } else if (path.endsWith('/report')) {
      this.page.set('report');
    } else {
      this.page.set('active');
    }
  }

  choose(alert: AlertItem): void {
    this.selected.set(this.selected()?.id === alert.id ? null : alert);
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.alerts().subscribe({
      next: alerts => {
        this.alerts.set(alerts);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Unable to load data.');
      }
    });
  }

  submit(): void {
    if (this.busy() || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.formError.set('');
    const value = this.form.getRawValue();
    if (value.outcome === 'DISEASE' && !value.diseaseName.trim()) {
      this.form.controls.diseaseName.setErrors({ required: true });
      this.busy.set(false);
      return;
    }
    const done = () => {
      this.busy.set(false);
      this.form.controls.diseaseName.setValue('');
      this.form.controls.signs.setValue('');
      this.form.controls.animalsAffected.setValue(1);
      this.form.controls.batchId.setValue('');
      this.form.controls.animalId.setValue('');
      this.load();
    };
    if (value.outcome === 'DISEASE') {
      this.api.reportDisease({
        farmId: value.farmId,
        diseaseName: value.diseaseName.trim(),
        status: value.status,
        animalsAffected: Number(value.animalsAffected),
        species: value.species.trim(),
        signs: value.signs.trim(),
        noticedOn: value.noticedOn
      }).subscribe({
        next: saved => {
          this.toast.show(`Disease alert recorded for ${saved.farmName}.`);
          done();
        },
        error: err => {
          this.busy.set(false);
          this.formError.set(apiError(err, 'The disease report could not be saved'));
        }
      });
      return;
    }
    this.api.reportAnimalHealth({
      farmId: value.farmId,
      outcome: value.outcome,
      animals: value.animalId ? 1 : Number(value.animalsAffected),
      condition: value.diseaseName.trim(),
      species: value.species.trim(),
      notes: value.signs.trim(),
      occurredOn: value.noticedOn,
      batchId: value.batchId || null,
      animalId: value.animalId || null
    }).subscribe({
      next: saved => {
        const herd = saved.herdQuantity == null ? '' : ` Herd headcount is now ${saved.herdQuantity}.`;
        const unchanged = value.outcome === 'DEATH' && saved.herdQuantity == null && !value.animalId
          ? ' Headcount was not changed because no herd was selected.'
          : '';
        const label = value.outcome === 'DEATH' ? 'Deaths recorded' : value.outcome === 'CURED' ? 'Recovery recorded' : 'Sick animals recorded';
        this.toast.show(`${label} for ${saved.farmName}.${herd}${unchanged}`);
        done();
      },
      error: err => {
        this.busy.set(false);
        this.formError.set(apiError(err, 'The health report could not be saved'));
      }
    });
  }

  severityLabel(severity: string): string {
    if (severity === 'CRITICAL') {
      return 'Critical';
    }
    if (severity === 'WARNING') {
      return 'Warning';
    }
    if (severity === 'INFO') {
      return 'Info';
    }
    return severity;
  }

  sourceLabel(source: string | null | undefined): string {
    if (source === 'PRODUCTION') {
      return 'Production';
    }
    if (source === 'TASK') {
      return 'Task';
    }
    if (source === 'INVENTORY') {
      return 'Stock';
    }
    if (source === 'DISEASE') {
      return 'Disease';
    }
    if (source === 'SICK') {
      return 'Sick';
    }
    if (source === 'DEATH') {
      return 'Death';
    }
    return source ?? '';
  }

  stamp(iso: string): string {
    return new Intl.DateTimeFormat('en-GB', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
      timeZone: 'Africa/Dar_es_Salaam'
    }).format(new Date(iso));
  }
}
