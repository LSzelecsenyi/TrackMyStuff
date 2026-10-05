import { provideHttpClient } from '@angular/common/http';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from '../app';
import { routes } from '../app.routes';

const APPLICATION_ID = '11111111-1111-4111-8111-111111111111';

describe('Founder detail route', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), provideLocationMocks(), provideHttpClient()],
    }).compileComponents();
  });

  it('lays out a review without approve or reject actions', async () => {
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl(`/founders/${APPLICATION_ID}`);
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    const text = compiled.textContent ?? '';
    expect(text).toContain(APPLICATION_ID);
    expect(text).toContain('Qualification progress');
    expect(text).toContain('Tester analytics');
    expect(text).toContain('Qualifying workouts');
    expect(text).toContain('Written feedback');
    expect(text).toContain('App version and platform');
    expect(text).toContain('Approval and rejection are not available');
    expect(compiled.querySelector('button[type="submit"]')).toBeNull();
  });

  it('rejects an address that is not an application id', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/founders/not-an-id');
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    const text = compiled.textContent ?? '';
    expect(router.url).toBe('/founders/not-an-id');
    expect(compiled.querySelector('strict-founder-detail')).not.toBeNull();
    expect(text).toContain('not a Founder application id');
  });
});
