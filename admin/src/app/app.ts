import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AdminAuth } from './auth/admin-auth';
import { StrictState } from './shared/strict-state';

@Component({
  selector: 'strict-root',
  imports: [RouterOutlet, StrictState],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  readonly auth = inject(AdminAuth);

  constructor() {
    this.auth.whenReady().subscribe();
  }
}
