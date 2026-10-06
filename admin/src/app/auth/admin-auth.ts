import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, map, of, shareReplay, switchMap, tap } from 'rxjs';
import { AdminSessionClient, AdminSessionView } from '../api/admin-session-client';
import { GoogleSignIn } from './google-sign-in';

export type AdminAuthPhase = 'checking' | 'unauthenticated' | 'authenticated' | 'signingIn' | 'error';

export const ADMIN_SIGN_IN_DENIED = 'This Google account is not authorized to access Strict Admin.';

@Injectable({ providedIn: 'root' })
export class AdminAuth {
  private readonly sessions = inject(AdminSessionClient);
  private readonly google = inject(GoogleSignIn);
  private readonly router = inject(Router);

  readonly phase = signal<AdminAuthPhase>('checking');
  readonly account = signal<AdminSessionView | null>(null);
  readonly errorMessage = signal<string | null>(null);

  private settled = false;
  private ready: Observable<AdminAuthPhase> | null = null;

  whenReady(): Observable<AdminAuthPhase> {
    if (this.settled) {
      return of(this.phase());
    }
    this.ready ??= this.restore().pipe(shareReplay({ bufferSize: 1, refCount: false }));
    return this.ready;
  }

  signIn(): Observable<void> {
    if (this.phase() === 'signingIn') {
      return of(undefined);
    }
    this.phase.set('signingIn');
    this.errorMessage.set(null);
    return this.google.requestIdToken().pipe(
      switchMap((idToken) => this.sessions.login(idToken)),
      switchMap(() => this.sessions.current()),
      tap((account) => this.markAuthenticated(account)),
      map(() => undefined),
      catchError((error: unknown) => {
        this.account.set(null);
        this.phase.set('error');
        this.errorMessage.set(this.signInMessage(error));
        return of(undefined);
      }),
    );
  }

  logout(): Observable<void> {
    this.errorMessage.set(null);
    return this.sessions.logout().pipe(
      tap(() => this.sessionEnded()),
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 401) {
          this.sessionEnded();
          return of(undefined);
        }
        this.errorMessage.set('Logout did not reach Strict Admin. This session may still be active.');
        return of(undefined);
      }),
    );
  }

  sessionEnded(): void {
    this.account.set(null);
    this.errorMessage.set(null);
    this.phase.set('unauthenticated');
    if (!this.router.url.startsWith('/login')) {
      void this.router.navigateByUrl('/login');
    }
  }

  private restore(): Observable<AdminAuthPhase> {
    return this.sessions.ensureCsrf().pipe(
      switchMap(() => this.sessions.current()),
      tap((account) => this.markAuthenticated(account)),
      map(() => 'authenticated' as const),
      catchError((error: unknown) => {
        this.account.set(null);
        if (error instanceof HttpErrorResponse && error.status === 401) {
          this.phase.set('unauthenticated');
          this.settled = true;
          return of('unauthenticated' as const);
        }
        this.phase.set('error');
        this.errorMessage.set('Strict Admin could not check the existing session.');
        this.settled = true;
        return of('error' as const);
      }),
    );
  }

  private markAuthenticated(account: AdminSessionView): void {
    this.account.set(account);
    this.errorMessage.set(null);
    this.phase.set('authenticated');
    this.settled = true;
    if (this.router.url.startsWith('/login')) {
      void this.router.navigateByUrl('/founders');
    }
  }

  private signInMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return ADMIN_SIGN_IN_DENIED;
    }
    if (error instanceof HttpErrorResponse && error.status === 0) {
      return 'Sign-in could not reach Strict Admin.';
    }
    if (error instanceof Error && error.message === 'Google sign-in is not configured.') {
      return 'Google sign-in is not configured for this admin build.';
    }
    return 'Sign-in could not be completed. Try again.';
  }
}
