import { InjectionToken } from '@angular/core';
import { environment } from '../../environments/environment';

export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => environment.apiBaseUrl,
});

export const GOOGLE_CLIENT_ID = new InjectionToken<string>('GOOGLE_CLIENT_ID', {
  providedIn: 'root',
  factory: () => environment.googleClientId,
});

export const adminPaths = {
  csrf: '/v1/admin/csrf',
  session: '/v1/admin/session',
  founderApplications: '/v1/admin/founder/applications',
} as const;

export function adminUrl(base: string, path: string): string {
  const trimmed = base.endsWith('/') ? base.slice(0, -1) : base;
  return `${trimmed}${path}`;
}
