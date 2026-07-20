import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const superAdminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated() && auth.isSuperAdmin()) {
    return true;
  }
  if (auth.isAuthenticated() && auth.isGuard()) {
    return router.createUrlTree(['/guard']);
  }
  if (auth.isAuthenticated() && auth.isMonitoring()) {
    return router.createUrlTree(['/monitor']);
  }
  return router.createUrlTree(['/']);
};

export const guardRoleGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated() && auth.isGuard()) {
    return true;
  }
  if (auth.isAuthenticated() && auth.isSuperAdmin()) {
    return router.createUrlTree(['/dashboard']);
  }
  if (auth.isAuthenticated() && auth.isMonitoring()) {
    return router.createUrlTree(['/monitor']);
  }
  return router.createUrlTree(['/']);
};

/** The monitoring wall display; superadmins may open it too. */
export const monitoringGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated() && (auth.isMonitoring() || auth.isSuperAdmin())) {
    return true;
  }
  if (auth.isAuthenticated() && auth.isGuard()) {
    return router.createUrlTree(['/guard']);
  }
  return router.createUrlTree(['/']);
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated() && auth.isSuperAdmin()) {
    return router.createUrlTree(['/dashboard']);
  }
  if (auth.isAuthenticated() && auth.isGuard()) {
    return router.createUrlTree(['/guard']);
  }
  if (auth.isAuthenticated() && auth.isMonitoring()) {
    return router.createUrlTree(['/monitor']);
  }
  return true;
};

/** @deprecated use superAdminGuard */
export const authGuard = superAdminGuard;
