import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import {
  ProgramClient,
  ProgramKey,
  ProgramLifecycle,
  ProgramSnapshot,
  ProgramsView,
  PromotionSnapshot,
} from '../api/program-client';
import { StrictState } from '../shared/strict-state';

type PagePhase = 'loading' | 'ready' | 'error';

@Component({
  selector: 'strict-program-management',
  imports: [StrictState],
  templateUrl: './program-management.html',
  styleUrl: './program-management.css',
})
export class ProgramManagement {
  private readonly programsApi = inject(ProgramClient);
  private request = 0;

  readonly phase = signal<PagePhase>('loading');
  readonly programs = signal<ProgramsView | null>(null);
  readonly actionError = signal('');
  readonly busy = signal<ProgramKey | null>(null);
  readonly confirming = signal<ProgramKey | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    const ticket = ++this.request;
    if (this.programs() === null) {
      this.phase.set('loading');
    }
    this.programsApi.listPrograms().subscribe({
      next: (view) => {
        if (ticket !== this.request) {
          return;
        }
        this.programs.set(view);
        this.phase.set('ready');
      },
      error: (error: unknown) => {
        if (ticket !== this.request || (error instanceof HttpErrorResponse && error.status === 401)) {
          return;
        }
        if (this.programs() === null) {
          this.phase.set('error');
        } else {
          this.actionError.set('Program management could not be refreshed. The cards may be stale.');
        }
      },
    });
  }

  pause(program: ProgramKey): void {
    this.change(program, 'INACTIVE');
  }

  resume(program: ProgramKey): void {
    this.change(program, 'ACTIVE');
  }

  askRetire(program: ProgramKey): void {
    this.actionError.set('');
    this.confirming.set(program);
  }

  cancelRetire(): void {
    this.confirming.set(null);
  }

  confirmRetire(): void {
    const program = this.confirming();
    if (program === null) {
      return;
    }
    this.change(program, 'RETIRED');
  }

  cards(): ProgramCard[] {
    const view = this.programs();
    if (view === null) {
      return [];
    }
    return [
      this.cohortCard('founder', 'Founder', 'Enrolled', view.founder),
      this.cohortCard('early-adopter', 'Early Adopter', 'Assigned', view.earlyAdopter),
      this.promotionCard('pro-discovery', 'Pro Discovery', view.proDiscovery),
      this.promotionCard('welcome-back', 'Welcome Back', view.welcomeBack),
    ];
  }

  name(program: ProgramKey): string {
    switch (program) {
      case 'founder':
        return 'Founder';
      case 'early-adopter':
        return 'Early Adopter';
      case 'pro-discovery':
        return 'Pro Discovery';
      case 'welcome-back':
        return 'Welcome Back';
    }
  }

  lifecycleLabel(lifecycle: ProgramLifecycle): string {
    switch (lifecycle) {
      case 'ACTIVE':
        return 'Active';
      case 'INACTIVE':
        return 'Paused';
      case 'RETIRED':
        return 'Retired';
    }
  }

  joinLabel(program: ProgramSnapshot): string {
    return program.acceptingNewParticipants
      ? 'New participants can join'
      : 'New participants cannot join';
  }

  private cohortCard(key: ProgramKey, title: string, countLabel: string, program: ProgramSnapshot): ProgramCard {
    return {
      key,
      title,
      lifecycle: program.lifecycle,
      join: this.joinLabel(program),
      summary: '',
      eligibility: '',
      facts: [
        ['Lifecycle', program.lifecycle],
        ['Capacity', String(program.capacity)],
        [countLabel, String(program.usedCount)],
        ['Remaining', String(program.remaining)],
      ],
    };
  }

  private promotionCard(key: ProgramKey, title: string, program: PromotionSnapshot): ProgramCard {
    return {
      key,
      title,
      lifecycle: program.lifecycle,
      join: program.acceptingNewGrants ? 'New benefits can be granted' : 'New benefits cannot be granted',
      summary: program.summary,
      eligibility: program.eligibility,
      facts: [['Lifecycle', program.lifecycle]],
    };
  }

  private change(program: ProgramKey, lifecycle: ProgramLifecycle): void {
    if (this.busy() !== null) {
      return;
    }
    this.busy.set(program);
    this.actionError.set('');
    this.programsApi.changeProgram(program, lifecycle).subscribe({
      next: () => {
        this.busy.set(null);
        this.confirming.set(null);
        this.load();
      },
      error: (error: unknown) => {
        this.busy.set(null);
        this.confirming.set(null);
        if (error instanceof HttpErrorResponse && error.status === 401) {
          return;
        }
        this.actionError.set(this.problem(error));
        this.load();
      },
    });
  }

  private problem(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as { message?: string } | null;
      if (body && typeof body.message === 'string' && body.message.length > 0) {
        return body.message;
      }
    }
    return 'The program could not be updated.';
  }
}

export interface ProgramCard {
  key: ProgramKey;
  title: string;
  lifecycle: ProgramLifecycle;
  join: string;
  summary: string;
  eligibility: string;
  facts: [string, string][];
}
