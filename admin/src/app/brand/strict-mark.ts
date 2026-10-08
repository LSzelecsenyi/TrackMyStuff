import { Component, input } from '@angular/core';

@Component({
  selector: 'strict-mark',
  template: `
    <img
      class="mark"
      src="strict-symbol-dark.png"
      [attr.alt]="decorative() ? '' : 'Strict'"
    />
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
      background: var(--strict-navy);
    }

    .mark {
      width: 78%;
      height: 78%;
      object-fit: contain;
    }
  `,
})
export class StrictMark {
  readonly decorative = input(true);
}
