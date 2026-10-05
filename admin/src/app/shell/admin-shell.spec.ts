import { provideHttpClient } from '@angular/common/http';
import { provideLocationMocks } from '@angular/common/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from '../app';
import { routes } from '../app.routes';

describe('Admin shell', () => {
  let fixture: ComponentFixture<App>;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), provideLocationMocks(), provideHttpClient()],
    }).compileComponents();
    fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl('/founders');
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('shows the wordmark, one section, and the account placeholder', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.wordmark')?.textContent).toBe('Strict');
    expect(compiled.querySelector('.admin-label')?.textContent).toBe('Admin');
    const links = [...compiled.querySelectorAll('nav a')].map((link) => link.textContent?.trim());
    expect(links).toEqual(['Founder applications']);
    expect(compiled.querySelector('aside')).toBeNull();
    expect(compiled.querySelector('#main')).not.toBeNull();
  });

  it('opens the account state without signing in', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const account = [...compiled.querySelectorAll('button')].find((button) => button.textContent?.trim() === 'Account');
    account?.click();
    fixture.detectChanges();
    expect(compiled.querySelector('#admin-account')?.textContent).toContain('Not signed in');
    expect(compiled.querySelector('#admin-account')?.textContent).toContain('member token');
  });

  it('keeps navigation usable without a fixed sidebar', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const nav = compiled.querySelector('nav');
    expect(nav?.getAttribute('aria-label')).toBe('Admin');
    expect(compiled.querySelector('.topbar')).not.toBeNull();
    expect(getComputedStyle(compiled.querySelector('.topbar') as Element).display).not.toBe('none');
  });
});
