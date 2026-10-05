import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { StrictState } from '../shared/strict-state';

const APPLICATION_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

@Component({
  selector: 'strict-founder-detail',
  imports: [StrictState, RouterLink],
  templateUrl: './founder-detail.html',
  styleUrl: './founder-detail.css',
})
export class FounderDetail {
  private readonly route = inject(ActivatedRoute);

  readonly applicationId = toSignal(
    this.route.paramMap.pipe(map((params) => params.get('applicationId') ?? '')),
    { initialValue: this.route.snapshot.paramMap.get('applicationId') ?? '' },
  );

  readonly valid = computed(() => APPLICATION_ID.test(this.applicationId()));

  readonly sections = [
    { id: 'status', title: 'Status', text: 'Application status will appear here.' },
    { id: 'window', title: 'Enrollment and deadline', text: 'Enrollment and deadline will appear here.' },
    { id: 'progress', title: 'Qualification progress', text: 'Workout and distinct-day progress will appear here.' },
    { id: 'analytics', title: 'Tester analytics', text: 'The rules and workout observations used for review will appear here.' },
    { id: 'workouts', title: 'Qualifying workouts', text: 'Accepted qualifying workouts will appear here.' },
    { id: 'feedback', title: 'Written feedback', text: 'The tester’s written feedback will appear here.' },
    { id: 'client', title: 'App version and platform', text: 'App version and platform will appear here.' },
  ] as const;
}
