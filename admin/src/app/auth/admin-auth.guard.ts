import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AdminAuth } from './admin-auth';

export const adminAuthGuard: CanActivateFn = () => {
  const auth = inject(AdminAuth);
  const router = inject(Router);
  return auth.whenReady().pipe(
    map((phase) => (phase === 'authenticated' ? true : router.createUrlTree(['/login']))),
  );
};

export const adminGuestGuard: CanActivateFn = () => {
  const auth = inject(AdminAuth);
  const router = inject(Router);
  return auth.whenReady().pipe(
    map((phase) => (phase === 'authenticated' ? router.createUrlTree(['/founders']) : true)),
  );
};
