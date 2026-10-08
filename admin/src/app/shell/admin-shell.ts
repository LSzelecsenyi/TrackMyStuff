import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AdminAuth } from '../auth/admin-auth';
import { ThemeController } from '../theme/theme-controller';

@Component({
  selector: 'strict-admin-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './admin-shell.html',
  styleUrl: './admin-shell.css',
})
export class AdminShell {
  readonly theme = inject(ThemeController);
  readonly auth = inject(AdminAuth);
  readonly accountOpen = signal(false);

  toggleAccount(): void {
    this.accountOpen.update((open) => !open);
  }

  logout(): void {
    this.auth.logout().subscribe();
  }
}
