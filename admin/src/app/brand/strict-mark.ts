import { Component, input } from '@angular/core';

@Component({
  selector: 'strict-mark',
  template: `
    <svg
      class="mark"
      [attr.viewBox]="'0 0 108 108'"
      [attr.aria-hidden]="decorative() ? 'true' : null"
      [attr.role]="decorative() ? null : 'img'"
      [attr.aria-label]="decorative() ? null : 'Strict'"
    >
      <path
        fill="currentColor"
        d="M54,28c-2.2,0 -4,1.8 -4,4v6H38c-3.3,0 -6,2.7 -6,6v8c0,11.6 9.4,22 22,22s22,-10.4 22,-22v-8c0,-3.3 -2.7,-6 -6,-6H58v-6C58,29.8 56.2,28 54,28zM38,44h32v8c0,8.8 -7.2,16 -16,16s-16,-7.2 -16,-16v-8z"
      />
      <path fill="currentColor" d="M50,52h8v4h-8z" />
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      width: 36px;
      height: 36px;
      flex: 0 0 auto;
      align-items: center;
      justify-content: center;
      border-radius: var(--strict-radius-compact);
      background: var(--strict-primary);
      color: var(--strict-on-primary);
    }

    .mark {
      width: 28px;
      height: 28px;
    }
  `,
})
export class StrictMark {
  readonly decorative = input(true);
}
