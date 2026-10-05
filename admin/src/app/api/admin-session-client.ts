import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, adminPaths, adminUrl } from './admin-api';

export interface AdminSessionResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  admin: { id: string };
}

/**
 * Contract for the existing admin session.
 * POST exchanges a Google ID token for an admin bearer.
 * A Strict user bearer is a different credential and is not sent here.
 * This foundation does not collect or store either token.
 */
@Injectable({ providedIn: 'root' })
export class AdminSessionClient {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  login(idToken: string): Observable<AdminSessionResponse> {
    return this.http.post<AdminSessionResponse>(this.url(adminPaths.session), { idToken });
  }

  logout(): Observable<void> {
    return this.http.delete<void>(this.url(adminPaths.session));
  }

  private url(path: string): string {
    return adminUrl(this.apiBaseUrl, path);
  }
}
