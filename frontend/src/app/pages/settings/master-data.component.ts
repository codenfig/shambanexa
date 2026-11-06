import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ApiService } from '../../core/api.service';
import { MasterCategory, MasterDetail, MasterImport, MasterRecord } from '../../core/models';

type SortKey = '' | 'code' | 'name' | 'group' | 'status' | 'updated';

@Component({
  selector: 'app-master-data',
  templateUrl: './master-data.component.html',
  styleUrl: './master-data.component.css'
})
export class MasterDataComponent implements OnInit {
  private api = inject(ApiService);

  @Input() canEdit = false;

  categories = signal<MasterCategory[]>([]);
  category = signal('SPECIES');
  query = signal('');
  status = signal('');
  parentId = signal('');
  sort = signal<SortKey>('');
  direction = signal<'asc' | 'desc'>('asc');
  page = signal(0);
  total = signal(0);
  size = signal(8);
  rows = signal<MasterRecord[]>([]);
  parents = signal<MasterRecord[]>([]);
  groups = signal<string[]>([]);
  loading = signal(true);
  error = signal('');
  notice = signal('');
  detail = signal<MasterDetail | null>(null);
  editing = signal(false);
  saving = signal(false);
  formError = signal('');
  confirm = signal<{ id: string; name: string; active: boolean; usage: number } | null>(null);
  removing = signal<MasterRecord | null>(null);
  preview = signal<MasterImport | null>(null);
  updateExisting = signal(false);
  importing = signal(false);
  private file: File | null = null;

  code = signal('');
  name = signal('');
  group = signal('LIVESTOCK');
  color = signal('#1E8330');
  sortOrder = signal('1');
  tracking = signal(true);
  breedSpecies = signal('');
  note = signal('');
  numericValue = signal('');

  ngOnInit(): void {
    this.api.masterCategories().subscribe({
      next: rows => {
        this.categories.set(rows);
        this.loadGroups();
        this.load();
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Master data could not be loaded.');
      }
    });
  }

  chosen(): MasterCategory | undefined {
    return this.categories().find(item => item.code === this.category());
  }

  pickCategory(code: string): void {
    this.category.set(code);
    this.page.set(0);
    this.query.set('');
    this.status.set('');
    this.parentId.set('');
    this.sort.set('');
    this.close();
    this.load();
  }

  search(value: string): void {
    this.query.set(value);
    this.page.set(0);
    this.load();
  }

  filterStatus(value: string): void {
    this.status.set(value);
    this.page.set(0);
    this.load();
  }

  filterParent(value: string): void {
    this.parentId.set(value);
    this.page.set(0);
    this.load();
  }

