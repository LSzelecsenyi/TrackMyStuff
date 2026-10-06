import { HttpClient, provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { App } from '../app';
import { routes } from '../app.routes';
import { ADMIN_SIGN_IN_DENIED, AdminAuth } from './admin-auth';
import { adminSessionInterceptor } from './admin-session.interceptor';
import { flushAdminBootstrap, flushFounderList } from './admin-testing';
import { GoogleSignIn } from './google-sign-in';

class FakeGoogleSignIn {
  calls = 0;
  token = 'google-id-token';
  failure: Error | null = null;

  requestIdToken(): Observable<string> {
    this.calls += 1;
    return this.failure ? throwError(() => this.failure) : of(this.token);
  }
}

describe('Admin authentication', () => {
  let http: HttpTestingController;
  let google: FakeGoogleSignIn;
  let fixture: ComponentFixture<App>;

  beforeEach(async () => {
    localStorage.clear();
    sessionStorage.clear();
    google = new FakeGoogleSignIn();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        provideLocationMocks(),
        provideHttpClient(
          withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
          withInterceptors([adminSessionInterceptor]),
        ),
        provideHttpClientTesting(),
        { provide: GoogleSignIn, useValue: google },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(App);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('bootstraps CSRF and restores a valid session without Google', async () => {
    flushAdminBootstrap(http, 200, 'ada@example.com');
    await fixture.whenStable();
    fixture.detectChanges();
    const auth = TestBed.inject(AdminAuth);
    expect(auth.phase()).toBe('authenticated');
    expect(auth.account()).toEqual({ email: 'ada@example.com' });
    expect(google.calls).toBe(0);
    expect(localStorage.getItem('STRICT_ADMIN_SESSION')).toBeNull();
    expect(sessionStorage.length).toBe(0);
  });

  it('starts unauthenticated when the session endpoint returns 401', async () => {
    flushAdminBootstrap(http, 401);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/founders');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(router.url).toBe('/login');
    expect(fixture.nativeElement.textContent).toContain('Restricted access');
    expect(fixture.nativeElement.textContent).not.toContain('No applications loaded');
    expect(google.calls).toBe(0);
  });

  it('signs in through the backend and does not keep the Google token', async () => {
    flushAdminBootstrap(http, 401);
    await fixture.whenStable();
    const auth = TestBed.inject(AdminAuth);
    auth.signIn().subscribe();
    const login = http.expectOne((request) => request.method === 'POST' && request.url === '/api/v1/admin/session');
    expect(login.request.body).toEqual({ idToken: 'google-id-token' });
    expect(login.request.headers.get('Authorization')).toBeNull();
    login.flush(null, { status: 204, statusText: 'No Content' });
    const current = http.expectOne((request) => request.method === 'GET' && request.url === '/api/v1/admin/session');
    current.flush({ email: 'ada@example.com' });
    await fixture.whenStable();
    expect(auth.phase()).toBe('authenticated');
    expect(auth.account()?.email).toBe('ada@example.com');
    expect(JSON.stringify(localStorage)).not.toContain('google-id-token');
    expect(localStorage.getItem('isAdmin')).toBeNull();
  });

  it('shows a generic message when sign-in is rejected', async () => {
    flushAdminBootstrap(http, 401);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/login');
    await fixture.whenStable();
    fixture.detectChanges();
    const auth = TestBed.inject(AdminAuth);
    auth.signIn().subscribe();
    http
      .expectOne((request) => request.method === 'POST')
      .flush({ errorCode: 'ADMIN_NOT_ALLOWED', message: 'allowlist' }, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(auth.phase()).toBe('error');
    expect(auth.errorMessage()).toBe(ADMIN_SIGN_IN_DENIED);
    expect(fixture.nativeElement.textContent).toContain(ADMIN_SIGN_IN_DENIED);
    expect(fixture.nativeElement.textContent).not.toContain('ADMIN_NOT_ALLOWED');
    expect(fixture.nativeElement.textContent).not.toContain('allowlist');
  });

  it('allows a restored session into Founder routes without loading review data', async () => {
    flushAdminBootstrap(http, 200);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/founders');
    fixture.detectChanges();
    flushFounderList(http);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(router.url).toBe('/founders');
    expect(fixture.nativeElement.textContent).toContain('No Founder applications are waiting for review');
    const account = [...fixture.nativeElement.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === 'Account',
    ) as HTMLButtonElement;
    account.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('ada@example.com');
    expect(fixture.nativeElement.textContent).not.toContain('Not signed in');
  });

  it('turns a later 401 into the login screen and leaves 403 alone', async () => {
    flushAdminBootstrap(http, 200);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/founders');
    fixture.detectChanges();
    flushFounderList(http);
    await fixture.whenStable();
    const auth = TestBed.inject(AdminAuth);
    const client = TestBed.inject(HttpClient);
    client.get('/api/v1/admin/founder/applications').subscribe({ error: () => undefined });
    http.expectOne('/api/v1/admin/founder/applications').flush(null, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    expect(auth.phase()).toBe('authenticated');
    expect(router.url).toBe('/founders');

    client.get('/api/v1/admin/founder/applications').subscribe({ error: () => undefined });
    http.expectOne('/api/v1/admin/founder/applications').flush(null, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(auth.phase()).toBe('unauthenticated');
    expect(router.url).toBe('/login');
    expect(google.calls).toBe(0);
  });

  it('logs out when the backend accepts it', async () => {
    flushAdminBootstrap(http, 200);
    await fixture.whenStable();
    const auth = TestBed.inject(AdminAuth);
    auth.logout().subscribe();
    http.expectOne((request) => request.method === 'DELETE').flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    expect(auth.phase()).toBe('unauthenticated');
    expect(auth.account()).toBeNull();
  });

  it('treats a logout 401 as already signed out', async () => {
    flushAdminBootstrap(http, 200);
    await fixture.whenStable();
    const auth = TestBed.inject(AdminAuth);
    auth.logout().subscribe();
    http.expectOne((request) => request.method === 'DELETE').flush(null, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
    expect(auth.phase()).toBe('unauthenticated');
    expect(auth.account()).toBeNull();
  });

  it('keeps the session when logout cannot reach the server', async () => {
    flushAdminBootstrap(http, 200);
    await fixture.whenStable();
    const auth = TestBed.inject(AdminAuth);
    auth.logout().subscribe();
    http.expectOne((request) => request.method === 'DELETE').error(new ProgressEvent('error'));
    await fixture.whenStable();
    expect(auth.phase()).toBe('authenticated');
    expect(auth.errorMessage()).toContain('may still be active');
  });
});
