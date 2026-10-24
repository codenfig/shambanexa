import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { apiError } from '../../core/format';
import { AccessCatalog, AccessModule, AccessRole, Farm, UserAccess } from '../../core/models';

type Tab = 'permissions' | 'assigned' | 'activity';
type Show = 'all' | 'granted' | 'missing';
type GrantKey = 'view' | 'create' | 'edit' | 'delete' | 'approve' | 'export';

interface RolePolicy {
  canManageUsers: boolean;
  requireApproval: boolean;
}

interface RoleDraft {
  modules: AccessModule[];
  policy: RolePolicy;
}

@Component({
  selector: 'app-access',
  templateUrl: './access.component.html',
  styleUrl: './access.component.css'
})
export class AccessComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private toast = inject(ToastService);
  private readonly storageKey = 'shambanexa.access.matrix';

  catalog = signal<AccessCatalog | null>(null);
  people = signal<UserAccess[]>([]);
  scopeFarms = signal<Farm[]>([]);
  scopeError = signal('');
  scopeBusy = signal('');
  loading = signal(true);
  error = signal('');
  selectedCode = signal('');
  roleQuery = signal('');
  moduleQuery = signal('');
  moduleId = signal('');
  show = signal<Show>('all');
  tab = signal<Tab>('permissions');
  openModules = signal<Record<string, boolean>>({});
  notice = signal('');
  applied = signal<Record<string, RoleDraft>>({});
  pending = signal<Record<string, RoleDraft>>({});

  readonly actions: { key: GrantKey; label: string }[] = [
    { key: 'view', label: 'View' },
    { key: 'create', label: 'Create' },
    { key: 'edit', label: 'Edit' },
    { key: 'delete', label: 'Delete' },
    { key: 'approve', label: 'Approve' },
    { key: 'export', label: 'Export' }
  ];

  roles = computed(() => {
    const query = this.roleQuery().trim().toLowerCase();
    return (this.catalog()?.roles ?? []).filter(role =>
      !query || role.label.toLowerCase().includes(query) || role.scopeLabel.toLowerCase().includes(query)
    );
  });

  selected = computed(() => {
    const roles = this.catalog()?.roles ?? [];
    return roles.find(role => role.code === this.selectedCode()) ?? roles[0] ?? null;
  });

  policy = computed(() => {
    const role = this.selected();
    if (!role) {
      return { canManageUsers: false, requireApproval: false };
    }
    return this.view(role).policy;
  });

  dirty = computed(() => {
    const role = this.selected();
    const pending = role ? this.pending()[role.code] : undefined;
    return !!role && !!pending && !this.same(pending, this.baseline(role));
  });

  modules = computed(() => {
    const role = this.selected();
    if (!role) {
      return [];
    }
    const query = this.moduleQuery().trim().toLowerCase();
    const moduleId = this.moduleId();
    const show = this.show();
    return this.view(role).modules.filter(module => {
      if (moduleId && module.id !== moduleId) {
        return false;
      }
      if (query && !module.label.toLowerCase().includes(query)) {
        return false;
      }
      const granted = this.actions.some(action => module[action.key] === true);
      const missing = this.actions.some(action => module[action.key] === false);
      if (show === 'granted') {
        return granted;
      }
      if (show === 'missing') {
        return missing;
      }
      return true;
    });
  });

  constructor() {
    if (!this.auth.session()?.canManageAccess) {
      this.router.navigateByUrl('/dashboard');
      return;
    }
    this.route.fragment.subscribe(fragment => {
      if (fragment === 'activity' || fragment === 'assigned' || fragment === 'permissions') {
        this.tab.set(fragment);
      }
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.accessCatalog().subscribe({
      next: catalog => {
        this.catalog.set(catalog);
        this.applied.set(this.storedDrafts(catalog));
        this.pending.set({});
        if (!this.selectedCode() && catalog.roles[0]) {
          this.selectedCode.set(catalog.roles[0].code);
        }
        this.loading.set(false);
        this.api.accessUsers().subscribe(rows => this.people.set(rows));
        this.api.farms().subscribe(rows => this.scopeFarms.set(rows));
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Unable to load roles.');
      }
    });
  }

  selectRole(code: string): void {
    this.selectedCode.set(code);
    this.openModules.set({});
  }

  showTab(tab: Tab): void {
    this.tab.set(tab);
    this.router.navigate([], { fragment: tab, replaceUrl: true });
  }

  toggleModule(id: string): void {
    this.openModules.update(current => ({ ...current, [id]: !current[id] }));
  }

  open(id: string): boolean {
    return this.openModules()[id] === true;
  }

  peopleLabel(count: number): string {
    return count === 1 ? '1 user' : `${count} users`;
  }

  assignedLabel(count: number): string {
    return count === 1 ? '1 user assigned' : `${count} users assigned`;
  }

  when(value: string): string {
    return new Date(value).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' });
  }

  pretty(action: string): string {
    const text = action.toLowerCase().replaceAll('_', ' ');
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  setShow(value: string): void {
    if (value === 'granted' || value === 'missing' || value === 'all') {
      this.show.set(value);
    }
  }

  allChecked(): boolean {
    return this.selection() === 'all';
  }

  someChecked(): boolean {
    return this.selection() === 'some';
  }

  toggleGrant(moduleId: string, key: GrantKey): void {
    const role = this.selected();
    if (!role) {
      return;
    }
    const draft = this.copy(this.view(role));
    draft.modules = draft.modules.map(module => module.id === moduleId ? { ...module, [key]: !module[key] } : module);
    this.stage(role, draft);
  }

  toggleVisible(checked: boolean): void {
    const role = this.selected();
    if (!role) {
      return;
    }
    const visible = new Set(this.modules().map(module => module.id));
    const draft = this.copy(this.view(role));
    draft.modules = draft.modules.map(module => {
      if (!visible.has(module.id)) {
        return module;
      }
      const next = { ...module };
      for (const action of this.actions) {
        next[action.key] = checked;
      }
      return next;
    });
    this.stage(role, draft);
  }

  togglePolicy(key: keyof RolePolicy): void {
    const role = this.selected();
    if (!role) {
      return;
    }
    const draft = this.copy(this.view(role));
    draft.policy = { ...draft.policy, [key]: !draft.policy[key] };
    this.stage(role, draft);
  }

  save(): void {
    const role = this.selected();
    const draft = role ? this.pending()[role.code] : undefined;
    if (!role || !draft || !this.dirty()) {
      return;
    }
    const applied = { ...this.applied(), [role.code]: this.copy(draft) };
    this.applied.set(applied);
    this.pending.update(current => {
      const next = { ...current };
      delete next[role.code];
      return next;
    });
    this.persist(applied);
    this.toast.show('Permissions saved in this browser.');
  }

  reset(): void {
    this.discard();
  }

  cancel(): void {
    this.discard();
  }

  explain(kind: 'create' | 'edit' | 'duplicate', role?: AccessRole | null): void {
    if (kind === 'create') {
      this.notice.set('Super Admin, Farm Manager, Farm Adviser, and Auditor are the system roles. A new role is not stored.');
      return;
    }
    const name = role?.label ?? 'This role';
    this.notice.set(`${name} keeps its recorded name and farm scope. Check or clear permissions in the matrix, then save.`);
  }

  private discard(): void {
    const role = this.selected();
    if (!role) {
      return;
    }
    this.pending.update(current => {
      const next = { ...current };
      delete next[role.code];
      return next;
    });
  }

  private stage(role: AccessRole, draft: RoleDraft): void {
    if (this.same(draft, this.baseline(role))) {
      this.pending.update(current => {
        const next = { ...current };
        delete next[role.code];
        return next;
      });
      return;
    }
    this.pending.update(current => ({ ...current, [role.code]: draft }));
  }

  private view(role: AccessRole): RoleDraft {
    return this.pending()[role.code] ?? this.baseline(role);
  }

  private baseline(role: AccessRole): RoleDraft {
    return this.applied()[role.code] ?? this.fromRole(role);
  }

  private fromRole(role: AccessRole): RoleDraft {
    return {
      modules: role.modules.map(module => ({ ...module })),
      policy: { canManageUsers: role.canManageUsers, requireApproval: role.requireApproval }
    };
  }

  private copy(draft: RoleDraft): RoleDraft {
    return {
      modules: draft.modules.map(module => ({ ...module })),
      policy: { ...draft.policy }
    };
  }

  private same(left: RoleDraft, right: RoleDraft): boolean {
    if (left.policy.canManageUsers !== right.policy.canManageUsers || left.policy.requireApproval !== right.policy.requireApproval) {
      return false;
    }
    return left.modules.every(module => {
      const other = right.modules.find(item => item.id === module.id);
      return !!other && this.actions.every(action => module[action.key] === other[action.key]);
    });
  }

  private selection(): 'all' | 'some' | 'none' {
    const flags = this.modules().flatMap(module => this.actions.map(action => module[action.key] === true));
    const on = flags.filter(Boolean).length;
    if (flags.length === 0 || on === 0) {
      return 'none';
    }
    return on === flags.length ? 'all' : 'some';
  }

  private storedDrafts(catalog: AccessCatalog): Record<string, RoleDraft> {
    const raw = this.readStorage();
    const applied: Record<string, RoleDraft> = {};
    for (const role of catalog.roles) {
      const stored = raw[role.code];
      if (!stored) {
        continue;
      }
      const modules = role.modules.map(module => {
        const saved = stored.modules?.find(item => item.id === module.id);
        if (!saved) {
          return { ...module };
        }
        return {
          ...module,
          view: saved.view === true,
          create: saved.create === true,
          edit: saved.edit === true,
          delete: saved.delete === true,
          approve: saved.approve === true,
          export: saved.export === true
        };
      });
      const draft: RoleDraft = {
        modules,
        policy: {
          canManageUsers: stored.policy?.canManageUsers ?? role.canManageUsers,
          requireApproval: stored.policy?.requireApproval ?? role.requireApproval
        }
      };
      if (!this.same(draft, this.fromRole(role))) {
        applied[role.code] = draft;
      }
    }
    return applied;
  }

  private readStorage(): Record<string, RoleDraft> {
    try {
      const raw = localStorage.getItem(this.storageKey);
      if (!raw) {
        return {};
      }
      const parsed = JSON.parse(raw) as Record<string, RoleDraft>;
      return parsed && typeof parsed === 'object' ? parsed : {};
    } catch {
      return {};
    }
  }

  private persist(applied: Record<string, RoleDraft>): void {
    localStorage.setItem(this.storageKey, JSON.stringify(applied));
  }

  closeNotice(): void {
    this.notice.set('');
  }

  setRole(userId: string, roleCode: string): void {
    this.people.update(rows => rows.map(row => {
      if (row.userId !== userId) {
        return row;
      }
      const accessScope = roleCode === 'TENANT_ADMIN' ? 'ALL_FARMS' : row.accessScope === 'ALL_FARMS' ? 'OWN_FARM' : row.accessScope;
      return { ...row, roleCode, accessScope, farmIds: accessScope === 'ALL_FARMS' ? [] : row.farmIds.slice(0, accessScope === 'OWN_FARM' ? 1 : row.farmIds.length) };
    }));
  }

  setScope(userId: string, accessScope: UserAccess['accessScope']): void {
    this.people.update(rows => rows.map(row => {
      if (row.userId !== userId || row.roleCode === 'TENANT_ADMIN') {
        return row;
      }
      return { ...row, accessScope, farmIds: accessScope === 'ALL_FARMS' ? [] : row.farmIds.slice(0, accessScope === 'OWN_FARM' ? 1 : row.farmIds.length) };
    }));
  }

  toggleFarm(userId: string, farmId: string): void {
    this.people.update(rows => rows.map(row => {
      if (row.userId !== userId || row.accessScope === 'ALL_FARMS' || row.roleCode === 'TENANT_ADMIN') {
        return row;
      }
      const selected = row.farmIds.includes(farmId);
      if (row.accessScope === 'OWN_FARM') {
        return { ...row, farmIds: selected ? [] : [farmId] };
      }
      return { ...row, farmIds: selected ? row.farmIds.filter(id => id !== farmId) : [...row.farmIds, farmId] };
    }));
  }

  saveScope(person: UserAccess): void {
    this.scopeBusy.set(person.userId);
    this.scopeError.set('');
    this.api.assignAccess(person.userId, {
      roleCode: person.roleCode,
      accessScope: person.roleCode === 'TENANT_ADMIN' ? 'ALL_FARMS' : person.accessScope,
      farmIds: person.roleCode === 'TENANT_ADMIN' || person.accessScope === 'ALL_FARMS' ? [] : person.farmIds
    }).subscribe({
      next: () => {
        this.scopeBusy.set('');
        this.toast.show('Access scope saved and recorded in the audit log.');
        this.load();
        if (person.userId === this.auth.session()?.userId) {
          this.auth.ensureFresh(true).subscribe();
        }
      },
      error: err => {
        this.scopeBusy.set('');
        this.scopeError.set(apiError(err, 'The access scope was not saved'));
      }
    });
  }
}
