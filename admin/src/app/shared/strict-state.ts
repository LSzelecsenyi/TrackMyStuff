import { Component, input } from '@angular/core';

export type PageKind = 'loading' | 'empty' | 'error' | 'content';

@Component({
  selector: 'strict-state',
  template: `
    <div
      class="state"
      [class.state-error]="kind() === 'error'"
      [attr.role]="kind() === 'error' ? 'alert' : kind() === 'content' ? null : 'status'"
      [attr.aria-live]="kind() === 'loading' ? 'polite' : null"
      [attr.aria-busy]="kind() === 'loading' ? 'true' : null"
    >
      @if (kind() === 'loading') {
        <span class="skeleton-line"></span>
        <span class="skeleton-line short"></span>
        <span class="visually-hidden">Loading</span>
      } @else {
        <ng-content />
      }
    </div>
  `,
})
export class StrictState {
  readonly kind = input.required<PageKind>();
}
