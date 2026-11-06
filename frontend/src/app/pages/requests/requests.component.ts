import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Farm, FarmerRequest } from '../../core/models';
import { apiError } from '../../core/format';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

@Component({
  selector: 'app-requests',
  imports: [PageHeaderComponent, FormsModule, ReactiveFormsModule],
  templateUrl: './requests.component.html',
  styleUrl: './requests.component.css'
})
export class RequestsComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private fb = inject(FormBuilder);

  requests = signal<FarmerRequest[]>([]);
  farms = signal<Farm[]>([]);
  loading = signal(true);
  error = signal('');
  busy = signal(false);
  replyFor = signal('');
  replyText = signal('');

  canWrite = () => this.auth.session()?.canWriteOperations === true;
  canReply = () => this.auth.session()?.canAdvise === true || this.auth.session()?.canWriteOperations === true;

  form = this.fb.nonNullable.group({
    farmId: ['', Validators.required],
    subject: ['', [Validators.required, Validators.maxLength(160)]],
    message: ['', [Validators.required, Validators.maxLength(2000)]]
  });

  constructor() {
    this.reload();
    this.api.farms().subscribe({
      next: farms => {
        this.farms.set(farms);
        if (farms[0] && !this.form.controls.farmId.value) this.form.controls.farmId.setValue(farms[0].id);
      }
    });
  }

  reload(): void {
    this.loading.set(true);
    this.api.requests().subscribe({
      next: rows => {
        this.requests.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Requests could not be loaded.');
      }
    });
  }

  submit(): void {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set('');
    const raw = this.form.getRawValue();
    this.api.createRequest({ farmId: raw.farmId, subject: raw.subject.trim(), message: raw.message.trim() }).subscribe({
      next: () => {
        this.busy.set(false);
        this.form.controls.subject.setValue('');
        this.form.controls.message.setValue('');
        this.reload();
      },
      error: err => {
        this.busy.set(false);
        this.error.set(apiError(err, 'Could not send the request'));
      }
    });
  }

  sendReply(id: string): void {
    const reply = this.replyText().trim();
    if (!reply || this.busy()) return;
    this.busy.set(true);
    this.api.replyRequest(id, reply).subscribe({
      next: () => {
        this.busy.set(false);
        this.replyFor.set('');
        this.replyText.set('');
        this.reload();
      },
      error: err => {
        this.busy.set(false);
        this.error.set(apiError(err, 'Could not send the reply'));
      }
    });
  }

  when(value: string): string {
    return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'Africa/Dar_es_Salaam' }).format(new Date(value));
  }
}
