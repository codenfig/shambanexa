import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Farm, StaffMember, WorkTask } from '../../core/models';
import { apiError } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

@Component({
  selector: 'app-staff',
  imports: [ReactiveFormsModule, PageHeaderComponent],
  templateUrl: './staff.component.html',
  styleUrl: './staff.component.css'
})
export class StaffComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);
  session = this.auth.session;
  people = signal<StaffMember[]>([]);
  tasks = signal<WorkTask[]>([]);
  farms = signal<Farm[]>([]);
  loading = signal(true);
  loadError = signal('');
  busy = signal(false);
  error = signal('');
  form = this.fb.nonNullable.group({
    fullName: ['', Validators.required],
    roleTitle: ['', Validators.required],
    farmId: ['', Validators.required],
    phone: ['']
  });

  myTasks = computed(() => {
    const name = this.session()?.fullName;
    return this.tasks().filter(task => !!name && task.assigneeName === name);
  });

  todoTasks = computed(() => this.tasks().filter(task => this.lane(task.status) === 'todo'));
  doingTasks = computed(() => this.tasks().filter(task => this.lane(task.status) === 'doing'));
  doneTasks = computed(() => this.tasks().filter(task => this.lane(task.status) === 'done'));

  taskDays = computed(() => {
    const groups = new Map<string, WorkTask[]>();
    for (const task of this.tasks()) {
      const day = task.dueOn || 'Unscheduled';
      groups.set(day, [...(groups.get(day) ?? []), task]);
    }
    return [...groups.entries()];
  });

  lane(status: string): 'todo' | 'doing' | 'done' {
    const value = status.toUpperCase();
    if (value.includes('DONE') || value.includes('COMPLETE') || value === 'CLOSED') return 'done';
    if (value.includes('PROGRESS') || value === 'DOING') return 'doing';
    return 'todo';
  }

  constructor() {
    this.reload();
    this.api.tasks().subscribe(tasks => this.tasks.set(tasks));
    this.api.farms().subscribe(farms => {
      this.farms.set(farms);
      if (farms[0]) {
        this.form.controls.farmId.setValue(farms[0].id);
      }
    });
  }

  submit(): void {
    if (this.busy() || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.api.createStaff(this.form.getRawValue()).subscribe({
      next: () => {
        this.busy.set(false);
        this.form.controls.fullName.setValue('');
        this.form.controls.phone.setValue('');
        this.toast.show('Staff member added.');
        this.reload();
      },
      error: err => {
        this.busy.set(false);
        this.error.set(apiError(err, 'Could not add staff'));
      }
    });
  }

  reload(): void {
    this.loading.set(true);
    this.loadError.set('');
    this.api.staff().subscribe({
      next: people => {
        this.people.set(people);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set('Unable to load data.');
      }
    });
  }
}
