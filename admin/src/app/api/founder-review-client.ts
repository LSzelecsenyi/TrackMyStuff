import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, catchError, switchMap, throwError } from 'rxjs';
import { FounderReviewDetail, FounderReviewSummary } from '../founders/founder-review';
import { API_BASE_URL, adminPaths, adminUrl } from './admin-api';
import { AdminSessionClient } from './admin-session-client';

/**
 * Founder review API.
 * The browser attaches the HttpOnly admin cookie. This client does not send a bearer token.
 * State-changing calls rely on Angular's XSRF header and retry one stale CSRF token.
 * The request body never chooses a status, reviewer, user, or entitlement.
 */
@Injectable({ providedIn: 'root' })
export class FounderReviewClient {
  private readonly http = inject(HttpClient);
  private readonly sessions = inject(AdminSessionClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  listFounderApplications(): Observable<FounderReviewSummary[]> {
    return this.http.get<FounderReviewSummary[]>(this.collection());
  }

  getFounderApplication(applicationId: string): Observable<FounderReviewDetail> {
    return this.http.get<FounderReviewDetail>(this.application(applicationId));
  }

  approveFounderApplication(applicationId: string): Observable<FounderReviewDetail> {
    return this.changing(() => this.http.post<FounderReviewDetail>(`${this.application(applicationId)}/approval`, {}));
  }

  rejectFounderApplication(applicationId: string, reason: string): Observable<FounderReviewDetail> {
    return this.changing(() =>
      this.http.post<FounderReviewDetail>(`${this.application(applicationId)}/rejection`, { reason }),
    );
  }

  private changing<T>(request: () => Observable<T>, retried = false): Observable<T> {
    return request().pipe(
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 403 && !retried) {
          return this.sessions.ensureCsrf().pipe(switchMap(() => this.changing(request, true)));
        }
        return throwError(() => error);
      }),
    );
  }

  private application(applicationId: string): string {
    return `${this.collection()}/${encodeURIComponent(applicationId)}`;
  }

  private collection(): string {
    return adminUrl(this.apiBaseUrl, adminPaths.founderApplications);
  }
}
