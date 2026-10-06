import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FounderReviewClient } from '../api/founder-review-client';
import { StrictState } from '../shared/strict-state';
import {
  FounderReviewSummary,
  countPair,
  emailLabel,
  formatInstant,
  statusLabel,
} from './founder-review';

type ListPhase = 'loading' | 'ready' | 'empty' | 'error';

@Component({
  selector: 'strict-founder-list',
  imports: [StrictState, RouterLink],
  templateUrl: './founder-list.html',
  styleUrl: './founder-list.css',
})
export class FounderList {
  private readonly reviews = inject(FounderReviewClient);
  private request = 0;

  readonly phase = signal<ListPhase>('loading');
  readonly applications = signal<FounderReviewSummary[]>([]);
  readonly emailLabel = emailLabel;
  readonly statusLabel = statusLabel;
  readonly formatInstant = formatInstant;
  readonly countPair = countPair;

  constructor() {
    this.load();
  }

  load(): void {
    const ticket = ++this.request;
    this.phase.set('loading');
    this.reviews.listFounderApplications().subscribe({
      next: (rows) => {
        if (ticket !== this.request) {
          return;
        }
        this.applications.set(rows);
        this.phase.set(rows.length === 0 ? 'empty' : 'ready');
      },
      error: (error: unknown) => {
        if (ticket !== this.request || (error instanceof HttpErrorResponse && error.status === 401)) {
          return;
        }
        this.phase.set('error');
      },
    });
  }
}
