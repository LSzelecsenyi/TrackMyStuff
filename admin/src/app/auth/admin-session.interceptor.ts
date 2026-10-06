import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AdminAuth } from './admin-auth';

export const adminSessionInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AdminAuth);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && isEstablishedAdminCall(request.url, request.method)) {
        if (!(request.method === 'GET' && request.url.includes('/api/v1/admin/session') && auth.phase() === 'checking')) {
          auth.sessionEnded();
        }
      }
      return throwError(() => error);
    }),
  );
};

function isEstablishedAdminCall(url: string, method: string): boolean {
  if (!url.includes('/api/v1/admin/')) {
    return false;
  }
  return !(method === 'POST' && url.includes('/api/v1/admin/session'));
}
