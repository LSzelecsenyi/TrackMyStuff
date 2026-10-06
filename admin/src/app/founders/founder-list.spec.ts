import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NavigationEnd, provideRouter, Router } from '@angular/router';
import { filter, firstValueFrom } from 'rxjs';
import { App } from '../app';
import { routes } from '../app.routes';
import { AdminAuth } from '../auth/admin-auth';
import { adminSessionInterceptor } from '../auth/admin-session.interceptor';
import { flushAdminBootstrap } from '../auth/admin-testing';
import { formatInstant } from './founder-review';

const FIRST_ID = '11111111-1111-4111-8111-111111111111';
const SECOND_ID = '22222222-2222-4222-8222-222222222222';

const first = {
  id: FIRST_ID,
  status: 'PENDING_APPROVAL',
  testerEmail: 'ada@example.com',
  enrolledAt: '2026-06-01T00:00:00Z',
  deadlineAt: '2026-07-16T00:00:00Z',
  pendingAt: '2026-06-20T15:00:00Z',
  qualification: {
    qualifyingWorkoutCount: 3,
    distinctWorkoutDayCount: 2,
    requiredWorkoutCount: 2,
    requiredDistinctDayCount: 1,
  },
  feedback: 'This feedback must stay off the queue',
};

const second = {
  id: SECOND_ID,
  status: 'PENDING_APPROVAL',
  testerEmail: null,
  enrolledAt: '2026-06-02T00:00:00Z',
  deadlineAt: '2026-07-17T00:00:00Z',
  pendingAt: '2026-06-01T00:00:00Z',
  qualification: {
    qualifyingWorkoutCount: 2,
    distinctWorkoutDayCount: 1,
    requiredWorkoutCount: null,
    requiredDistinctDayCount: null,
  },
};

describe('Founder list route', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
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
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(App);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    flushAdminBootstrap(http, 200);
  });

  afterEach(() => http.verify());

  it('shows loading before the queue arrives', async () => {
    await router.navigateByUrl('/founders');
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Loading');
    expect(text).not.toContain('No Founder applications are waiting for review');
    expect(text).not.toContain('ada@example.com');
    http.expectOne('/api/v1/admin/founder/applications').flush([]);
  });

  it('renders the backend queue without sorting or leaking detail', async () => {
    await open([first, second]);
    const text = fixture.nativeElement.textContent as string;
    const cards = [...fixture.nativeElement.querySelectorAll('.queue-card')] as HTMLAnchorElement[];
    expect(cards.map((card) => card.getAttribute('href'))).toEqual([`/founders/${FIRST_ID}`, `/founders/${SECOND_ID}`]);
    expect(cards[0].textContent).toContain('ada@example.com');
    expect(cards[0].textContent).toContain('Status: Pending review');
    expect(cards[0].textContent).toContain('3 / 2');
    expect(cards[0].textContent).toContain('2 / 1');
    expect(cards[0].textContent).toContain(formatInstant(first.pendingAt));
    expect(cards[0].textContent).toContain(formatInstant(first.enrolledAt));
    expect(cards[0].textContent).toContain(formatInstant(first.deadlineAt));
    expect(cards[1].textContent).toContain('Email not available');
    expect(cards[1].textContent).toContain('2 / —');
    expect(text.indexOf('ada@example.com')).toBeLessThan(text.indexOf('Email not available'));
    expect(text).not.toContain('This feedback must stay off the queue');
    expect(text).not.toContain('Qualifying workouts will appear');
  });

  it('shows an empty queue without implying the program is empty', async () => {
    await open([]);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('No Founder applications are waiting for review');
    expect(text).toContain('Founder program itself is unchanged');
    expect(text).not.toContain('could not be loaded');
  });

  it('shows a retryable error without the backend message', async () => {
    await open(undefined, 500);
    expect(fixture.nativeElement.textContent).toContain('Founder applications could not be loaded');
    expect(fixture.nativeElement.textContent).not.toContain('database exploded');
    const retry = [...fixture.nativeElement.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === 'Try again',
    ) as HTMLButtonElement;
    retry.click();
    http.expectOne('/api/v1/admin/founder/applications').flush([first]);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('ada@example.com');
  });

  it('opens the selected application', async () => {
    await open([first]);
    const link = fixture.nativeElement.querySelector('.queue-card') as HTMLAnchorElement;
    const navigated = firstValueFrom(router.events.pipe(filter((event) => event instanceof NavigationEnd)));
    link.click();
    await navigated;
    fixture.detectChanges();
    const detail = http.expectOne(`/api/v1/admin/founder/applications/${FIRST_ID}`);
    expect(detail.request.method).toBe('GET');
    expect(detail.request.headers.get('Authorization')).toBeNull();
    expect(router.url).toBe(`/founders/${FIRST_ID}`);
    detail.flush(detailBody());
    await fixture.whenStable();
  });

  it('sends a 401 through authentication instead of a queue error', async () => {
    await router.navigateByUrl('/founders');
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/founder/applications').flush(
      { errorCode: 'UNAUTHENTICATED', message: 'Authentication is required.' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await fixture.whenStable();
    fixture.detectChanges();
    expect(TestBed.inject(AdminAuth).phase()).toBe('unauthenticated');
    expect(router.url).toBe('/login');
    expect(fixture.nativeElement.textContent).not.toContain('Founder applications could not be loaded');
    expect(fixture.nativeElement.textContent).toContain('Restricted access');
  });

  async function open(body: unknown[] | undefined, status?: number): Promise<void> {
    await router.navigateByUrl('/founders');
    fixture.detectChanges();
    const request = http.expectOne('/api/v1/admin/founder/applications');
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('Authorization')).toBeNull();
    expect(request.request.url.startsWith('/api/')).toBe(true);
    if (status) {
      request.flush({ message: 'database exploded' }, { status, statusText: 'Error' });
    } else {
      request.flush(body ?? []);
    }
    await fixture.whenStable();
    fixture.detectChanges();
  }
});

function detailBody() {
  return {
    application: {
      id: FIRST_ID,
      status: 'PENDING_APPROVAL',
      enrolledAt: first.enrolledAt,
      deadlineAt: first.deadlineAt,
      pendingAt: first.pendingAt,
      expiredAt: null,
    },
    tester: { email: first.testerEmail },
    qualification: null,
    report: null,
    workouts: [],
    decision: null,
  };
}
