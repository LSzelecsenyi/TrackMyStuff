import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { flushAdminBootstrap, flushFounderList } from './auth/admin-testing';
import { App } from './app';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), provideLocationMocks(), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('boots into the admin shell', async () => {
    const fixture = TestBed.createComponent(App);
    flushAdminBootstrap(TestBed.inject(HttpTestingController), 200);
    await TestBed.inject(Router).navigateByUrl('/founders');
    fixture.detectChanges();
    flushFounderList(TestBed.inject(HttpTestingController));
    await fixture.whenStable();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('strict-admin-shell')).not.toBeNull();
    expect(compiled.querySelector('.wordmark')?.textContent).toContain('Strict');
  });

  it('opens the Founder list from the root route', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    flushAdminBootstrap(TestBed.inject(HttpTestingController), 200);
    await router.navigateByUrl('/');
    fixture.detectChanges();
    flushFounderList(TestBed.inject(HttpTestingController));
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(router.url).toBe('/founders');
    expect(compiled.querySelector('h1')?.textContent).toContain('No Founder applications are waiting for review');
  });
});
