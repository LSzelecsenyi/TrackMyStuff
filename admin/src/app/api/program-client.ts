import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, catchError, switchMap, throwError } from 'rxjs';
import { API_BASE_URL, adminPaths, adminUrl } from './admin-api';
import { AdminSessionClient } from './admin-session-client';

export type ProgramLifecycle = 'ACTIVE' | 'INACTIVE' | 'RETIRED';
export type ProgramKey = 'founder' | 'early-adopter' | 'pro-discovery' | 'welcome-back';

export interface ProgramSnapshot {
  program: string;
  lifecycle: ProgramLifecycle;
  usedCount: number;
  capacity: number;
  remaining: number;
  acceptingNewParticipants: boolean;
}

export interface PromotionSnapshot {
  program: string;
  lifecycle: ProgramLifecycle;
  acceptingNewGrants: boolean;
  summary: string;
  eligibility: string;
}

export interface ProgramsView {
  founder: ProgramSnapshot;
  earlyAdopter: ProgramSnapshot;
  proDiscovery: PromotionSnapshot;
  welcomeBack: PromotionSnapshot;
}

@Injectable({ providedIn: 'root' })
export class ProgramClient {
  private readonly http = inject(HttpClient);
  private readonly sessions = inject(AdminSessionClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  listPrograms(): Observable<ProgramsView> {
    return this.http.get<ProgramsView>(adminUrl(this.apiBaseUrl, adminPaths.programs));
  }

  changeProgram(program: ProgramKey, lifecycle: ProgramLifecycle): Observable<ProgramSnapshot> {
    return this.changing(() =>
      this.http.put<ProgramSnapshot>(`${adminUrl(this.apiBaseUrl, adminPaths.programs)}/${program}`, { lifecycle }),
    );
  }

  private changing<T>(request: () => Observable<T>, retried = false): Observable<T> {
    return request().pipe(
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 403 && !retried) {
          return this.sessions.ensureCsrf().pipe(switchMap(() => this.changing(request, true)));
        }
        return throwError(() => error);
      }),
    );
  }
}
