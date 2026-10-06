import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, catchError, map, switchMap, throwError } from 'rxjs';
import { API_BASE_URL, adminPaths, adminUrl } from './admin-api';

export interface AdminSessionView {
  email: string | null;
}

/**
 * Browser admin session.
 * The raw session credential stays in the HttpOnly STRICT_ADMIN_SESSION cookie.
 * This client never reads that cookie and never stores a bearer token.
 */
@Injectable({ providedIn: 'root' })
export class AdminSessionClient {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  ensureCsrf(): Observable<void> {
    return this.http.get(this.url(adminPaths.csrf), { responseType: 'text' }).pipe(map(() => undefined));
  }

  current(): Observable<AdminSessionView> {
    return this.http.get<AdminSessionView>(this.url(adminPaths.session));
  }

  login(idToken: string): Observable<void> {
    return this.changing(() =>
      this.http.post(this.url(adminPaths.session), { idToken }, { responseType: 'text' }),
    );
  }

  logout(): Observable<void> {
    return this.changing(() => this.http.delete(this.url(adminPaths.session), { responseType: 'text' }));
  }

  private changing(request: () => Observable<string>): Observable<void> {
    return this.sendChanging(request, false);
  }

  private sendChanging(request: () => Observable<string>, retried: boolean): Observable<void> {
    return request().pipe(
      map(() => undefined),
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 403 && !retried) {
          return this.ensureCsrf().pipe(switchMap(() => this.sendChanging(request, true)));
        }
        return throwError(() => error);
      }),
    );
  }

  private url(path: string): string {
    return adminUrl(this.apiBaseUrl, path);
  }
}
