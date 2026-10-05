import { provideHttpClient } from '@angular/common/http';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from '../app';
import { routes } from '../app.routes';

describe('Founder list route', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), provideLocationMocks(), provideHttpClient()],
    }).compileComponents();
  });

  it('stays empty until review data is connected', async () => {
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl('/founders');
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('No applications loaded');
    expect(text).not.toMatch(/@/);
    expect(text).not.toContain('PENDING_APPROVAL');
  });
});
