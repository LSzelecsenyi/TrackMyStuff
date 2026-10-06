export interface FounderQualificationSummary {
  qualifyingWorkoutCount: number;
  distinctWorkoutDayCount: number;
  requiredWorkoutCount: number | null;
  requiredDistinctDayCount: number | null;
}

export interface FounderReviewSummary {
  id: string;
  status: string;
  testerEmail: string | null;
  enrolledAt: string;
  deadlineAt: string;
  pendingAt: string | null;
  qualification: FounderQualificationSummary | null;
}

export interface FounderReviewApplication {
  id: string;
  status: string;
  enrolledAt: string;
  deadlineAt: string;
  pendingAt: string | null;
  expiredAt: string | null;
}

export interface FounderReviewRules {
  profile: string | null;
  requiredWorkoutCount: number | null;
  requiredDistinctDayCount: number | null;
  temporaryProWorkoutCount: number | null;
  qualificationWindowDays: number | null;
  enrolledAt: string;
  deadlineAt: string;
}

export interface FounderReviewQualification {
  qualifyingWorkoutCount: number;
  distinctWorkoutDayCount: number;
  trainingRequirementsComplete: boolean | null;
  temporaryProReached: boolean | null;
  rules: FounderReviewRules;
}

export interface FounderReviewReport {
  submittedAt: string;
  appVersion: string;
  platform: string;
  feedback: string;
}

export interface FounderReviewWorkout {
  clientWorkoutId: string;
  localDate: string;
  completedAt: string;
  displayName: string | null;
  durationSeconds: number | null;
  exerciseCount: number | null;
  completedSetCount: number | null;
  fromTemplate: boolean | null;
  usedExternalLoad: boolean | null;
}

export interface FounderReviewDecision {
  decision: string;
  decidedAt: string;
  reason: string | null;
  reviewedBy: string;
}

export interface FounderReviewDetail {
  application: FounderReviewApplication;
  tester: { email: string | null };
  qualification: FounderReviewQualification | null;
  report: FounderReviewReport | null;
  workouts: FounderReviewWorkout[];
  decision: FounderReviewDecision | null;
}

export const UNAVAILABLE = '—';

/** Matches the backend rejection reason limit. This is not a qualification rule. */
export const REJECTION_REASON_MAX = 2000;

export function rejectionReasonError(reason: string): string | null {
  const trimmed = reason.trim();
  if (!trimmed) {
    return 'Enter a rejection reason.';
  }
  if (reason.length > REJECTION_REASON_MAX || trimmed.length > REJECTION_REASON_MAX) {
    return `The rejection reason must be ${REJECTION_REASON_MAX} characters or fewer.`;
  }
  return null;
}

export function emailLabel(email: string | null | undefined): string {
  const trimmed = email?.trim();
  return trimmed ? trimmed : 'Email not available';
}

export function statusLabel(status: string | null | undefined): string {
  switch (status) {
    case 'ACTIVE_FREE':
      return 'Active';
    case 'ACTIVE_PRO':
      return 'Temporary Pro';
    case 'PENDING_APPROVAL':
      return 'Pending review';
    case 'EXPIRED':
      return 'Expired';
    case 'APPROVED':
      return 'Approved';
    case 'REJECTED':
      return 'Rejected';
    default: {
      const trimmed = status?.trim();
      return trimmed ? trimmed : 'Unknown status';
    }
  }
}

export function decisionLabel(decision: string | null | undefined): string {
  switch (decision) {
    case 'APPROVED':
      return 'Approved';
    case 'REJECTED':
      return 'Rejected';
    default: {
      const trimmed = decision?.trim();
      return trimmed ? trimmed : 'Recorded';
    }
  }
}

export function requirementLabel(value: boolean | null | undefined): string {
  if (value === true) {
    return 'Complete';
  }
  if (value === false) {
    return 'Incomplete';
  }
  return 'Not recorded';
}

export function temporaryProLabel(value: boolean | null | undefined): string {
  if (value === true) {
    return 'Reached';
  }
  if (value === false) {
    return 'Not reached';
  }
  return 'Not recorded';
}

export function countPair(actual: number | null | undefined, required: number | null | undefined): string {
  return `${actual == null ? UNAVAILABLE : actual} / ${required == null ? UNAVAILABLE : required}`;
}

export function recordedValue(value: string | number | null | undefined): string {
  if (value == null) {
    return 'Not recorded';
  }
  const text = String(value).trim();
  return text ? text : 'Not recorded';
}

export function windowLabel(days: number | null | undefined): string {
  if (days == null) {
    return 'Not recorded';
  }
  return days === 1 ? '1 day' : `${days} days`;
}

export function yesNo(value: boolean | null | undefined, yes: string, no: string): string {
  if (value === true) {
    return yes;
  }
  if (value === false) {
    return no;
  }
  return UNAVAILABLE;
}

export function formatInstant(value: string | null | undefined): string {
  if (!value) {
    return UNAVAILABLE;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return UNAVAILABLE;
  }
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

/** Calendar date stored for the workout. This is not an instant and is not shifted through UTC. */
export function formatLocalDate(value: string | null | undefined): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value ?? '');
  if (!match) {
    return UNAVAILABLE;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return UNAVAILABLE;
  }
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(date);
}

/** Human duration. Null stays unknown. 3672 seconds is 1h 1m. */
export function formatDuration(seconds: number | null | undefined): string | null {
  if (seconds == null || !Number.isFinite(seconds) || seconds < 0) {
    return null;
  }
  const total = Math.floor(seconds);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const remain = total % 60;
  if (hours === 0 && minutes === 0) {
    return `${remain}s`;
  }
  if (hours === 0) {
    return remain > 0 ? `${minutes}m ${remain}s` : `${minutes}m`;
  }
  return minutes > 0 ? `${hours}h ${minutes}m` : `${hours}h`;
}

export function sameInstant(left: string | null | undefined, right: string | null | undefined): boolean {
  if (!left || !right) {
    return false;
  }
  const first = new Date(left).getTime();
  const second = new Date(right).getTime();
  return Number.isFinite(first) && first === second;
}
