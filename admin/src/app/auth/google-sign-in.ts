import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GOOGLE_CLIENT_ID } from '../api/admin-api';

const GOOGLE_IDENTITY_SCRIPT = 'https://accounts.google.com/gsi/client';

interface GoogleCredentialResponse {
  credential?: string;
}

interface GooglePromptNotification {
  isNotDisplayed: () => boolean;
  isSkippedMoment: () => boolean;
}

interface GoogleAccountsId {
  initialize(config: {
    client_id: string;
    callback: (response: GoogleCredentialResponse) => void;
    auto_select: boolean;
    cancel_on_tap_outside: boolean;
  }): void;
  prompt(listener?: (notification: GooglePromptNotification) => void): void;
}

/**
 * Google Identity Services.
 * The returned value is a Google ID token, not an admin session.
 * The backend decides whether that identity may open an admin session.
 */
@Injectable({ providedIn: 'root' })
export class GoogleSignIn {
  private readonly document = inject(DOCUMENT);
  private readonly clientId = inject(GOOGLE_CLIENT_ID);
  private script: Promise<void> | null = null;

  requestIdToken(): Observable<string> {
    return new Observable((subscriber) => {
      if (!this.clientId) {
        subscriber.error(new Error('Google sign-in is not configured.'));
        return;
      }
      let active = true;
      this.load()
        .then(() => {
          if (!active) {
            return;
          }
          const identity = this.identity();
          if (!identity) {
            subscriber.error(new Error('Google sign-in did not start.'));
            return;
          }
          identity.initialize({
            client_id: this.clientId,
            auto_select: false,
            cancel_on_tap_outside: true,
            callback: (response) => {
              if (!active) {
                return;
              }
              if (response.credential) {
                subscriber.next(response.credential);
                subscriber.complete();
              } else {
                subscriber.error(new Error('Google sign-in did not start.'));
              }
            },
          });
          identity.prompt((notification) => {
            if (!active) {
              return;
            }
            if (notification.isNotDisplayed() || notification.isSkippedMoment()) {
              subscriber.error(new Error('Google sign-in did not start.'));
            }
          });
        })
        .catch((error: unknown) => subscriber.error(error));
      return () => {
        active = false;
      };
    });
  }

  private identity(): GoogleAccountsId | null {
    const google = (this.document.defaultView as Window & { google?: { accounts?: { id?: GoogleAccountsId } } } | null)
      ?.google;
    return google?.accounts?.id ?? null;
  }

  private load(): Promise<void> {
    if (this.identity()) {
      return Promise.resolve();
    }
    this.script ??= new Promise((resolve, reject) => {
      const script = this.document.createElement('script');
      script.src = GOOGLE_IDENTITY_SCRIPT;
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error('Google sign-in did not start.'));
      this.document.head.appendChild(script);
    });
    return this.script;
  }
}