  toggleSort(key: SortKey): void {
    if (this.sort() === key) {
      this.direction.set(this.direction() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sort.set(key);
      this.direction.set('asc');
    }
    this.load();
  }

  go(next: number): void {
    const pages = this.pageCount();
    if (next < 0 || next >= pages) {
      return;
    }
    this.page.set(next);
    this.load();
  }

  pageCount(): number {
    return Math.max(1, Math.ceil(this.total() / this.size()));
  }

  range(): string {
    if (!this.total()) {
      return '0 records';
    }
    const from = this.page() * this.size() + 1;
    const to = Math.min(this.total(), from + this.rows().length - 1);
    return `${from}–${to} of ${this.total()}`;
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    const category = this.category();
    const params: { q?: string; status?: string; parentId?: string; sort?: string; direction?: string; page?: number; size?: number } = {
      q: this.query(),
      direction: this.direction(),
      page: this.page(),
      size: this.size()
    };
    if (this.status()) {
      params.status = this.status();
    }
    if (this.parentId()) {
      params.parentId = this.parentId();
    }
    if (this.sort()) {
      params.sort = this.sort();
    }
    this.api.masterPage(category, params).subscribe({
      next: page => {
        this.rows.set(page.records);
        this.total.set(page.total);
        this.size.set(page.size);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Master data could not be loaded.');
      }
    });
    if (category === 'BREEDS') {
      this.api.masterPage('SPECIES', { size: 50 }).subscribe({
        next: page => this.parents.set(page.records),
        error: () => this.parents.set([])
      });
    }
    if (category === 'INV_TYPE') {
      this.api.masterPage('INV_CATEGORY', { size: 50 }).subscribe({
        next: page => this.parents.set(page.records),
        error: () => this.parents.set([])
      });
    }
  }

  inventoryList(): boolean {
    return this.category().startsWith('INV_');
  }

  open(row: MasterRecord): void {
    this.editing.set(false);
    this.formError.set('');
    this.preview.set(null);
    this.api.masterDetail(this.category(), row.id).subscribe({
      next: detail => {
        this.detail.set(detail);
        this.fill(detail.record);
      },
      error: () => this.formError.set('That record could not be opened.')
    });
  }

  startAdd(): void {
    this.detail.set(null);
    this.editing.set(true);
    this.formError.set('');
    this.preview.set(null);
    this.code.set('');
    this.name.set('');
    this.group.set(this.groups().includes('LIVESTOCK') ? 'LIVESTOCK' : (this.groups()[0] || ''));
    this.color.set('#1E8330');
    this.sortOrder.set(String((this.total() || 0) + 1));
    this.tracking.set(true);
    this.breedSpecies.set(this.parentId() || this.parents()[0]?.id || '');
    this.note.set('');
    this.numericValue.set(this.category() === 'INV_WINDOW' ? '30' : '');
    if (this.category() === 'BREEDS' && !this.parents().length) {
      this.api.masterPage('SPECIES', { size: 50 }).subscribe({
        next: page => {
          this.parents.set(page.records);
          if (!this.breedSpecies()) {
            this.breedSpecies.set(page.records[0]?.id || '');
          }
        }
      });
    }
  }

  edit(): void {
    const row = this.detail()?.record;
    if (!row || !this.canEdit) {
      return;
    }
    this.fill(row);
    this.editing.set(true);
    this.formError.set('');
  }

  close(): void {
    this.detail.set(null);
    this.editing.set(false);
    this.formError.set('');
    this.confirm.set(null);
    this.removing.set(null);
    this.preview.set(null);
  }

  save(): void {
    if (!this.canEdit || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.formError.set('');
    const current = this.detail()?.record;
    const request = this.inventoryList()
      ? (current
        ? this.api.updateMasterReference(current.id, this.referenceBody())
        : this.api.createMasterReference(this.category(), this.referenceBody()))
      : this.category() === 'SPECIES'
      ? (current
        ? this.api.updateMasterSpecies(current.id, this.speciesBody())
        : this.api.createMasterSpecies(this.speciesBody()))
      : (current
        ? this.api.updateMasterBreed(current.id, this.breedBody())
        : this.api.createMasterBreed(this.breedBody()));
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.notice.set(current ? 'Record updated.' : 'Record added.');
        this.close();
        this.refreshCounts();
      },
      error: (err) => {
        this.saving.set(false);
        this.formError.set(err?.error?.message || 'The record was not saved.');
      }
    });
  }

  askStatus(row: MasterRecord): void {
    this.confirm.set({ id: row.id, name: row.name, active: row.status !== 'ACTIVE', usage: row.usage });
  }

  applyStatus(): void {
    const pending = this.confirm();
    if (!pending || this.saving()) {
      return;
    }
    this.saving.set(true);
    const call = this.inventoryList()
      ? this.api.masterReferenceStatus(pending.id, pending.active)
      : this.category() === 'SPECIES'
      ? this.api.masterSpeciesStatus(pending.id, pending.active)
      : this.api.masterBreedStatus(pending.id, pending.active);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.confirm.set(null);
        this.notice.set(pending.active ? `${pending.name} is active.` : `${pending.name} is inactive.`);
        this.close();
        this.refreshCounts();
      },
      error: (err) => {
        this.saving.set(false);
        this.formError.set(err?.error?.message || 'The status was not changed.');
      }
    });
  }

  askRemove(row: MasterRecord): void {
    this.removing.set(row);
  }

  applyRemove(): void {
    const row = this.removing();
    if (!row || this.saving()) {
      return;
    }
    this.saving.set(true);
    const call = this.inventoryList()
      ? this.api.deleteMasterReference(row.id)
      : this.category() === 'SPECIES' ? this.api.deleteMasterSpecies(row.id) : this.api.deleteMasterBreed(row.id);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.removing.set(null);
        this.notice.set(`${row.name} was removed.`);
        this.close();
        this.refreshCounts();
      },
      error: (err) => {
        this.saving.set(false);
        this.formError.set(err?.error?.message || 'That record was not removed.');
      }
    });
  }

  chooseFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file = input.files?.[0] || null;
    this.preview.set(null);
    if (this.file) {
      this.runImport(false);
    }
  }

  runImport(apply: boolean): void {
    if (!this.file || !this.canEdit || this.importing()) {
      return;
    }
    this.importing.set(true);
    this.formError.set('');
    const category = this.category() === 'BREEDS' ? 'breeds' : 'species';
    this.api.importMaster(category, this.file, apply, this.updateExisting()).subscribe({
      next: result => {
        this.importing.set(false);
        this.preview.set(result);
        if (apply) {
          this.notice.set(`${result.created} added, ${result.updated} updated, ${result.skipped} skipped, ${result.failed} failed.`);
          this.refreshCounts();
        }
      },
      error: (err) => {
        this.importing.set(false);
        this.formError.set(err?.error?.message || 'The file could not be read.');
      }
    });
  }

  downloadErrors(): void {
    const rows = this.preview()?.rows.filter(row => row.action === 'FAIL') || [];
    if (!rows.length) {
      return;
    }
    const body = ['line,code,name,message', ...rows.map(row => [row.line, row.code, row.name, row.message].map(value => `"${String(value).replaceAll('"', '""')}"`).join(','))].join('\n');
    this.saveBlob(new Blob([body], { type: 'text/csv' }), `${this.category().toLowerCase()}-import-errors.csv`);
  }

  export(format: 'csv' | 'xlsx'): void {
    const category = this.category().toLowerCase();
    this.api.exportMaster(category, format).subscribe({
      next: (response: HttpResponse<Blob>) => {
        const blob = response.body;
        if (!blob) {
          return;
        }
        this.saveBlob(blob, `${category}.${format}`);
      },
      error: () => this.formError.set('The file could not be exported.')
    });
  }

  when(value: string | null): string {
    if (!value) {
      return 'Not recorded';
    }
    return new Intl.DateTimeFormat('en-GB', {
      day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit', hour12: true,
      timeZone: 'Africa/Dar_es_Salaam'
    }).format(new Date(value));
  }

  source(row: MasterRecord): string {
    return row.origin === 'SYSTEM' ? 'System' : 'Added here';
  }

  private fill(row: MasterRecord): void {
    this.code.set(row.code);
    this.name.set(row.name);
    this.group.set(row.groupLabel);
    this.color.set(row.chartColor || '#1E8330');
    this.sortOrder.set(String(row.sortOrder ?? 0));
    this.tracking.set(row.individualTracking !== false);
    this.breedSpecies.set(row.parentId || '');
    this.note.set(row.detail || '');
    this.numericValue.set(row.numericValue == null ? '' : String(row.numericValue));
  }

  private referenceBody() {
    const number = this.numericValue().trim();
    return {
      code: this.code().trim(),
      name: this.name().trim(),
      parentId: this.category() === 'INV_TYPE' ? (this.breedSpecies() || null) : null,
      detail: this.note().trim() || null,
      numericValue: number === '' ? null : Number(number),
      sortOrder: Number(this.sortOrder()) || 0
    };
  }

  private speciesBody() {
    return {
      code: this.code().trim(),
      name: this.name().trim(),
      category: this.group().trim(),
      chartColor: this.color().trim(),
      sortOrder: Number(this.sortOrder()),
      individualTracking: this.tracking()
    };
  }

  private breedBody() {
    return { speciesId: this.breedSpecies(), code: this.code().trim(), name: this.name().trim() };
  }

  labelFor(value: string): string {
    const text = (value || '').toLowerCase();
    return text ? text.charAt(0).toUpperCase() + text.slice(1) : '';
  }

  swatch(): string {
    return /^#[0-9A-Fa-f]{6}$/.test(this.color()) ? this.color() : '#1E8330';
  }

  groupOptions(): string[] {
    const current = this.group();
    const stored = this.groups();
    return current && !stored.includes(current) ? [current, ...stored] : stored;
  }

  private loadGroups(): void {
    this.api.masterPage('SPECIES', { size: 50 }).subscribe({
      next: page => {
        const names = [...new Set(page.records.map(row => row.groupLabel).filter(name => !!name))].sort();
        if (names.includes('LIVESTOCK')) {
          names.splice(names.indexOf('LIVESTOCK'), 1);
          names.unshift('LIVESTOCK');
        }
        this.groups.set(names);
        if (this.editing() && !this.detail() && !this.group() && names.length) {
          this.group.set(names.includes('LIVESTOCK') ? 'LIVESTOCK' : names[0]);
        }
      },
      error: () => undefined
    });
  }

  private refreshCounts(): void {
    this.api.masterCategories().subscribe({
      next: rows => this.categories.set(rows),
      error: () => undefined
    });
    this.loadGroups();
    this.load();
  }

  private saveBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }
}
