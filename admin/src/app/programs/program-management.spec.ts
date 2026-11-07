import { provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgramManagement } from './program-management';

const promotion = {
  program: 'PRO_DISCOVERY',
  lifecycle: 'INACTIVE',
  acceptingNewGrants: false,
  summary: 'One 14-day Pro trial.',
  eligibility: 'A new trial is issued only while this program is Active.',
};

const active = {
  founder: {
    program: 'FOUNDER',
    lifecycle: 'ACTIVE',
    usedCount: 4,
    capacity: 2000,
    remaining: 1996,
    acceptingNewParticipants: true,
  },
  earlyAdopter: {
    program: 'EARLY_ADOPTER',
    lifecycle: 'ACTIVE',
    usedCount: 1000,
    capacity: 1000,
    remaining: 0,
    acceptingNewParticipants: false,
  },
  proDiscovery: promotion,
  welcomeBack: { ...promotion, program: 'WELCOME_BACK' },
};

describe('Program management', () => {
  let fixture: ComponentFixture<ProgramManagement>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProgramManagement],
      providers: [
        provideHttpClient(withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' })),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ProgramManagement);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('shows both programs and pauses Founder without offering capacity editing', () => {
    http.expectOne('/api/v1/admin/programs').flush(active);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Founder');
    expect(text).toContain('Early Adopter');
    expect(text).toContain('ACTIVE');
    expect(text).toContain('1996');
    expect(text).toContain('New participants can join');
    expect(text).toContain('New participants cannot join');
    expect(text).not.toContain('Capacity edit');

    const pause = [...fixture.nativeElement.querySelectorAll('button')].find((button) => button.textContent?.trim() === 'Pause');
    pause?.click();
    const update = http.expectOne('/api/v1/admin/programs/founder');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ lifecycle: 'INACTIVE' });
    update.flush({ ...active.founder, lifecycle: 'INACTIVE', acceptingNewParticipants: false });
    http.expectOne('/api/v1/admin/programs').flush({
      ...active,
      founder: { ...active.founder, lifecycle: 'INACTIVE', acceptingNewParticipants: false },
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Resume');
  });

  it('requires a permanent-retirement confirmation and then shows a terminal Founder card', () => {
    http.expectOne('/api/v1/admin/programs').flush(active);
    fixture.detectChanges();
    const retire = [...fixture.nativeElement.querySelectorAll('button')].find((button) =>
      button.textContent?.includes('Retire permanently'),
    );
    retire?.click();
    fixture.detectChanges();
    const dialog = fixture.nativeElement.textContent as string;
    expect(dialog).toContain('This action is permanent');
    expect(dialog).toContain('New participants will never be accepted again');
    expect(dialog).toContain('Existing grants and entitlements remain valid');
    expect(dialog).toContain('Existing Founder participants may still complete the program');
    expect(dialog).toContain('Obsolete HMAC deletion markers');
    expect(dialog).toContain('Retirement is not a pause');

    const confirm = [...fixture.nativeElement.querySelectorAll('button')].find(
      (button) => button.closest('[role="alertdialog"]') && button.textContent?.includes('Retire permanently'),
    );
    confirm?.click();
    const update = http.expectOne('/api/v1/admin/programs/founder');
    expect(update.request.body).toEqual({ lifecycle: 'RETIRED' });
    update.flush({ ...active.founder, lifecycle: 'RETIRED', acceptingNewParticipants: false });
    http.expectOne('/api/v1/admin/programs').flush({
      ...active,
      founder: { ...active.founder, lifecycle: 'RETIRED', remaining: 1996, acceptingNewParticipants: false },
    });
    fixture.detectChanges();
    const founder = fixture.nativeElement.querySelector('#founder-program')?.closest('section');
    expect(founder?.textContent).toContain('RETIRED');
    expect(founder?.textContent).not.toContain('Resume');
    expect(founder?.textContent).not.toContain('Pause');
    expect(fixture.nativeElement.querySelector('[role="alertdialog"]')).toBeNull();
  });

  it('shows the server message when a retired program cannot be resumed', () => {
    http.expectOne('/api/v1/admin/programs').flush({
      ...active,
      earlyAdopter: { ...active.earlyAdopter, lifecycle: 'INACTIVE', acceptingNewParticipants: false },
    });
    fixture.detectChanges();
    const resume = [...fixture.nativeElement.querySelectorAll('button')].find((button) => button.textContent?.trim() === 'Resume');
    resume?.click();
    const update = http.expectOne('/api/v1/admin/programs/early-adopter');
    update.flush(
      { errorCode: 'PROGRAM_RETIRED', message: 'This program is retired and cannot be reactivated.' },
      { status: 409, statusText: 'Conflict' },
    );
    http.expectOne('/api/v1/admin/programs').flush({
      ...active,
      earlyAdopter: { ...active.earlyAdopter, lifecycle: 'RETIRED', acceptingNewParticipants: false },
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('This program is retired and cannot be reactivated.');
    expect(fixture.nativeElement.textContent).toContain('There is no resume or pause action.');
  });
});
