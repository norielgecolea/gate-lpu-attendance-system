import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthEventMessage } from '../auth/auth.models';
import { GuardPresenceApiService } from '../guards/guard-presence-api.service';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly presenceApi = inject(GuardPresenceApiService);
  private socket: WebSocket | null = null;
  private token: string | null = null;
  private reconnectTimer?: ReturnType<typeof setTimeout>;
  private reconnectAttempt = 0;
  private intentionalClose = false;
  private readonly eventsSubject = new Subject<AuthEventMessage>();
  /** Prefer live WS presence over a slower/stale HTTP snapshot. */
  private presenceFromSocket = false;

  readonly events$ = this.eventsSubject.asObservable();
  readonly latestEvent = signal<AuthEventMessage | null>(null);
  readonly connected = signal(false);
  /** Gate locations with at least one connected guard kiosk. */
  readonly onlineGuardLocations = signal<string[]>([]);

  connect(token: string): void {
    if (!isPlatformBrowser(this.platformId) || !token) {
      return;
    }

    const state = this.socket?.readyState;
    if (
      this.token === token &&
      this.socket &&
      (state === WebSocket.OPEN || state === WebSocket.CONNECTING)
    ) {
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
    this.presenceFromSocket = false;
    this.onlineGuardLocations.set([]);
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

    this.presenceFromSocket = false;
    const url = `${environment.wsUrl}?token=${encodeURIComponent(this.token)}`;
    this.socket = new WebSocket(url);

    this.socket.onopen = () => {
      this.reconnectAttempt = 0;
      this.connected.set(true);
      if (!this.canReadGuardPresence(this.token)) {
        return;
      }
      // HTTP snapshot for admin/monitor; guards are not allowed on this endpoint.
      this.presenceApi.onlineLocations().subscribe({
        next: (locations) => {
          if (!this.presenceFromSocket) {
            this.onlineGuardLocations.set(locations);
          }
        },
        error: () => undefined,
      });
    };

    this.socket.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data as string) as AuthEventMessage;
        this.latestEvent.set(payload);
        this.eventsSubject.next(payload);
        if (payload.type === 'GUARD_PRESENCE' && Array.isArray(payload.locations)) {
          this.presenceFromSocket = true;
          this.onlineGuardLocations.set(
            payload.locations
              .map((loc) => String(loc).trim())
              .filter((loc) => loc.length > 0),
          );
        }
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

  private canReadGuardPresence(token: string | null): boolean {
    if (!token) {
      return false;
    }
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))) as {
        role?: string;
      };
      const role = payload.role ?? '';
      return role === 'SUPERADMIN' || role === 'MONITORING' || role === 'OSAS' || role === 'HR';
    } catch {
      return false;
    }
  }
}
