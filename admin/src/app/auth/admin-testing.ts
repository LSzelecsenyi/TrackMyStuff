import { HttpTestingController } from '@angular/common/http/testing';

export function flushFounderList(http: HttpTestingController, body: unknown[] = []): void {
  const request = http.expectOne('/api/v1/admin/founder/applications');
  if (request.request.method !== 'GET') {
    throw new Error(`Expected founder list GET, got ${request.request.method}`);
  }
  request.flush(body);
}

export function flushAdminBootstrap(
  http: HttpTestingController,
  status: 200 | 401,
  email: string | null = 'ada@example.com',
): void {
  const csrf = http.expectOne('/api/v1/admin/csrf');
  if (csrf.request.method !== 'GET') {
    throw new Error(`Expected CSRF GET, got ${csrf.request.method}`);
  }
  csrf.flush(null, { status: 204, statusText: 'No Content' });
  const current = http.expectOne((request) => request.method === 'GET' && request.url === '/api/v1/admin/session');
  if (status === 200) {
    current.flush({ email });
  } else {
    current.flush(
      { errorCode: 'UNAUTHENTICATED', message: 'Authentication is required.' },
      { status: 401, statusText: 'Unauthorized' },
    );
  }
}
