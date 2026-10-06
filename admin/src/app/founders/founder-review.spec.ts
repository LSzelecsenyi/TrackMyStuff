import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { FounderReviewClient } from '../api/founder-review-client';
import { formatDuration, formatLocalDate } from './founder-review';

describe('Founder review contract', () => {
  it('formats duration for people and leaves unknown durations unknown', () => {
    expect(formatDuration(3672)).toBe('1h 1m');
    expect(formatDuration(3600)).toBe('1h');
    expect(formatDuration(90)).toBe('1m 30s');
    expect(formatDuration(45)).toBe('45s');
    expect(formatDuration(0)).toBe('0s');
    expect(formatDuration(null)).toBeNull();
    expect(formatDuration(undefined)).toBeNull();
  });

  it('formats a workout local date as that calendar day', () => {
    const expected = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(2026, 5, 1));
    expect(formatLocalDate('2026-06-01')).toBe(expected);
    expect(readFileSync(resolve(process.cwd(), 'src/app/founders/founder-review.ts'), 'utf8')).toContain(
      'new Date(year, month - 1, day)',
    );
  });

  it('requests the review resources without a bearer token', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    const client = TestBed.inject(FounderReviewClient);
    client.listFounderApplications().subscribe();
    client.getFounderApplication('11111111-1111-4111-8111-111111111111').subscribe();
    const list = http.expectOne('/api/v1/admin/founder/applications');
    const detail = http.expectOne('/api/v1/admin/founder/applications/11111111-1111-4111-8111-111111111111');
    expect(list.request.method).toBe('GET');
    expect(detail.request.method).toBe('GET');
    expect(list.request.headers.get('Authorization')).toBeNull();
    expect(detail.request.headers.get('Authorization')).toBeNull();
    list.flush([]);
    detail.flush({});
    http.verify();
  });

  it('does not choose entitlement, status, reviewer, or qualification thresholds', () => {
    const source = [
      'src/app/api/founder-review-client.ts',
      'src/app/founders/founder-list.ts',
      'src/app/founders/founder-list.html',
      'src/app/founders/founder-detail.ts',
      'src/app/founders/founder-detail.html',
      'src/app/founders/founder-review.ts',
    ]
      .map((file) => readFileSync(resolve(process.cwd(), file), 'utf8'))
      .join('\n');
    expect(source).not.toContain('FOUNDER_LIFETIME');
    expect(source).not.toContain('REQUIRED_');
    expect(source).not.toContain('founderWorkoutCount');
    expect(source).not.toContain('Authorization');
    expect(source).not.toContain('googleSubject');
    expect(source).not.toContain('userId');
    const client = readFileSync(resolve(process.cwd(), 'src/app/api/founder-review-client.ts'), 'utf8');
    expect(client).toContain('/approval');
    expect(client).toContain('/rejection');
    expect(client).not.toContain('reviewedBy');
    expect(client).not.toContain('status:');
  });
});
