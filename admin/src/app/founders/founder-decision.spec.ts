import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NavigationEnd, provideRouter, Router } from '@angular/router';
import { filter, firstValueFrom } from 'rxjs';
import { App } from '../app';
import { routes } from '../app.routes';
import { AdminAuth } from '../auth/admin-auth';
import { adminSessionInterceptor } from '../auth/admin-session.interceptor';
import { flushAdminBootstrap } from '../auth/admin-testing';
import { FounderReviewDetail } from './founder-review';

const APPLICATION_ID = '11111111-1111-4111-8111-111111111111';

describe('Founder review decisions', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    document.cookie = 'XSRF-TOKEN=csrf-value';
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

  it('asks for confirmation and does not approve on cancel or escape', async () => {
    await open(pending());
    click('Approve');
    expect(text()).toContain('Approve this Founding Tester?');
    expect(text()).toContain('permanent Founder Lifetime access');
    expect(postCount()).toBe(0);

    click('Cancel');
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    expect(text()).toContain('Status: Pending review');
    expect(postCount()).toBe(0);

    click('Approve');
    fixture.debugElement.query(By.css('[role="dialog"]')).triggerEventHandler('keydown.escape', {
      preventDefault() {
        return undefined;
      },
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    expect(postCount()).toBe(0);
  });

  it('approves through the backend and renders the returned decision', async () => {
    await open(pending());
    click('Approve');
    click('Approve');
    const posts = http.match((request) => request.method === 'POST');
    expect(posts).toHaveLength(1);
    expect(posts[0].request.url).toBe(`${detailUrl()}/approval`);
    expect(posts[0].request.body).toEqual({});
    expect(posts[0].request.headers.get('Authorization')).toBeNull();
    expect(posts[0].request.headers.get('X-XSRF-TOKEN')).toBe('csrf-value');
    expect(JSON.stringify(posts[0].request.body)).not.toContain('FOUNDER_LIFETIME');
    expect(text()).toContain('Status: Pending review');
    posts[0].flush(decided('APPROVED', null));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text()).toContain('Status: Approved');
    expect(text()).toContain('Decision: Approved');
    expect(buttons()).not.toContain('Approve');
    expect(buttons()).not.toContain('Reject');
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
  });

  it('refreshes a stale CSRF token once and then uses the retried approval', async () => {
    await open(pending());
    click('Approve');
    click('Approve');
    http.expectOne((request) => request.method === 'POST').flush(null, { status: 403, statusText: 'Forbidden' });
    http.expectOne('/api/v1/admin/csrf').flush('ok');
    const retry = http.expectOne((request) => request.method === 'POST');
    expect(retry.request.url).toBe(`${detailUrl()}/approval`);
    retry.flush(decided('APPROVED', null));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text()).toContain('Status: Approved');
    expect(postCount()).toBe(0);
  });

  it('keeps the application pending when approval fails and blocks a second in-flight request', async () => {
    await open(pending());
    click('Approve');
    const confirm = button('Approve');
    confirm.click();
    confirm.click();
    const posts = http.match((request) => request.method === 'POST');
    expect(posts).toHaveLength(1);
    posts[0].flush(null, { status: 500, statusText: 'Error' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text()).toContain('Status: Pending review');
    expect(text()).toContain('The decision was not saved. Try again.');
    expect(text()).not.toContain('Decision: Approved');
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();
  });

  it('sends approval 401 through authentication', async () => {
    await open(pending());
    click('Approve');
    click('Approve');
    http.expectOne((request) => request.method === 'POST').flush(
      { errorCode: 'UNAUTHENTICATED', message: 'Authentication is required.' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await fixture.whenStable();
    fixture.detectChanges();
    expect(TestBed.inject(AdminAuth).phase()).toBe('unauthenticated');
    expect(router.url).toBe('/login');
    expect(text()).not.toContain('Decision: Approved');
  });

  it('reloads the authoritative detail when approval conflicts', async () => {
    await open(pending());
    click('Approve');
    click('Approve');
    http.expectOne((request) => request.method === 'POST').flush(
      { errorCode: 'REVIEW_CONFLICT', message: 'The Founder application cannot take that review decision.' },
      { status: 409, statusText: 'Conflict' },
    );
    http.expectOne(detailUrl()).flush(decided('APPROVED', null));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text()).toContain('This application is no longer awaiting review.');
    expect(text()).not.toContain('REVIEW_CONFLICT');
    expect(text()).toContain('Status: Approved');
    expect(buttons()).not.toContain('Approve');
    expect(postCount()).toBe(0);
  });

  it('rejects a blank or too-long reason without calling the backend', async () => {
    await open(pending());
    click('Reject');
    click('Reject application');
    expect(text()).toContain('Enter a rejection reason.');
    expect(postCount()).toBe(0);

    setReason('   ');
    click('Reject application');
    expect(text()).toContain('Enter a rejection reason.');
    expect(postCount()).toBe(0);

    setReason('a'.repeat(2001));
    click('Reject application');
    expect(text()).toContain('2000 characters or fewer');
    expect((fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement).value).toHaveLength(2001);
    expect(postCount()).toBe(0);
  });

  it('rejects with the typed reason and shows the returned decision', async () => {
    await open(pending());
    click('Reject');
    setReason('  The report did not describe the training.  ');
    click('Reject application');
    const post = http.expectOne((request) => request.method === 'POST');
    expect(post.request.url).toBe(`${detailUrl()}/rejection`);
    expect(post.request.body).toEqual({ reason: 'The report did not describe the training.' });
    expect(Object.keys(post.request.body as object)).toEqual(['reason']);
    expect(post.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-value');
    expect(post.request.headers.get('Authorization')).toBeNull();
    post.flush(decided('REJECTED', 'The report did not describe the training.'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text()).toContain('Status: Rejected');
    expect(text()).toContain('Decision: Rejected');
    expect(text()).toContain('The report did not describe the training.');
    expect(text()).not.toContain('permanent Founder Lifetime');
    expect(buttons()).not.toContain('Reject');
    expect(buttons()).not.toContain('Approve');
  });

  it('sends one rejection while the first request is in flight', async () => {
    await open(pending());
    click('Reject');
    setReason('Not enough detail');
    const confirm = button('Reject application');
    confirm.click();
    confirm.click();
    const posts = http.match((request) => request.method === 'POST');
    expect(posts).toHaveLength(1);
    posts[0].flush(decided('REJECTED', 'Not enough detail'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text()).toContain('Status: Rejected');
    expect(postCount()).toBe(0);
  });

  it('preserves the rejection reason when the request fails', async () => {
    await open(pending());
    click('Reject');
    setReason('Not enough detail');
    click('Reject application');
    http.expectOne((request) => request.method === 'POST').error(new ProgressEvent('error'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text()).toContain('Status: Pending review');
    expect(text()).toContain('The decision was not saved. Try again.');
    expect((fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement).value).toBe('Not enough detail');
    expect(text()).not.toContain('Decision: Rejected');
  });

  it('reloads the detail when rejection conflicts and does not retry the mutation', async () => {
    await open(pending());
    click('Reject');
    setReason('Too late');
    click('Reject application');
    http.expectOne((request) => request.method === 'POST').flush(
      { errorCode: 'REVIEW_CONFLICT', message: 'already decided' },
      { status: 409, statusText: 'Conflict' },
    );
    http.expectOne(detailUrl()).flush(decided('REJECTED', 'An earlier reason'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text()).toContain('This application is no longer awaiting review.');
    expect(text()).toContain('An earlier reason');
    expect(text()).not.toContain('already decided');
    expect(buttons()).not.toContain('Reject application');
    expect(postCount()).toBe(0);
  });

  it('hides review actions for applications the backend already decided', async () => {
    await open(decided('APPROVED', null));
    expect(text()).toContain('Decision: Approved');
    expect(buttons()).not.toContain('Approve');
    expect(buttons()).not.toContain('Reject');

    await router.navigateByUrl('/founders');
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/founder/applications').flush([]);
    await open(decided('REJECTED', 'Not this time'));
    expect(text()).toContain('Decision: Rejected');
    expect(text()).toContain('Not this time');
    expect(buttons()).not.toContain('Approve');
    expect(buttons()).not.toContain('Reject');
  });

  it('loads the current backend queue when returning from a decision', async () => {
    await open(pending());
    click('Approve');
    click('Approve');
    http.expectOne((request) => request.method === 'POST').flush(decided('APPROVED', null));
    await fixture.whenStable();
    fixture.detectChanges();
    const back = fixture.nativeElement.querySelector('a[href="/founders"]') as HTMLAnchorElement;
    const navigated = firstValueFrom(router.events.pipe(filter((event) => event instanceof NavigationEnd)));
    back.click();
    await navigated;
    fixture.detectChanges();
    const list = http.expectOne('/api/v1/admin/founder/applications');
    expect(list.request.method).toBe('GET');
    list.flush([]);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text()).toContain('No Founder applications are waiting for review');
    expect(text()).not.toContain('founder.tester@example.com');
  });

  async function open(body: FounderReviewDetail): Promise<void> {
    await router.navigateByUrl(`/founders/${APPLICATION_ID}`);
    fixture.detectChanges();
    const request = http.expectOne(detailUrl());
    request.flush(body);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function click(label: string): void {
    button(label).click();
    fixture.detectChanges();
  }

  function button(label: string): HTMLButtonElement {
    const match = [...fixture.nativeElement.querySelectorAll('button')].find(
      (candidate) => candidate.textContent?.trim() === label,
    ) as HTMLButtonElement | undefined;
    if (!match) {
      throw new Error(`Missing button ${label}`);
    }
    return match;
  }

  function buttons(): string[] {
    return [...fixture.nativeElement.querySelectorAll('button')].map((candidate) => candidate.textContent?.trim() ?? '');
  }

  function text(): string {
    return fixture.nativeElement.textContent as string;
  }

  function postCount(): number {
    return http.match((request) => request.method === 'POST').length;
  }

  function setReason(value: string): void {
    const field = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    field.value = value;
    field.dispatchEvent(new InputEvent('input', { bubbles: true }));
    fixture.detectChanges();
  }
});

function detailUrl(): string {
  return `/api/v1/admin/founder/applications/${APPLICATION_ID}`;
}

function pending(): FounderReviewDetail {
  return {
    application: {
      id: APPLICATION_ID,
      status: 'PENDING_APPROVAL',
      enrolledAt: '2026-06-01T00:00:00Z',
      deadlineAt: '2026-07-16T00:00:00Z',
      pendingAt: '2026-06-20T15:00:00Z',
      expiredAt: null,
    },
    tester: { email: 'founder.tester@example.com' },
    qualification: null,
    report: null,
    workouts: [],
    decision: null,
  };
}

function decided(status: 'APPROVED' | 'REJECTED', reason: string | null): FounderReviewDetail {
  const detail = pending();
  detail.application.status = status;
  detail.decision = {
    decision: status,
    decidedAt: '2026-06-21T12:00:00Z',
    reason,
    reviewedBy: '99999999-9999-4999-8999-999999999999',
  };
  return detail;
}
