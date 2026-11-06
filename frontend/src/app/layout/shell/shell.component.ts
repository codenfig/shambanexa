import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { filter } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { ApiService } from '../../core/api.service';
import { AlertItem, Farm, SearchHit, Species } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ADVISER_SECTIONS, NAV_SECTIONS, NavChildConfig, NavItemConfig, NavMatch, NavPermission, NavTarget } from './nav.config';

export interface NavChildView {
  id: string;
  label: string;
  commands: string[];
  query?: Record<string, string>;
  fragment?: string;
  active: boolean;
  species?: string;
  icon?: string;
}

export interface NavItemView {
  id: string;
  label: string;
  icon: string;
  commands: string[];
  query?: Record<string, string>;
  fragment?: string;
  active: boolean;
  open: boolean;
  badge: number;
  badgeTone: 'gold' | 'red' | '';
  children: NavChildView[];
}

export interface NavSectionView {
  id: string;
  label: string;
  icon: string;
  open: boolean;
  current: boolean;
  items: NavItemView[];
}

interface Crumb {
  label: string;
  link: string | null;
}

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, FormsModule],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css'
})
export class ShellComponent {
  private auth = inject(AuthService);
  private api = inject(ApiService);
  private router = inject(Router);
  toast = inject(ToastService);

  session = this.auth.session;
  query = '';
  hits = signal<SearchHit[]>([]);
  menuOpen = signal(false);
  alertsOpen = signal(false);
  quickOpen = signal(false);
  navOpen = signal(false);
  collapsed = signal(this.readStorage('shambanexa-sidebar') === '1');
  theme = signal<'light' | 'dark'>(this.readStorage('shambanexa-theme') === 'dark' ? 'dark' : 'light');
  private openIds = signal<Record<string, boolean>>({});
  private sectionOpen = signal<Record<string, boolean>>({});
  alertCount = signal(0);
  recentAlerts = signal<AlertItem[]>([]);
  farms = signal<Farm[]>([]);
  speciesList = signal<Species[]>([]);
  showFish = signal(false);
  openTaskCount = signal(0);
  apiConnected = signal(false);
  readonly menu = computed(() => this.buildMenu());
  upgradeOpen = signal(false);
  plan = signal('Plan');
  validUntil = signal('—');
  private path = signal(this.router.url);

