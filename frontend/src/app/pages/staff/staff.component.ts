import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, NavigationEnd, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Farm, StaffMember, WorkTask, AttendanceMark, StaffShift, StaffTimesheet, StaffReview, StaffTraining, StaffGoal, StaffCompetency } from '../../core/models';
import { chartSlice } from '../../core/chart-colors';
import { apiError, formatNumber } from '../../core/format';
import { ToastService } from '../../core/toast.service';

type StaffScreen = 'overview' | 'directory' | 'profile' | 'assignments' | 'attendance' | 'performance';
type ProfileTab = 'record' | 'tasks';

interface Slice {
  name: string;
  count: number;
  share: number;
  color: string;
}

interface TaskDay {
  key: string;
  label: string;
  late: boolean;
  tasks: WorkTask[];
}

const COLORS = [chartSlice(0), chartSlice(1), chartSlice(2)];
const SCREENS = ['directory', 'assignments', 'attendance', 'performance'];

@Component({
  selector: 'app-staff',
  imports: [FormsModule, ReactiveFormsModule, RouterLink],
  templateUrl: './staff.component.html',
  styleUrl: './staff.component.css'
})
export class StaffComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private readonly today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Dar_es_Salaam',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(new Date());

  readonly screen = signal<StaffScreen>('overview');
  readonly profileTab = signal<ProfileTab>('record');
  private readonly detailId = signal('');
  session = this.auth.session;
  people = signal<StaffMember[]>([]);
  tasks = signal<WorkTask[]>([]);
  attendance = signal<AttendanceMark[]>([]);
  shifts = signal<StaffShift[]>([]);
  timesheets = signal<StaffTimesheet[]>([]);
  reviews = signal<StaffReview[]>([]);
  training = signal<StaffTraining[]>([]);
  goals = signal<StaffGoal[]>([]);
  competencies = signal<StaffCompetency[]>([]);
  workforceError = signal('');
  readonly attendanceMarks = ['PRESENT', 'LATE', 'ABSENT', 'LEAVE'];
  readonly weekdays = [
    { value: 1, label: 'Monday' },
    { value: 2, label: 'Tuesday' },
    { value: 3, label: 'Wednesday' },
    { value: 4, label: 'Thursday' },
    { value: 5, label: 'Friday' },
    { value: 6, label: 'Saturday' },
    { value: 7, label: 'Sunday' }
  ];
  shiftStaff = signal('');
  shiftDay = signal(1);
  shiftStart = signal('08:00');
  shiftEnd = signal('16:00');
  sheetStaff = signal('');
  sheetHours = signal('');
  sheetNote = signal('');
  reviewStaff = signal('');
  reviewScore = signal('');
  reviewNotes = signal('');
  trainingStaff = signal('');
  trainingTitle = signal('');
  trainingDate = signal('');
  goalStaff = signal('');
  goalTitle = signal('');
  skillStaff = signal('');
  skillName = signal('');
  skillLevel = signal('MET');
  farms = signal<Farm[]>([]);
  loading = signal(true);
  tasksReady = signal(false);
  loadError = signal('');
  taskError = signal('');
  busy = signal(false);
  error = signal('');
  formOpen = signal(false);
  query = signal('');
  farmFilter = signal('');
  roleFilter = signal('');
  statusFilter = signal('');
  page = signal(0);
  rollPage = signal(0);
  readonly pageSize = 8;
  canWrite = computed(() => this.auth.session()?.canWriteOperations === true);
  fmt = formatNumber;
  form = this.fb.nonNullable.group({
    fullName: ['', Validators.required],
    roleTitle: ['', Validators.required],
    farmId: ['', Validators.required],
    phone: ['']
  });

  filteredPeople = computed(() => {
    const query = this.query().trim().toLowerCase();
    const farmId = this.farmFilter();
    const role = this.roleFilter();
    const status = this.statusFilter();
    return this.people().filter(person => {
      if (farmId && person.farmId !== farmId) return false;
      if (role && person.roleTitle !== role) return false;
      if (status && person.status !== status) return false;
      if (!query) return true;
      return [person.fullName, person.roleTitle, person.farmName, person.phone || ''].join(' ').toLowerCase().includes(query);
    });
  });

  pageCount = computed(() => Math.max(1, Math.ceil(this.filteredPeople().length / this.pageSize)));
  visiblePage = computed(() => Math.min(this.page(), this.pageCount() - 1));
  pagedPeople = computed(() => {
    const start = this.visiblePage() * this.pageSize;
    return this.filteredPeople().slice(start, start + this.pageSize);
  });
  rollPageCount = computed(() => Math.max(1, Math.ceil(this.people().length / this.pageSize)));
  rollVisiblePage = computed(() => Math.min(this.rollPage(), this.rollPageCount() - 1));
  pagedRoll = computed(() => {
    const start = this.rollVisiblePage() * this.pageSize;
    return this.people().slice(start, start + this.pageSize);
  });
  rollPages = computed(() => this.pageWindow(this.rollPageCount(), this.rollVisiblePage()));
  rollRangeLabel = computed(() => {
    const total = this.people().length;
    if (total === 0) {
      return 'Showing 0 people';
    }
    const start = this.rollVisiblePage() * this.pageSize + 1;
    const end = Math.min(total, start + this.pageSize - 1);
    return `Showing ${start}–${end} of ${formatNumber(total)} people`;
  });

  farmChoices = computed(() => {
    const seen = new Map<string, string>();
    for (const person of this.people()) seen.set(person.farmId, person.farmName);
    return [...seen.entries()].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name));
  });

  roleChoices = computed(() => [...new Set(this.people().map(person => person.roleTitle))].sort());
  statusChoices = computed(() => [...new Set(this.people().map(person => person.status))].sort());
  activeCount = computed(() => this.people().filter(person => person.status.toUpperCase() === 'ACTIVE').length);
  inactiveCount = computed(() => this.people().length - this.activeCount());
  roleSlices = computed(() => this.slices(person => person.roleTitle));
  farmSlices = computed(() => this.slices(person => person.farmName));
  farmGroups = computed(() => {
    const groups = new Map<string, { id: string; name: string; people: StaffMember[] }>();
    for (const person of this.people()) {
      const current = groups.get(person.farmId) ?? { id: person.farmId, name: person.farmName, people: [] };
      current.people.push(person);
      groups.set(person.farmId, current);
    }
    return [...groups.values()]
      .map(group => ({
        ...group,
        people: [...group.people].sort((a, b) => a.fullName.localeCompare(b.fullName))
      }))
      .sort((a, b) => b.people.length - a.people.length || a.name.localeCompare(b.name));
  });
  roleDonut = computed(() => this.donut(this.roleSlices()));
  farmDonut = computed(() => this.donut(this.farmSlices()));
  preview = computed(() => this.people().slice(0, 5));
  selected = computed(() => this.people().find(person => person.id === this.detailId()) ?? null);
  openTasks = computed(() => this.tasks().filter(task => this.lane(task.status) !== 'done'));
  overdueTasks = computed(() => this.openTasks().filter(task => !!task.dueOn && task.dueOn < this.today));

  myTasks = computed(() => {
    const name = this.session()?.fullName;
    return this.tasks().filter(task => !!name && task.assigneeName === name && this.lane(task.status) !== 'done');
  });

  profileTasks = computed(() => {
    const name = this.selected()?.fullName;
    return this.tasks().filter(task => !!name && task.assigneeName === name);
  });

  todoTasks = computed(() => this.tasks().filter(task => this.lane(task.status) === 'todo'));
  doingTasks = computed(() => this.tasks().filter(task => this.lane(task.status) === 'doing'));
  doneTasks = computed(() => this.tasks().filter(task => this.lane(task.status) === 'done'));

  taskDays = computed<TaskDay[]>(() => {
    const groups = new Map<string, WorkTask[]>();
    for (const task of this.tasks()) {
      const day = task.dueOn || 'Unscheduled';
      groups.set(day, [...(groups.get(day) ?? []), task]);
    }
    return [...groups.entries()]
      .sort((a, b) => a[0] === 'Unscheduled' ? 1 : b[0] === 'Unscheduled' ? -1 : a[0].localeCompare(b[0]))
      .map(([key, tasks]) => ({
        key,
        label: key === 'Unscheduled' ? 'Unscheduled' : this.dateLabel(key),
        late: key !== 'Unscheduled' && key < this.today && tasks.some(task => this.lane(task.status) !== 'done'),
        tasks
      }));
  });

  constructor() {
    this.applyRoute();
    this.router.events.subscribe(event => {
      if (event instanceof NavigationEnd) this.applyRoute();
    });
    this.reload();
    this.loadWorkforce();
    this.api.tasks().subscribe({
      next: tasks => {
        this.tasks.set(tasks);
        this.tasksReady.set(true);
      },
      error: () => {
        this.tasks.set([]);
        this.taskError.set('Tasks could not be loaded.');
        this.tasksReady.set(true);
      }
    });
    this.api.farms().subscribe({
      next: farms => {
        this.farms.set(farms);
        if (farms[0] && !this.form.controls.farmId.value) this.form.controls.farmId.setValue(farms[0].id);
      },
      error: () => this.farms.set([])
    });
  }

  private applyRoute(): void {
    const url = this.router.url;
    const path = url.split(/[?#]/)[0];
    const parts = path.split('/').filter(Boolean);
    const fragment = url.includes('#') ? decodeURIComponent(url.split('#')[1] || '') : '';
    if (fragment === 'add') this.formOpen.set(this.canWrite());
    this.detailId.set('');
    const section = parts[1] || '';
    if (section === 'directory') {
      this.screen.set('directory');
      return;
    }
    if (section === 'assignments') {
      this.screen.set('assignments');
      return;
    }
    if (section === 'attendance') {
      this.screen.set('attendance');
      return;
    }
    if (section === 'performance') {
      this.screen.set('performance');
      return;
    }
    if (section && !SCREENS.includes(section)) {
      this.screen.set('profile');
      this.detailId.set(section);
      this.profileTab.set('record');
      return;
    }
    this.screen.set('overview');
    if (fragment === 'people') {
      this.router.navigate(['/staff', 'directory'], { replaceUrl: true });
      return;
    }
    if (fragment === 'assignments') {
      this.router.navigate(['/staff', 'assignments'], { replaceUrl: true });
      return;
    }
    if (fragment === 'tasks' || fragment === 'my-tasks' || fragment === 'calendar') {
      this.router.navigate(['/staff', 'assignments'], { fragment, replaceUrl: true });
    }
  }

  pageTitle(): string {
    const screen = this.screen();
    if (screen === 'directory') return 'Staff directory';
    if (screen === 'assignments') return 'Assignments';
    if (screen === 'attendance') return 'Attendance & shifts';
    if (screen === 'performance') return 'Performance & training';
    if (screen === 'profile') return 'Staff profile';
    return 'Staff overview';
  }

  pageBlurb(): string {
    const screen = this.screen();
    if (screen === 'directory') return 'People recorded on the farms in your scope.';
    if (screen === 'assignments') return 'Farm placement for each person, and the open tasks stored for them.';
    if (screen === 'attendance') return 'Daily marks, shifts, and hours recorded for people on your farms.';
    if (screen === 'performance') return 'Reviews, training, goals, and skills recorded for your people.';
    if (screen === 'profile') return 'The record stored for this person.';
    return 'People and open tasks for the farms in your scope.';
  }

  scopeChip(): string {
    if (this.session()?.allFarms) return 'All farms';
    const count = new Set(this.people().map(person => person.farmId)).size;
    return count === 1 ? '1 farm' : `${count} farms`;
  }

  filtersActive(): boolean {
    return !!this.query().trim() || !!this.farmFilter() || !!this.roleFilter() || !!this.statusFilter();
  }

  setQuery(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
    this.page.set(0);
  }

  setFarm(event: Event): void {
    this.farmFilter.set((event.target as HTMLSelectElement).value);
    this.page.set(0);
  }

  setRole(event: Event): void {
    this.roleFilter.set((event.target as HTMLSelectElement).value);
    this.page.set(0);
  }

  setStatus(event: Event): void {
    this.statusFilter.set((event.target as HTMLSelectElement).value);
    this.page.set(0);
  }

  clearFilters(): void {
    this.query.set('');
    this.farmFilter.set('');
    this.roleFilter.set('');
    this.statusFilter.set('');
    this.page.set(0);
  }

  go(index: number): void {
    this.page.set(Math.min(Math.max(index, 0), this.pageCount() - 1));
  }

  goRoll(index: number): void {
    this.rollPage.set(Math.min(Math.max(index, 0), this.rollPageCount() - 1));
  }

  pages(): number[] {
    return Array.from({ length: this.pageCount() }, (_, index) => index);
  }

  private pageWindow(count: number, current: number): Array<number | 'gap'> {
    if (count <= 7) {
      return Array.from({ length: count }, (_, index) => index);
    }
    const wanted = [0, current - 1, current, current + 1, count - 1]
      .filter((value, index, list) => value >= 0 && value < count && list.indexOf(value) === index)
      .sort((a, b) => a - b);
    const window: Array<number | 'gap'> = [];
    wanted.forEach((value, index) => {
      if (index > 0 && value - wanted[index - 1] > 1) {
        window.push('gap');
      }
      window.push(value);
    });
    return window;
  }

  rangeLabel(): string {
    const total = this.filteredPeople().length;
    if (total === 0) return '0 people';
    const start = this.visiblePage() * this.pageSize + 1;
    const end = Math.min(total, start + this.pageSize - 1);
    return `${start}–${end} of ${this.fmt(total)}`;
  }

  open(person: StaffMember): void {
    this.router.navigate(['/staff', person.id]);
  }

  shareLabel(value: number): string {
    return `${Math.round(value)}%`;
  }

  initial(person: StaffMember): string {
    const parts = person.fullName.split(/\s+/).filter(Boolean);
    return ((parts[0]?.[0] || '') + (parts[1]?.[0] || '')).toUpperCase();
  }

  tone(name: string): string {
    let total = 0;
    for (const char of name) total += char.charCodeAt(0);
    return ['green', 'gold', 'blue'][total % 3];
  }

  label(value: string): string {
    return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, char => char.toUpperCase());
  }

  statusClass(status: string): string {
    return status.toUpperCase() === 'ACTIVE' ? 'is-active' : 'is-other';
  }

  late(task: WorkTask): boolean {
    return !!task.dueOn && task.dueOn < this.today && this.lane(task.status) !== 'done';
  }

  todayLabel(): string {
    return new Intl.DateTimeFormat('en-GB', {
      weekday: 'long', day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC'
    }).format(new Date(`${this.today}T00:00:00Z`));
  }

  dateLabel(value: string | null): string {
    if (!value) return '—';
    const day = value.slice(0, 10);
    return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${day}T00:00:00Z`));
  }

  dueLabel(value: string | null): string {
    if (!value) return 'No date';
    return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' }).format(new Date(`${value.slice(0, 10)}T00:00:00Z`));
  }

  lane(status: string): 'todo' | 'doing' | 'done' {
    const value = status.toUpperCase();
    if (value.includes('DONE') || value.includes('COMPLETE') || value === 'CLOSED') return 'done';
    if (value.includes('PROGRESS') || value === 'DOING') return 'doing';
    return 'todo';
  }

  exportCsv(): void {
    const source = this.screen() === 'directory' ? this.filteredPeople() : this.people();
    const rows = [
      ['Name', 'Role', 'Farm', 'Phone', 'Hired', 'Status'],
      ...source.map(person => [person.fullName, person.roleTitle, person.farmName, person.phone || '', person.hiredOn || '', person.status])
    ];
    const csv = rows.map(columns => columns.map(value => `"${String(value).replaceAll('"', '""')}"`).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'shambanexa-staff.csv';
    link.click();
    URL.revokeObjectURL(url);
  }

  openForm(): void {
    this.error.set('');
    this.formOpen.set(true);
  }

  closeForm(): void {
    if (this.busy()) return;
    this.formOpen.set(false);
    if (this.router.url.includes('#add')) {
      this.router.navigateByUrl(this.router.url.split('#')[0], { replaceUrl: true });
    }
  }

  @HostListener('document:keydown.escape')
  closeOnEscape(): void {
    if (this.formOpen()) this.closeForm();
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
        this.form.controls.roleTitle.setValue('');
        this.form.controls.phone.setValue('');
        this.formOpen.set(false);
        this.toast.show('Staff member added.');
        this.reload();
      },
      error: err => {
        this.busy.set(false);
        this.error.set(apiError(err, 'Could not add staff'));
      }
    });
  }

  loadWorkforce(): void {
    this.api.workforce().subscribe({
      next: bundle => {
        this.attendance.set(bundle.attendance);
        this.shifts.set(bundle.shifts);
        this.timesheets.set(bundle.timesheets);
        this.reviews.set(bundle.reviews);
        this.training.set(bundle.training);
        this.goals.set(bundle.goals);
        this.competencies.set(bundle.competencies);
        this.workforceError.set('');
      },
      error: () => this.workforceError.set('Attendance and performance records could not be loaded.')
    });
  }

  todayMarks(): AttendanceMark[] {
    return this.attendance().filter(row => row.workDate === this.today);
  }

  attendanceTaken(): boolean {
    return this.todayMarks().length > 0;
  }

  countMark(mark: string): number {
    return this.todayMarks().filter(row => row.mark === mark).length;
  }

  markFor(staffId: string): string {
    return this.todayMarks().find(row => row.staffId === staffId)?.mark ?? '';
  }

  markLabel(mark: string): string {
    if (mark === 'PRESENT') return 'Present';
    if (mark === 'LATE') return 'Late';
    if (mark === 'ABSENT') return 'Absent';
    if (mark === 'LEAVE') return 'Leave';
    return 'Not recorded';
  }

  weekdayLabel(day: number): string {
    return this.weekdays.find(item => item.value === day)?.label ?? '';
  }

  onShiftDay(event: Event): void {
    this.shiftDay.set(Number((event.target as HTMLSelectElement).value));
  }

  clock(value: string): string {
    return value ? value.slice(0, 5) : '';
  }

  latestScore(staffId: string): string {
    const review = this.reviews().find(row => row.staffId === staffId);
    return review ? Number(review.score).toFixed(1) : '—';
  }

  trainingCount(staffId: string): string {
    const count = this.training().filter(row => row.staffId === staffId).length;
    return count === 0 ? '—' : String(count);
  }

  openGoalCount(staffId: string): string {
    const count = this.goals().filter(row => row.staffId === staffId && row.status === 'OPEN').length;
    return count === 0 ? '—' : String(count);
  }

  reviewScoreLabel(): string {
    const latest = new Map<string, StaffReview>();
    for (const review of this.reviews()) {
      const current = latest.get(review.staffId);
      if (!current || review.reviewedOn > current.reviewedOn) latest.set(review.staffId, review);
    }
    if (latest.size === 0) return '—';
    const total = [...latest.values()].reduce((sum, review) => sum + Number(review.score), 0);
    return (total / latest.size).toFixed(1);
  }

  filedLabel(count: number): string {
    return count === 0 ? 'None filed' : 'Filed';
  }

  saveMark(person: StaffMember, mark: string): void {
    if (!this.canWrite() || this.busy()) return;
    this.busy.set(true);
    this.api.saveAttendance({ staffId: person.id, workDate: this.today, mark }).subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.show('Attendance saved.');
        this.loadWorkforce();
      },
      error: err => {
        this.busy.set(false);
        this.workforceError.set(apiError(err, 'Could not save attendance'));
      }
    });
  }

  saveShift(): void {
    if (!this.canWrite() || this.busy() || !this.shiftStaff()) return;
    this.busy.set(true);
    this.api.saveShift({ staffId: this.shiftStaff(), weekday: Number(this.shiftDay()), starts: this.shiftStart(), ends: this.shiftEnd() }).subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.show('Shift saved.');
        this.loadWorkforce();
      },
      error: err => {
        this.busy.set(false);
        this.workforceError.set(apiError(err, 'Could not save the shift'));
      }
    });
  }

  saveTimesheet(): void {
    const hours = Number(this.sheetHours());
    if (!this.canWrite() || this.busy() || !this.sheetStaff() || !hours) return;
    this.busy.set(true);
    this.api.saveTimesheet({ staffId: this.sheetStaff(), workDate: this.today, hours, note: this.sheetNote() }).subscribe({
      next: () => {
        this.busy.set(false);
        this.sheetHours.set('');
        this.sheetNote.set('');
        this.toast.show('Timesheet saved.');
        this.loadWorkforce();
      },
      error: err => {
        this.busy.set(false);
        this.workforceError.set(apiError(err, 'Could not save the timesheet'));
      }
    });
  }

  saveReview(): void {
    const score = Number(this.reviewScore());
    if (!this.canWrite() || this.busy() || !this.reviewStaff() || Number.isNaN(score)) return;
    this.busy.set(true);
    this.api.saveReview({ staffId: this.reviewStaff(), score, reviewedOn: this.today, notes: this.reviewNotes() }).subscribe({
      next: () => {
        this.busy.set(false);
        this.reviewScore.set('');
        this.reviewNotes.set('');
        this.toast.show('Review saved.');
        this.loadWorkforce();
      },
      error: err => {
        this.busy.set(false);
        this.workforceError.set(apiError(err, 'Could not save the review'));
      }
    });
  }

  saveTraining(): void {
    if (!this.canWrite() || this.busy() || !this.trainingStaff() || !this.trainingTitle().trim()) return;
    this.busy.set(true);
    this.api.saveTraining({ staffId: this.trainingStaff(), title: this.trainingTitle().trim(), completedOn: this.trainingDate() || null }).subscribe({
      next: () => {
        this.busy.set(false);
        this.trainingTitle.set('');
        this.trainingDate.set('');
        this.toast.show('Training saved.');
        this.loadWorkforce();
      },
      error: err => {
        this.busy.set(false);
        this.workforceError.set(apiError(err, 'Could not save training'));
      }
    });
  }

  saveGoal(): void {
    if (!this.canWrite() || this.busy() || !this.goalStaff() || !this.goalTitle().trim()) return;
    this.busy.set(true);
    this.api.saveGoal({ staffId: this.goalStaff(), title: this.goalTitle().trim() }).subscribe({
      next: () => {
        this.busy.set(false);
        this.goalTitle.set('');
        this.toast.show('Goal saved.');
        this.loadWorkforce();
      },
      error: err => {
        this.busy.set(false);
        this.workforceError.set(apiError(err, 'Could not save the goal'));
      }
    });
  }

  finishGoal(id: string): void {
    if (!this.canWrite() || this.busy()) return;
    this.busy.set(true);
    this.api.completeGoal(id).subscribe({
      next: () => {
        this.busy.set(false);
        this.loadWorkforce();
      },
      error: err => {
        this.busy.set(false);
        this.workforceError.set(apiError(err, 'Could not update the goal'));
      }
    });
  }

  saveSkill(): void {
    if (!this.canWrite() || this.busy() || !this.skillStaff() || !this.skillName().trim()) return;
    this.busy.set(true);
    this.api.saveCompetency({ staffId: this.skillStaff(), skill: this.skillName().trim(), level: this.skillLevel() }).subscribe({
      next: () => {
        this.busy.set(false);
        this.skillName.set('');
        this.toast.show('Skill saved.');
        this.loadWorkforce();
      },
      error: err => {
        this.busy.set(false);
        this.workforceError.set(apiError(err, 'Could not save the skill'));
      }
    });
  }

  reload(): void {
    this.loading.set(true);
    this.loadError.set('');
    this.api.staff().subscribe({
      next: people => {
        this.people.set([...people].sort((a, b) => a.fullName.localeCompare(b.fullName)));
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set('Unable to load staff.');
      }
    });
  }

  private slices(pick: (person: StaffMember) => string): Slice[] {
    const counts = new Map<string, number>();
    for (const person of this.people()) {
      const name = pick(person);
      counts.set(name, (counts.get(name) || 0) + 1);
    }
    const total = this.people().length || 1;
    return [...counts.entries()]
      .map(([name, count]) => ({ name, count, share: (count / total) * 100, color: '' }))
      .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name))
      .map((item, index) => ({ ...item, color: COLORS[index % COLORS.length] }));
  }

  private donut(items: Slice[]): string {
    if (items.length === 0) return 'conic-gradient(#e7ece8 0 100%)';
    let cursor = 0;
    const stops = items.map(item => {
      const start = cursor;
      cursor += item.share;
      return `${item.color} ${start}% ${cursor}%`;
    });
    return `conic-gradient(${stops.join(',')})`;
  }
}
