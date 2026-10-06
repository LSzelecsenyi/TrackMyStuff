import { Routes } from '@angular/router';
import { adminAuthGuard, adminGuestGuard } from './auth/admin-auth.guard';
import { AdminShell } from './shell/admin-shell';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [adminGuestGuard],
    loadComponent: () => import('./auth/admin-login').then((module) => module.AdminLogin),
  },
  {
    path: '',
    canActivate: [adminAuthGuard],
    component: AdminShell,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'founders' },
      {
        path: 'founders',
        loadComponent: () => import('./founders/founder-list').then((module) => module.FounderList),
      },
      {
        path: 'founders/:applicationId',
        loadComponent: () => import('./founders/founder-detail').then((module) => module.FounderDetail),
      },
    ],
  },
];