  constructor() {
    this.applyTheme(this.theme());
    this.router.events.pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd)).subscribe(event => {
      const tree = this.router.parseUrl(this.router.url);
      const base = event.urlAfterRedirects.split('#')[0];
      this.path.set(tree.fragment ? `${base}#${tree.fragment}` : base);
      this.navOpen.set(false);
      this.menuOpen.set(false);
      this.alertsOpen.set(false);
      this.quickOpen.set(false);
      this.hits.set([]);
      this.expandForRoute();
    });
    this.api.dashboard().subscribe({
      next: data => {
        this.apiConnected.set(true);
        this.alertCount.set(data.openAlertCount);
        this.plan.set(data.subscription.plan);
        const date = new Date(data.subscription.validUntil);
        this.validUntil.set(date.toLocaleDateString('en-US', { month: 'short', year: 'numeric', timeZone: 'UTC' }));
      },
      error: () => this.apiConnected.set(false)
    });
    this.api.farms().subscribe({
      next: farms => {
        this.farms.set(farms);
        this.expandForRoute();
      },
      error: () => this.farms.set([])
    });
    this.api.species().subscribe({
      next: species => {
        this.speciesList.set(species);
        this.expandForRoute();
      },
      error: () => this.speciesList.set([])
    });
    this.api.fishCycles().subscribe({ next: cycles => this.showFish.set(cycles.length > 0), error: () => this.showFish.set(false) });
    this.api.tasks().subscribe({
      next: tasks => this.openTaskCount.set(tasks.filter(task => task.status === 'OPEN').length),
      error: () => this.openTaskCount.set(0)
    });
    this.api.alerts().subscribe({
      next: alerts => {
        this.recentAlerts.set(alerts.slice(0, 4));
        this.expandForRoute();
      }
    });
    this.expandForRoute();
  }

  initial(): string {
    return (this.session()?.fullName || this.session()?.jobTitle || 'A').charAt(0).toUpperCase();
  }

  topCrumbs(): Crumb[] {
    const path = this.path().split(/[?#]/)[0];
    if (path === '/dashboard' || path === '/') {
      const activity = this.path().includes('#activity');
      const adviser = this.session()?.canAdvise === true && !activity;
      return [
        { label: 'Dashboard', link: '/dashboard' },
        { label: activity ? 'Recent Activity' : adviser ? 'Adviser Overview' : 'Overview', link: null }
      ];
    }
    return this.crumbs();
  }

  menuLabel(): string {
    if (window.matchMedia('(max-width: 1100px)').matches) {
      return this.navOpen() ? 'Close menu' : 'Open menu';
    }
    return this.collapsed() ? 'Expand sidebar' : 'Collapse sidebar';
  }

  toggleMenu(event: Event): void {
    event.stopPropagation();
    if (window.matchMedia('(max-width: 1100px)').matches) {
      this.navOpen.update(open => !open);
      return;
    }
    this.toggleCollapsed();
  }

  crumbs(): Crumb[] {
    const path = this.path().split('?')[0];
    const parts = path.split('/').filter(Boolean);
    const names: Record<string, string> = {
      dashboard: 'Dashboard',
      farms: 'Farms',
      livestock: 'Livestock',
      production: 'Production',
      analytics: 'Analytics',
      inventory: 'Inventory',
      staff: 'Staff',
      alerts: 'Alerts',
      advisers: 'Farm Advisers',
      health: 'Animal health',
      suppliers: 'Medical Suppliers',
      market: 'Agricultural Marketplace',
      settings: 'Settings',
      help: 'Help',
      access: 'Roles & Permissions'
    };
    const root = parts[0] || 'dashboard';
    const section = this.sectionCrumb(root);
    const first: Crumb = { label: names[root] || root, link: `/${root === 'dashboard' ? 'dashboard' : root}` };
    if (parts.length <= 1) {
      return [section, { label: first.label, link: null }];
    }
    if (root === 'farms' && parts[1] === 'new') {
      return [section, first, { label: 'Add farm', link: null }];
    }
    if (root === 'farms' && parts[2] === 'edit') {
      return [section, first, { label: 'Edit farm', link: null }];
    }
    if (root === 'farms') {
      return [section, first, { label: 'Farm details', link: null }];
    }
    if (root === 'livestock' && parts[1] === 'new') {
      return [section, first, { label: 'Register animal', link: null }];
    }
    if (root === 'livestock' && parts[1]) {
      const labels: Record<string, string> = {
        registry: 'Animal registry',
        herds: 'Herd batches',
        health: 'Health records',
        transfers: 'Transfer history',
        species: decodeURIComponent(parts[2] || 'Species')
      };
      return [section, first, { label: parts[1] === 'species' ? labels['species'] : (labels[parts[1]] || 'Animal'), link: null }];
    }
    if (root === 'staff' && parts[1]) {
      const labels: Record<string, string> = {
        directory: 'Staff directory',
        assignments: 'Assignments',
        attendance: 'Attendance & shifts',
        performance: 'Performance & training'
      };
      return [section, first, { label: labels[parts[1]] || 'Staff profile', link: null }];
    }
    if (root === 'alerts' && parts[1]) {
      const pages: Record<string, string> = {
        history: 'Alert History',
        rules: 'Alert Rules',
        report: 'Report health'
      };
      return [section, first, { label: pages[parts[1]] || parts[1], link: null }];
    }
    if (root === 'inventory' && parts[1]) {
      const pages: Record<string, string> = {
        receipts: 'Stock Receipts',
        issues: 'Stock Issues',
        adjustments: 'Adjustments',
        transfers: 'Transfers',
        suppliers: 'Suppliers',
        low: 'Low-stock Alerts',
        reports: 'Reports'
      };
      return [section, first, { label: pages[parts[1]] || parts[1], link: null }];
    }
    return [section, first, { label: parts[parts.length - 1], link: null }];
  }

  toggleItem(id: string, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.openIds.update(current => ({ ...current, [id]: !current[id] }));
  }

  toggleSection(id: string, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.sectionOpen.update(current => ({ ...current, [id]: !current[id] }));
  }

  onNavKey(event: KeyboardEvent): void {
    const nav = event.currentTarget as HTMLElement;
    const items = [...nav.querySelectorAll<HTMLElement>('a, button.branch-chev, button.section-toggle')].filter(el => el.offsetParent !== null);
    const current = document.activeElement as HTMLElement | null;
    const index = current ? items.indexOf(current) : -1;
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      if (!items.length) {
        return;
      }
      event.preventDefault();
      const step = event.key === 'ArrowDown' ? 1 : -1;
      const next = index < 0 ? 0 : (index + step + items.length) % items.length;
      items[next]?.focus();
      return;
    }
    if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault();
      items[event.key === 'Home' ? 0 : items.length - 1]?.focus();
      return;
    }
    if (!current) {
      return;
    }
    if (current.classList.contains('section-toggle')) {
      const bodyId = current.getAttribute('aria-controls') || '';
      if (event.key === 'ArrowRight') {
        event.preventDefault();
        if (current.getAttribute('aria-expanded') !== 'true') {
          current.click();
        }
        window.setTimeout(() => document.getElementById(bodyId)?.querySelector<HTMLElement>('a')?.focus(), 0);
      }
      if (event.key === 'ArrowLeft' && current.getAttribute('aria-expanded') === 'true') {
        event.preventDefault();
        current.click();
      }
      return;
    }
    const branch = current.closest('.branch');
    if (event.key === 'ArrowRight' && branch) {
      const chevron = branch.querySelector<HTMLButtonElement>('button.branch-chev');
      if (!chevron) {
        return;
      }
      event.preventDefault();
      if (chevron.getAttribute('aria-expanded') !== 'true') {
        chevron.click();
      }
      const submenu = branch.nextElementSibling;
      window.setTimeout(() => submenu?.querySelector<HTMLElement>('a')?.focus(), 0);
    }
    if (event.key === 'ArrowLeft') {
      const submenu = current.closest('.subnav');
      if (submenu) {
        event.preventDefault();
        submenu.previousElementSibling?.querySelector<HTMLElement>('a')?.focus();
        return;
      }
      const chevron = branch?.querySelector<HTMLButtonElement>('button.branch-chev');
      if (chevron?.getAttribute('aria-expanded') === 'true') {
        event.preventDefault();
        chevron.click();
        return;
      }
      current.closest('.nav-group')?.querySelector<HTMLElement>('button.section-toggle')?.focus();
    }
  }

  currentFarm(): Farm | null {
    const match = this.path().split('?')[0].match(/^\/farms\/([^/]+)/);
    if (!match || match[1] === 'new') {
      return null;
    }
    return this.farms().find(farm => farm.id === match[1]) ?? null;
  }

  toggleCollapsed(): void {
    this.collapsed.update(value => !value);
    this.writeStorage('shambanexa-sidebar', this.collapsed() ? '1' : '0');
  }

  toggleTheme(): void {
    const next = this.theme() === 'dark' ? 'light' : 'dark';
    this.theme.set(next);
    this.writeStorage('shambanexa-theme', next);
    this.applyTheme(next);
  }

  toggleQuick(event: Event): void {
    event.stopPropagation();
    this.quickOpen.update(open => !open);
    this.menuOpen.set(false);
    this.alertsOpen.set(false);
  }

  toggleNav(event: Event): void {
    event.stopPropagation();
    this.navOpen.update(open => !open);
  }

  search(): void {
    const q = this.query.trim();
    if (q.length < 2) {
      this.hits.set([]);
      return;
    }
    this.api.search(q).subscribe(hits => this.hits.set(hits));
  }

  openHit(hit: SearchHit): void {
    this.hits.set([]);
    this.query = '';
    if (hit.type === 'Farm') {
      this.router.navigate(['/farms', hit.id]);
    } else if (hit.type === 'Animal') {
      this.router.navigate(['/livestock'], { queryParams: { q: hit.subtitle } });
    } else if (hit.type === 'Inventory') {
      this.router.navigate(['/inventory']);
    } else {
      this.router.navigate(['/staff']);
    }
  }

  logout(): void {
    this.auth.logout();
  }

  @HostListener('document:click')
  closeFloating(): void {
    this.menuOpen.set(false);
    this.alertsOpen.set(false);
    this.quickOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  closeOnEscape(): void {
    this.menuOpen.set(false);
    this.alertsOpen.set(false);
    this.quickOpen.set(false);
    this.navOpen.set(false);
    this.upgradeOpen.set(false);
  }

  private buildMenu(): NavSectionView[] {
    const sections = this.session()?.canAdvise ? ADVISER_SECTIONS : NAV_SECTIONS;
    return sections.map(section => {
      const items = section.items.filter(item => this.allowed(item.permission)).map(item => this.toItem(item));
      return {
        id: section.id,
        label: section.label,
        icon: section.icon,
        open: this.sectionOpen()[section.id] === true,
        current: items.some(item => item.active || item.children.some(child => child.active)),
        items
      };
    }).filter(section => section.items.length > 0);
  }

  private toItem(item: NavItemConfig): NavItemView {
    const target = item.id === 'facilities' ? this.facilitiesTarget() : item.target;
    return {
      id: item.id,
      label: item.label,
      icon: item.icon,
      commands: target.commands,
      query: target.query,
      fragment: target.fragment,
      active: this.matches(item.match),
      open: this.openIds()[item.id] === true,
      badge: this.badgeCount(item.badge),
      badgeTone: item.badge === 'alerts' || (this.session()?.canAdvise && item.badge === 'tasks') ? 'red' : item.badge === 'tasks' ? 'gold' : '',
      children: this.childViews(item)
    };
  }

  private childViews(item: NavItemConfig): NavChildView[] {
    const configured = (item.children ?? []).filter(child => this.allowed(child.permission)).map(child => this.toChild(child));
    const dynamic = this.dynamicChildren(item);
    return item.dynamic === 'species' ? [...dynamic, ...configured] : [...dynamic, ...configured];
  }

  private dynamicChildren(item: NavItemConfig): NavChildView[] {
    if (item.dynamic === 'species') {
      const names = this.speciesList().map(row => row.name);
      if (this.showFish() && !names.some(name => name.toLowerCase() === 'fish')) {
        names.push('Fish');
      }
      return names.map(name => ({
        id: `species-${name.toLowerCase()}`,
        label: name,
        species: name,
        commands: ['/livestock', 'species', name],
        active: this.matches({ path: `/livestock/species/${name}`, exact: true })
      }));
    }
    if (item.dynamic === 'facilities') {
      return this.farms().map(farm => ({
        id: `facility-${farm.id}`,
        label: farm.name,
        commands: ['/farms', farm.id],
        query: { tab: 'facilities' },
        active: this.matches({ path: `/farms/${farm.id}`, exact: true, query: { tab: 'facilities' } })
      }));
    }
    if (item.dynamic === 'alerts') {
      return this.recentAlerts().map(alert => ({
        id: `alert-${alert.id}`,
        label: alert.title,
        commands: ['/alerts'],
        active: false
      }));
    }
    return [];
  }

  branchOn(item: NavItemView): boolean {
    return item.active || item.children.some(child => child.active);
  }

  private toChild(child: NavChildConfig): NavChildView {
    return {
      id: child.id,
      label: child.label,
      commands: child.target.commands,
      query: child.target.query,
      fragment: child.target.fragment,
      species: child.species,
      icon: child.icon,
      active: this.matches(child.match)
    };
  }

  private facilitiesTarget(): NavTarget {
    const farm = this.currentFarm();
    if (farm) {
      return { commands: ['/farms', farm.id], query: { tab: 'facilities' } };
    }
    return { commands: ['/farms'], query: { view: 'facilities' } };
  }

  private badgeCount(badge?: NavItemConfig['badge']): number {
    if (badge === 'alerts') {
      return this.alertCount();
    }
    if (badge === 'tasks') {
      return this.openTaskCount();
    }
    return 0;
  }

  private allowed(permission?: NavPermission): boolean {
    if (permission === 'allFarms') {
      return this.session()?.allFarms === true;
    }
    if (permission === 'manageAccess') {
      return this.session()?.canManageAccess === true;
    }
    if (permission === 'advise') {
      return this.session()?.canAdvise === true;
    }
    return true;
  }

  private matches(match?: NavMatch): boolean {
    if (!match) {
      return false;
    }
    if (match.any?.length) {
      return match.any.some(item => this.matches(item));
    }
    const raw = this.path();
    const hash = raw.includes('#') ? decodeURIComponent(raw.slice(raw.indexOf('#') + 1)) : '';
    const before = raw.split('#')[0];
    const path = before.split('?')[0];
    const query = new URLSearchParams(before.split('?')[1] || '');
    if (!match.path) {
      return false;
    }
    const pathOk = match.exact ? path === match.path : path === match.path || path.startsWith(`${match.path}/`);
    if (!pathOk) {
      return false;
    }
    if (match.fragment) {
      if (hash !== match.fragment) {
        return false;
      }
    } else if (match.exact && hash) {
      return false;
    }
    if (match.query) {
      for (const [key, value] of Object.entries(match.query)) {
        if ((query.get(key) || '').toLowerCase() !== value.toLowerCase()) {
          return false;
        }
      }
    }
    return !match.withoutQuery?.some(key => query.get(key));
  }

  private expandForRoute(): void {
    const sections = this.menu().filter(section => section.current);
    const ids = sections.flatMap(section => section.items.filter(item => item.active || item.children.some(child => child.active)).map(item => item.id));
    if (!sections.length) {
      return;
    }
    this.openIds.update(current => {
      const next = { ...current };
      ids.forEach(id => next[id] = true);
      return next;
    });
    this.sectionOpen.update(current => {
      const next = { ...current };
      sections.forEach(section => next[section.id] = true);
      return next;
    });
  }

  private sectionCrumb(root: string): Crumb {
    if (root === 'analytics' || root === 'alerts') {
      return { label: 'Insight', link: '/analytics' };
    }
    if (root === 'settings' || root === 'help' || root === 'access') {
      return { label: 'System', link: '/settings' };
    }
    return { label: 'Workspace', link: '/dashboard' };
  }

  private applyTheme(theme: 'light' | 'dark'): void {
    if (theme === 'dark') {
      document.documentElement.dataset['theme'] = 'dark';
    } else {
      delete document.documentElement.dataset['theme'];
    }
  }

  private readStorage(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }

  private writeStorage(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch {
      /* preference storage is optional */
    }
  }
}
