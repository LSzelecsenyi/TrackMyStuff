import { DOCUMENT } from '@angular/common';
import { effect, inject, Injectable, signal } from '@angular/core';

export type ThemeMode = 'system' | 'light' | 'dark';

const STORAGE_KEY = 'strict-admin-theme';

@Injectable({ providedIn: 'root' })
export class ThemeController {
  private readonly document = inject(DOCUMENT);
  readonly mode = signal<ThemeMode>(this.stored());

  constructor() {
    effect(() => {
      const mode = this.mode();
      this.document.documentElement.dataset['theme'] = mode;
      this.document.defaultView?.localStorage.setItem(STORAGE_KEY, mode);
    });
  }

  cycle(): void {
    const order: ThemeMode[] = ['system', 'light', 'dark'];
    const next = order[(order.indexOf(this.mode()) + 1) % order.length];
    this.mode.set(next);
  }

  label(): string {
    switch (this.mode()) {
      case 'light':
        return 'Light';
      case 'dark':
        return 'Dark';
      default:
        return 'System';
    }
  }

  cycleLabel(): string {
    const next = this.mode() === 'system' ? 'light' : this.mode() === 'light' ? 'dark' : 'system';
    return `Color theme, ${this.label()}. Switch to ${next}.`;
  }

  private stored(): ThemeMode {
    const value = this.document.defaultView?.localStorage.getItem(STORAGE_KEY);
    if (value === 'light' || value === 'dark' || value === 'system') {
      return value;
    }
    return 'system';
  }
}
