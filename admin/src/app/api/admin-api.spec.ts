import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { API_BASE_URL, adminPaths, adminUrl } from './admin-api';
import { AdminSessionClient } from './admin-session-client';

const adminRoot = process.cwd();

describe('Admin API configuration', () => {
  it('keeps the development server on port 4021 and proxies the local backend', () => {
    const angular = JSON.parse(readFileSync(resolve(adminRoot, 'angular.json'), 'utf8')) as {
      projects: { admin: { architect: { serve: { options: { port: number; proxyConfig: string } } } } };
    };
    const proxy = JSON.parse(readFileSync(resolve(adminRoot, 'proxy.conf.json'), 'utf8')) as {
      '/api': { target: string };
    };
    const production = readFileSync(resolve(adminRoot, 'src/environments/environment.production.ts'), 'utf8');
    const development = readFileSync(resolve(adminRoot, 'src/environments/environment.ts'), 'utf8');

    expect(angular.projects.admin.architect.serve.options.port).toBe(4021);
    expect(angular.projects.admin.architect.serve.options.proxyConfig).toBe('proxy.conf.json');
    expect(proxy['/api'].target).toBe('http://127.0.0.1:8082');
    expect(development).toContain("apiBaseUrl: '/api'");
    expect(production).toContain("apiBaseUrl: '/api'");
    expect(production).not.toContain('https://api.strictworkout.eu');
    expect(production).not.toContain('client_secret');
    expect(development).not.toContain('client_secret');
    expect(production).not.toContain('puff');
    expect(development).not.toContain('4200');
  });

  it('builds admin session URLs on the same-origin /api base', () => {
    expect(adminUrl('/api', adminPaths.session)).toBe('/api/v1/admin/session');
    expect(adminUrl('/api', adminPaths.csrf)).toBe('/api/v1/admin/csrf');
    expect(adminUrl('/api', adminPaths.founderApplications)).toBe('/api/v1/admin/founder/applications');
    expect(adminPaths.session).not.toContain('/api/v1/auth/');
  });

  it('posts a Google ID token without storing an admin credential', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' })),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    document.cookie = 'XSRF-TOKEN=csrf-value';
    const client = TestBed.inject(AdminSessionClient);
    const http = TestBed.inject(HttpTestingController);
    client.login('google-id-token').subscribe();
    const request = http.expectOne('/api/v1/admin/session');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ idToken: 'google-id-token' });
    expect(request.request.headers.get('Authorization')).toBeNull();
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-value');
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(localStorage.getItem('admin-token')).toBeNull();
    expect(localStorage.getItem('STRICT_ADMIN_SESSION')).toBeNull();
    expect(sessionStorage.getItem('STRICT_ADMIN_SESSION')).toBeNull();
    http.verify();
  });

  it('retries one CSRF failure and then stops', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api' }],
    });
    const client = TestBed.inject(AdminSessionClient);
    const http = TestBed.inject(HttpTestingController);
    let failed = false;
    client.logout().subscribe({
      error: () => {
        failed = true;
      },
    });
    http.expectOne((request) => request.method === 'DELETE').flush(null, { status: 403, statusText: 'Forbidden' });
    http.expectOne((request) => request.method === 'GET' && request.url.endsWith('/csrf')).flush(null, {
      status: 204,
      statusText: 'No Content',
    });
    http.expectOne((request) => request.method === 'DELETE').flush(null, { status: 403, statusText: 'Forbidden' });
    expect(failed).toBe(true);
    http.verify();
  });
});
