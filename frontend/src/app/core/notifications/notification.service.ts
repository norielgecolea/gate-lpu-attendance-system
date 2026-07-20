import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthEventMessage } from '../auth/auth.models';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly platformId = inject(PLATFORM_ID);
  private socket: WebSocket | null = null;
  private token: string | null = null;
  private reconnectTimer?: ReturnType<typeof setTimeout>;
  private reconnectAttempt = 0;
  private intentionalClose = false;
  private readonly eventsSubject = new Subject<AuthEventMessage>();

  readonly events$ = this.eventsSubject.asObservable();
  readonly latestEvent = signal<AuthEventMessage | null>(null);
  readonly connected = signal(false);

  connect(token: string): void {
    if (!isPlatformBrowser(this.platformId) || !token) {
      return;
    }

    this.token = token;
    this.intentionalClose = false;
    this.clearReconnect();
    this.openSocket();
  }

  disconnect(): void {
    this.intentionalClose = true;
    this.clearReconnect();
    this.token = null;
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
    this.connected.set(false);
  }

  dismissLatest(): void {
    this.latestEvent.set(null);
  }

  private openSocket(): void {
    if (!this.token) {
      return;
    }

    if (this.socket) {
      this.socket.onclose = null;
      this.socket.onerror = null;
      this.socket.onmessage = null;
      this.socket.onopen = null;
      this.socket.close();
      this.socket = null;
    }

    const url = `${environment.wsUrl}?token=${encodeURIComponent(this.token)}`;
    this.socket = new WebSocket(url);

    this.socket.onopen = () => {
      this.reconnectAttempt = 0;
      this.connected.set(true);
    };

    this.socket.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data as string) as AuthEventMessage;
        this.latestEvent.set(payload);
        this.eventsSubject.next(payload);
      } catch {
        // ignore malformed payloads
      }
    };

    this.socket.onclose = () => {
      this.connected.set(false);
      this.socket = null;
      this.scheduleReconnect();
    };

    this.socket.onerror = () => {
      this.connected.set(false);
    };
  }

  private scheduleReconnect(): void {
    if (this.intentionalClose || !this.token) {
      return;
    }
    this.clearReconnect();
    const delay = Math.min(1000 * 2 ** this.reconnectAttempt, 15000);
    this.reconnectAttempt += 1;
    this.reconnectTimer = setTimeout(() => this.openSocket(), delay);
  }

  private clearReconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = undefined;
    }
  }
}
