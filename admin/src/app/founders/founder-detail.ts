import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, effect, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FounderReviewClient } from '../api/founder-review-client';
import { StrictState } from '../shared/strict-state';
import {
  FounderReviewDetail,
  countPair,
  decisionLabel,
  emailLabel,
  formatDuration,
  formatInstant,
  formatLocalDate,
  recordedValue,
  rejectionReasonError,
  requirementLabel,
  sameInstant,
  statusLabel,
  temporaryProLabel,
  windowLabel,
  yesNo,
  UNAVAILABLE,
} from './founder-review';

const APPLICATION_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

type DetailPhase = 'loading' | 'ready' | 'invalid' | 'missing' | 'error';
type ReviewEditor = 'approve' | 'reject' | null;

@Component({
  selector: 'strict-founder-detail',
  imports: [StrictState, RouterLink],
  templateUrl: './founder-detail.html',
  styleUrl: './founder-detail.css',
})
export class FounderDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly reviews = inject(FounderReviewClient);
  private readonly approveDialog = viewChild<ElementRef<HTMLElement>>('approveDialog');
  private readonly rejectDialog = viewChild<ElementRef<HTMLElement>>('rejectDialog');
  private request = 0;

  readonly phase = signal<DetailPhase>('loading');
  readonly applicationId = signal('');
  readonly detail = signal<FounderReviewDetail | null>(null);
  readonly editor = signal<ReviewEditor>(null);
  readonly deciding = signal(false);
  readonly reason = signal('');
  readonly reasonAttempted = signal(false);
  readonly actionError = signal<string | null>(null);
  readonly emailLabel = emailLabel;
  readonly statusLabel = statusLabel;
  readonly decisionLabel = decisionLabel;
  readonly formatInstant = formatInstant;
  readonly formatLocalDate = formatLocalDate;
  readonly requirementLabel = requirementLabel;
  readonly temporaryProLabel = temporaryProLabel;
  readonly countPair = countPair;
  readonly recordedValue = recordedValue;
  readonly windowLabel = windowLabel;
  readonly yesNo = yesNo;
  readonly unavailable = UNAVAILABLE;
  readonly formatDuration = formatDuration;

  constructor() {
    effect(() => {
      const mode = this.editor();
      const dialog = mode === 'approve' ? this.approveDialog() : mode === 'reject' ? this.rejectDialog() : null;
      dialog?.nativeElement.focus();
    });
    this.route.paramMap.subscribe((params) => {
      const id = params.get('applicationId') ?? '';
      this.applicationId.set(id);
      if (!APPLICATION_ID.test(id)) {
        this.request += 1;
        this.detail.set(null);
        this.editor.set(null);
        this.phase.set('invalid');
        return;
      }
      this.load(id);
    });
  }

  load(applicationId = this.applicationId()): void {
    if (!APPLICATION_ID.test(applicationId)) {
      this.phase.set('invalid');
      return;
    }
    const ticket = ++this.request;
    this.phase.set('loading');
    this.reviews.getFounderApplication(applicationId).subscribe({
      next: (detail) => {
        if (ticket !== this.request) {
          return;
        }
        this.detail.set(detail);
        this.phase.set('ready');
      },
      error: (error: unknown) => {
        if (ticket !== this.request || (error instanceof HttpErrorResponse && error.status === 401)) {
          return;
        }
        this.detail.set(null);
        this.phase.set(error instanceof HttpErrorResponse && error.status === 404 ? 'missing' : 'error');
      },
    });
  }

  reasonError(): string | null {
    if (!this.reasonAttempted()) {
      return null;
    }
    return rejectionReasonError(this.reason());
  }

  openApprove(): void {
    if (this.deciding()) {
      return;
    }
    this.actionError.set(null);
    this.editor.set('approve');
  }

  openReject(): void {
    if (this.deciding()) {
      return;
    }
    this.actionError.set(null);
    this.reasonAttempted.set(false);
    this.editor.set('reject');
  }

  cancelEditor(event?: Event): void {
    event?.preventDefault();
    if (this.deciding()) {
      return;
    }
    this.editor.set(null);
    this.reason.set('');
    this.reasonAttempted.set(false);
    this.actionError.set(null);
  }

  confirmApprove(): void {
    if (this.deciding() || this.editor() !== 'approve') {
      return;
    }
    this.deciding.set(true);
    this.actionError.set(null);
    this.reviews.approveFounderApplication(this.applicationId()).subscribe({
      next: (detail) => this.applyDecision(detail),
      error: (error: unknown) => this.failDecision(error),
    });
  }

  confirmReject(): void {
    if (this.deciding() || this.editor() !== 'reject') {
      return;
    }
    this.reasonAttempted.set(true);
    const error = rejectionReasonError(this.reason());
    if (error) {
      this.actionError.set(null);
      return;
    }
    this.deciding.set(true);
    this.actionError.set(null);
    this.reviews.rejectFounderApplication(this.applicationId(), this.reason().trim()).subscribe({
      next: (detail) => this.applyDecision(detail),
      error: (failure: unknown) => this.failDecision(failure),
    });
  }

  onReasonInput(value: string): void {
    this.reason.set(value);
  }

  private applyDecision(detail: FounderReviewDetail): void {
    this.detail.set(detail);
    this.phase.set('ready');
    this.deciding.set(false);
    this.editor.set(null);
    this.reason.set('');
    this.reasonAttempted.set(false);
    this.actionError.set(null);
  }

  private failDecision(error: unknown): void {
    this.deciding.set(false);
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return;
    }
    if (error instanceof HttpErrorResponse && error.status === 409) {
      this.editor.set(null);
      this.reason.set('');
      this.reasonAttempted.set(false);
      this.actionError.set('This application is no longer awaiting review.');
      this.reload();
      return;
    }
    this.actionError.set('The decision was not saved. Try again.');
  }

  private reload(): void {
    const applicationId = this.applicationId();
    const ticket = ++this.request;
    this.reviews.getFounderApplication(applicationId).subscribe({
      next: (detail) => {
        if (ticket !== this.request) {
          return;
        }
        this.detail.set(detail);
        this.phase.set('ready');
      },
      error: (error: unknown) => {
        if (ticket !== this.request || (error instanceof HttpErrorResponse && error.status === 401)) {
          return;
        }
        this.detail.set(null);
        this.phase.set(error instanceof HttpErrorResponse && error.status === 404 ? 'missing' : 'error');
      },
    });
  }

  recordedWindowDiffers(detail: FounderReviewDetail): boolean {
    const rules = detail.qualification?.rules;
    if (!rules) {
      return false;
    }
    return (
      !sameInstant(rules.enrolledAt, detail.application.enrolledAt) ||
      !sameInstant(rules.deadlineAt, detail.application.deadlineAt)
    );
  }
}
