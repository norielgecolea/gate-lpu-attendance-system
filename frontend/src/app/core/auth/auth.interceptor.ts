import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const header = auth.getAuthorizationHeader();
  const authed =
    !!header && !req.url.includes('/auth/login') && !req.url.includes('/kiosk/time');
  const outgoing = authed ? req.clone({ setHeaders: { Authorization: header } }) : req;
  return next(outgoing).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && !req.url.includes('/auth/login')) {
        const message =
          err.error && typeof err.error === 'object' && 'message' in err.error
            ? String((err.error as { message?: string }).message ?? '')
            : '';
        if (err.status === 403 && message === 'Password change required') {
          auth.requirePasswordChange();
        }
      }
      return throwError(() => err);
    }),
  );
};
