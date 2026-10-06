import { Component, inject } from '@angular/core';
import { StrictMark } from '../brand/strict-mark';
import { AdminAuth } from './admin-auth';

@Component({
  selector: 'strict-admin-login',
  imports: [StrictMark],
  templateUrl: './admin-login.html',
  styleUrl: './admin-login.css',
})
export class AdminLogin {
  readonly auth = inject(AdminAuth);

  signIn(): void {
    this.auth.signIn().subscribe();
  }
}
