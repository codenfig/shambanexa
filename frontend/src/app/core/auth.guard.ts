import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.token()) {
    return router.createUrlTree(['/login']);
  }
  return auth.ensureFresh().pipe(map(session => {
    if (!session) {
      return router.createUrlTree(['/login']);
    }
    const needsSetup = !session.allFarms && session.farmIds.length === 0;
    const onSetup = state.url.startsWith('/farms/new');
    if (needsSetup && !onSetup) {
      return router.createUrlTree(['/farms/new']);
    }
    return true;
  }));
};

export const farmScopeGuard: CanActivateFn = route => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const session = auth.session();
  const id = route.paramMap.get('id');
  if (!session) {
    return router.createUrlTree(['/login']);
  }
  if (session.allFarms || session.accessScope === 'ALL_FARMS') {
    return true;
  }
  if (id && session.farmIds.includes(id)) {
    return true;
  }
  return router.createUrlTree(['/dashboard']);
};

export const accessGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.session()?.canManageAccess) {
    return true;
  }
  return router.createUrlTree(['/dashboard']);
};
