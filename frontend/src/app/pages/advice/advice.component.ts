import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { AdviceNote } from '../../core/models';
import { apiError, relativeTime } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

@Component({
  selector: 'app-advice',
  imports: [PageHeaderComponent, ReactiveFormsModule],
  templateUrl: './advice.component.html',
  styleUrl: './advice.component.css'
})
export class AdviceComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);

  notes = signal<AdviceNote[]>([]);
  loading = signal(true);
  error = signal('');
  busy = signal(false);
  formError = signal('');
  removing = signal('');
  when = relativeTime;
  canAdvise = computed(() => this.auth.session()?.canAdvise === true);
  withMedication = computed(() => this.notes().filter(note => !!note.medication).length);
  form = this.fb.nonNullable.group({
    topic: ['', [Validators.required, Validators.maxLength(160)]],
    advice: ['', [Validators.required, Validators.maxLength(2000)]],
    medication: ['', Validators.maxLength(160)],
    medicationNote: ['', Validators.maxLength(400)]
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.advice().subscribe({
      next: notes => {
        this.notes.set(notes);
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
    const value = this.form.getRawValue();
    if (value.medicationNote.trim() && !value.medication.trim()) {
      this.formError.set('Name the medication before adding a note.');
      return;
    }
    this.busy.set(true);
    this.formError.set('');
    this.api.publishAdvice({
      topic: value.topic.trim(),
      advice: value.advice.trim(),
      medication: value.medication.trim(),
      medicationNote: value.medicationNote.trim()
    }).subscribe({
      next: saved => {
        this.busy.set(false);
        this.notes.update(rows => [saved, ...rows.filter(row => row.id !== saved.id)]);
        this.form.reset({ topic: '', advice: '', medication: '', medicationNote: '' });
        this.toast.show('Advice published for every farmer.');
      },
      error: err => {
        this.busy.set(false);
        this.formError.set(apiError(err, 'The advice could not be saved'));
      }
    });
  }

  remove(note: AdviceNote): void {
    if (this.removing()) {
      return;
    }
    this.removing.set(note.id);
    this.api.removeAdvice(note.id).subscribe({
      next: () => {
        this.removing.set('');
        this.notes.update(rows => rows.filter(row => row.id !== note.id));
        this.toast.show('Advice removed.');
      },
      error: err => {
        this.removing.set('');
        this.toast.show(apiError(err, 'The advice could not be removed'), 'err');
      }
    });
  }
}
