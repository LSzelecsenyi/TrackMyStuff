import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
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
    expect(development).toContain("apiBaseUrl: ''");
    expect(production).toContain('https://api.strictworkout.eu');
    expect(production).not.toContain('puff');
    expect(development).not.toContain('4200');
  });

  it('builds admin session URLs without using a member API path', () => {
    expect(adminUrl('', adminPaths.session)).toBe('/api/v1/admin/session');
    expect(adminUrl('https://api.strictworkout.eu', adminPaths.founderApplications)).toBe(
      'https://api.strictworkout.eu/api/v1/admin/founder/applications',
    );
    expect(adminPaths.session).not.toContain('/api/v1/auth/');
  });

  it('posts a Google ID token to the admin session and does not store it', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '' }],
    });
    const client = TestBed.inject(AdminSessionClient);
    const http = TestBed.inject(HttpTestingController);
    client.login('google-id-token').subscribe();
    const request = http.expectOne('/api/v1/admin/session');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ idToken: 'google-id-token' });
    request.flush({ accessToken: 'admin-token', tokenType: 'Bearer', expiresAt: '2026-10-05T00:00:00Z', admin: { id: '1' } });
    expect(localStorage.getItem('admin-token')).toBeNull();
    http.verify();
  });
});
