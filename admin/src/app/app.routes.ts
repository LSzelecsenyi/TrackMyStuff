import { Routes } from '@angular/router';
import { AdminShell } from './shell/admin-shell';

export const routes: Routes = [
  {
    path: '',
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
