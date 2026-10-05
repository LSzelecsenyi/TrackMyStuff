import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { StrictMark } from '../brand/strict-mark';
import { ThemeController } from '../theme/theme-controller';

@Component({
  selector: 'strict-admin-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, StrictMark],
  templateUrl: './admin-shell.html',
  styleUrl: './admin-shell.css',
})
export class AdminShell {
  readonly theme = inject(ThemeController);
  readonly accountOpen = signal(false);

  toggleAccount(): void {
    this.accountOpen.update((open) => !open);
  }
}
