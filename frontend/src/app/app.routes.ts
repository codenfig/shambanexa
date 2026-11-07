import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { accessGuard, authGuard, farmScopeGuard } from './core/auth.guard';

const legacyAlertsGuard: CanActivateFn = (_, state) => {
  const fragment = state.root.fragment;
  const path = state.url.split(/[?#]/)[0];
  if (path !== '/alerts' || !fragment || fragment === 'active') {
    return true;
  }
  const pages: Record<string, string> = {
    history: '/alerts/history',
    rules: '/alerts/rules',
    report: '/alerts/report'
  };
  const next = pages[fragment];
  return next ? inject(Router).parseUrl(next) : true;
};

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'register',
    loadComponent: () => import('./pages/register/register.component').then(m => m.RegisterComponent)
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell/shell.component').then(m => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent) },
      { path: 'farms', loadComponent: () => import('./pages/farms/farms.component').then(m => m.FarmsComponent) },
      { path: 'farms/new', loadComponent: () => import('./pages/farm-form/farm-form.component').then(m => m.FarmFormComponent) },
      { path: 'farms/:id/edit', canActivate: [farmScopeGuard], loadComponent: () => import('./pages/farm-form/farm-form.component').then(m => m.FarmFormComponent) },
      { path: 'farms/:id', canActivate: [farmScopeGuard], loadComponent: () => import('./pages/farm-detail/farm-detail.component').then(m => m.FarmDetailComponent) },
      { path: 'livestock', loadComponent: () => import('./pages/livestock/livestock.component').then(m => m.LivestockComponent) },
      { path: 'livestock/new', loadComponent: () => import('./pages/animal-form/animal-form.component').then(m => m.AnimalFormComponent) },
      { path: 'livestock/registry', loadComponent: () => import('./pages/livestock/livestock.component').then(m => m.LivestockComponent) },
      { path: 'livestock/herds', loadComponent: () => import('./pages/livestock/livestock.component').then(m => m.LivestockComponent) },
      { path: 'livestock/health', loadComponent: () => import('./pages/livestock/livestock.component').then(m => m.LivestockComponent) },
      { path: 'health', loadComponent: () => import('./pages/health/health-center.component').then(m => m.HealthCenterComponent) },

      { path: 'livestock/transfers', loadComponent: () => import('./pages/movements/movements.component').then(m => m.MovementsComponent) },
      { path: 'livestock/species/:name', loadComponent: () => import('./pages/livestock/livestock.component').then(m => m.LivestockComponent) },
      { path: 'livestock/:id', loadComponent: () => import('./pages/livestock/livestock.component').then(m => m.LivestockComponent) },
      { path: 'production', loadComponent: () => import('./pages/production/production.component').then(m => m.ProductionComponent) },
      { path: 'analytics', loadComponent: () => import('./pages/analytics/analytics.component').then(m => m.AnalyticsComponent) },
      { path: 'inventory', loadComponent: () => import('./pages/inventory/inventory.component').then(m => m.InventoryComponent) },
      { path: 'inventory/receipts', loadComponent: () => import('./pages/inventory/receipts.component').then(m => m.StockReceiptsComponent) },
      { path: 'inventory/issues', loadComponent: () => import('./pages/inventory/issues.component').then(m => m.StockIssuesComponent) },
      { path: 'inventory/adjustments', loadComponent: () => import('./pages/inventory/adjustments.component').then(m => m.StockAdjustmentsComponent) },
      { path: 'inventory/transfers', loadComponent: () => import('./pages/inventory/transfers.component').then(m => m.StockTransfersComponent) },
      { path: 'inventory/suppliers', loadComponent: () => import('./pages/inventory/suppliers.component').then(m => m.StockSuppliersComponent) },
      { path: 'inventory/low', loadComponent: () => import('./pages/inventory/low-stock.component').then(m => m.LowStockComponent) },
      { path: 'inventory/reports', loadComponent: () => import('./pages/inventory/reports.component').then(m => m.StockReportsComponent) },
      { path: 'staff', loadComponent: () => import('./pages/staff/staff.component').then(m => m.StaffComponent) },
      { path: 'staff/directory', loadComponent: () => import('./pages/staff/staff.component').then(m => m.StaffComponent) },
      { path: 'staff/assignments', loadComponent: () => import('./pages/staff/staff.component').then(m => m.StaffComponent) },
      { path: 'staff/attendance', loadComponent: () => import('./pages/staff/staff.component').then(m => m.StaffComponent) },
      { path: 'staff/performance', loadComponent: () => import('./pages/staff/staff.component').then(m => m.StaffComponent) },
      { path: 'staff/:id', loadComponent: () => import('./pages/staff/staff.component').then(m => m.StaffComponent) },
      { path: 'alerts/history', canActivate: [legacyAlertsGuard], loadComponent: () => import('./pages/alerts/alerts.component').then(m => m.AlertsComponent) },
      { path: 'alerts/rules', canActivate: [legacyAlertsGuard], loadComponent: () => import('./pages/alerts/alerts.component').then(m => m.AlertsComponent) },
      { path: 'alerts/report', canActivate: [legacyAlertsGuard], loadComponent: () => import('./pages/alerts/alerts.component').then(m => m.AlertsComponent) },
      { path: 'alerts', canActivate: [legacyAlertsGuard], loadComponent: () => import('./pages/alerts/alerts.component').then(m => m.AlertsComponent) },
      { path: 'advice', loadComponent: () => import('./pages/advice/advice.component').then(m => m.AdviceComponent) },
      { path: 'requests', loadComponent: () => import('./pages/requests/requests.component').then(m => m.RequestsComponent) },
      { path: 'advisers', loadComponent: () => import('./pages/advisers/advisers.component').then(m => m.AdvisersComponent) },
      { path: 'suppliers', loadComponent: () => import('./pages/suppliers/suppliers.component').then(m => m.SuppliersComponent) },
      { path: 'market', loadComponent: () => import('./pages/market/market.component').then(m => m.MarketComponent) },
      { path: 'access', canActivate: [accessGuard], loadComponent: () => import('./pages/access/access.component').then(m => m.AccessComponent) },
      { path: 'account', loadComponent: () => import('./pages/account/account.component').then(m => m.AccountComponent) },
      { path: 'settings', loadComponent: () => import('./pages/settings/settings.component').then(m => m.SettingsComponent) },
      { path: 'help', loadComponent: () => import('./pages/help/help.component').then(m => m.HelpComponent) }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];
