import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from '../app';
import { routes } from '../app.routes';
import { AdminAuth } from '../auth/admin-auth';
import { adminSessionInterceptor } from '../auth/admin-session.interceptor';
import { flushAdminBootstrap } from '../auth/admin-testing';
import { FounderReviewDetail, formatInstant, formatLocalDate } from './founder-review';

const APPLICATION_ID = '11111111-1111-4111-8111-111111111111';
const REVIEWER_ID = '99999999-9999-4999-8999-999999999999';
const LATER_WORKOUT = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const EARLIER_WORKOUT = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';

describe('Founder detail route', () => {
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

  it('shows loading before the review arrives', async () => {
    await router.navigateByUrl(`/founders/${APPLICATION_ID}`);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Loading');
    expect(text).not.toContain('ada@example.com');
    expect(text).not.toContain('Awaiting review');
    http.expectOne(detailUrl()).flush(review());
  });

  it('renders stored evidence without recalculating it or exposing reviewer ids', async () => {
    await open(review());
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('ada@example.com');
    expect(text).toContain('Status: Pending review');
    expect(text).toContain(formatInstant('2026-06-20T15:00:00Z'));
    expect(text).toContain(formatInstant('2026-06-01T00:00:00Z'));
    expect(text).toContain(formatInstant('2026-07-16T00:00:00Z'));
    expect(text).toContain('Qualifying workouts');
    expect(text).toContain('3 / 2');
    expect(text).toContain('Distinct workout days');
    expect(text).toContain('2 / 1');
    expect(text).toContain('Training requirement');
    expect(text).toContain('Complete');
    expect(text).toContain('Temporary Pro');
    expect(text).toContain('Not reached');
    expect(text).toContain('Rules recorded with this submission');
    expect(text).toContain('fast');
    expect(text).toContain('45 days');
    expect(text).toContain('window stored with this submission');
    expect(text).not.toContain('Enrollment recorded with this submission');
    expect(text).toContain('1.4.2');
    expect(text).toContain('android');
    expect(text).toContain('Line one');
    expect(text).toContain('Line two');
    expect(text).toContain('<b>not html</b>');
    expect(fixture.nativeElement.querySelector('b')).toBeNull();
    expect(fixture.nativeElement.querySelector('.feedback')?.textContent).toContain('Line one\n\nLine two');
    const names = [...fixture.nativeElement.querySelectorAll('.workout h3')].map((node) => node.textContent?.trim());
    expect(names).toEqual(['Later day', 'Untitled workout']);
    expect(text.indexOf('Later day')).toBeLessThan(text.indexOf('Untitled workout'));
    expect(text).toContain('1h 1m');
    expect(text).toContain(formatLocalDate('2026-06-02'));
    expect(text).toContain('From a template');
    expect(text).toContain('External load used');
    expect(text).toContain('—');
    expect(text).not.toContain('0s');
    expect(text).not.toContain('0 exercises');
    expect(text).toContain('Awaiting review');
    expect(text).not.toContain(REVIEWER_ID);
    expect(text).toContain('Approve');
    expect(text).toContain('Reject');
    expect(text).not.toContain('Approve this Founding Tester?');
    expect(fixture.nativeElement.querySelector('button[type="submit"]')).toBeNull();
  });

  it('shows historical window dates when they differ from the current application', async () => {
    const body = review();
    const qualification = body.qualification;
    if (!qualification) {
      throw new Error('expected qualification');
    }
    qualification.rules.deadlineAt = '2026-07-16T00:00:00Z';
    body.application.deadlineAt = '2026-06-02T00:00:00Z';
    await open(body);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Deadline recorded with this submission');
    expect(text).toContain(formatInstant('2026-07-16T00:00:00Z'));
    expect(text).toContain(formatInstant('2026-06-02T00:00:00Z'));
  });

  it('shows an unavailable rule and a recorded rejection without the reviewer id', async () => {
    const body = review();
    const qualification = body.qualification;
    if (!qualification) {
      throw new Error('expected qualification');
    }
    qualification.rules.profile = null;
    qualification.rules.requiredWorkoutCount = null;
    qualification.rules.qualificationWindowDays = null;
    qualification.trainingRequirementsComplete = null;
    body.application.status = 'REJECTED';
    body.decision = {
      decision: 'REJECTED',
      decidedAt: '2026-06-21T12:00:00Z',
      reason: 'The report did not describe the training.',
      reviewedBy: REVIEWER_ID,
    };
    await open(body);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Not recorded');
    expect(text).toContain('Status: Rejected');
    expect(text).toContain('Decision: Rejected');
    expect(text).toContain('The report did not describe the training.');
    expect(text).toContain(formatInstant('2026-06-21T12:00:00Z'));
    expect(text).not.toContain(REVIEWER_ID);
    expect(text).not.toContain('Awaiting review');
  });

  it('rejects an address that is not an application id without calling the API', async () => {
    await router.navigateByUrl('/founders/not-an-id');
    await fixture.whenStable();
    fixture.detectChanges();
    http.expectNone((request) => request.url.includes('/founder/applications/'));
    expect(router.url).toBe('/founders/not-an-id');
    expect(fixture.nativeElement.textContent).toContain('not a Founder application id');
    expect(fixture.nativeElement.querySelector('a[href="/founders"]')).not.toBeNull();
  });

  it('shows not-found without the backend message or logging out', async () => {
    await router.navigateByUrl(`/founders/${APPLICATION_ID}`);
    fixture.detectChanges();
    http.expectOne(detailUrl()).flush(
      { errorCode: 'FOUNDER_NOT_FOUND', message: 'Founder application was not found.' },
      { status: 404, statusText: 'Not Found' },
    );
    await fixture.whenStable();
    fixture.detectChanges();
    expect(router.url).toBe(`/founders/${APPLICATION_ID}`);
    expect(TestBed.inject(AdminAuth).phase()).toBe('authenticated');
    expect(fixture.nativeElement.textContent).toContain('This Founder application was not found');
    expect(fixture.nativeElement.textContent).not.toContain('FOUNDER_NOT_FOUND');
  });

  it('retries a server error without showing the exception', async () => {
    await router.navigateByUrl(`/founders/${APPLICATION_ID}`);
    fixture.detectChanges();
    http.expectOne(detailUrl()).flush({ message: 'SQLException boom' }, { status: 500, statusText: 'Error' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('could not be loaded');
    expect(fixture.nativeElement.textContent).not.toContain('SQLException');
    const retry = [...fixture.nativeElement.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === 'Try again',
    ) as HTMLButtonElement;
    retry.click();
    http.expectOne(detailUrl()).flush(review());
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('ada@example.com');
  });

  it('sends a 401 through authentication instead of a detail error', async () => {
    await router.navigateByUrl(`/founders/${APPLICATION_ID}`);
    fixture.detectChanges();
    http.expectOne(detailUrl()).flush(
      { errorCode: 'UNAUTHENTICATED', message: 'Authentication is required.' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await fixture.whenStable();
    fixture.detectChanges();
    expect(TestBed.inject(AdminAuth).phase()).toBe('unauthenticated');
    expect(router.url).toBe('/login');
    expect(fixture.nativeElement.textContent).not.toContain('could not be loaded');
    expect(fixture.nativeElement.textContent).toContain('Restricted access');
  });

  async function open(body: FounderReviewDetail): Promise<void> {
    await router.navigateByUrl(`/founders/${APPLICATION_ID}`);
    fixture.detectChanges();
    const request = http.expectOne(detailUrl());
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('Authorization')).toBeNull();
    expect(request.request.url.startsWith('/api/')).toBe(true);
    request.flush(body);
    await fixture.whenStable();
    fixture.detectChanges();
  }
});

function detailUrl(): string {
  return `/api/v1/admin/founder/applications/${APPLICATION_ID}`;
}

function review(): FounderReviewDetail {
  return {
    application: {
      id: APPLICATION_ID,
      status: 'PENDING_APPROVAL',
      enrolledAt: '2026-06-01T00:00:00Z',
      deadlineAt: '2026-07-16T00:00:00Z',
      pendingAt: '2026-06-20T15:00:00Z',
      expiredAt: null,
    },
    tester: { email: 'ada@example.com' },
    qualification: {
      qualifyingWorkoutCount: 3,
      distinctWorkoutDayCount: 2,
      trainingRequirementsComplete: true,
      temporaryProReached: false,
      rules: {
        profile: 'fast',
        requiredWorkoutCount: 2,
        requiredDistinctDayCount: 1,
        temporaryProWorkoutCount: 1,
        qualificationWindowDays: 45,
        enrolledAt: '2026-06-01T00:00:00Z',
        deadlineAt: '2026-07-16T00:00:00Z',
      },
    },
    report: {
      submittedAt: '2026-06-20T15:00:00Z',
      appVersion: '1.4.2',
      platform: 'android',
      feedback: 'Line one\n\nLine two <b>not html</b>',
    },
    workouts: [
      {
        clientWorkoutId: LATER_WORKOUT,
        localDate: '2026-06-02',
        completedAt: '2026-06-01T14:00:00Z',
        displayName: 'Later day',
        durationSeconds: 3672,
        exerciseCount: 4,
        completedSetCount: 12,
        fromTemplate: true,
        usedExternalLoad: true,
      },
      {
        clientWorkoutId: EARLIER_WORKOUT,
        localDate: '2026-06-01',
        completedAt: '2026-06-01T00:00:00Z',
        displayName: null,
        durationSeconds: null,
        exerciseCount: null,
        completedSetCount: null,
        fromTemplate: null,
        usedExternalLoad: null,
      },
    ],
    decision: null,
  };
}
