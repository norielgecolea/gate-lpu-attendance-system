import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { environment } from '../../../environments/environment';

export interface ServerTimeResponse {
  serverTime: string;
  zoneId: string;
  utcOffset: string;
}

/** Keeps displayed clocks aligned with the Tomcat host, not the kiosk browser. */
@Injectable({ providedIn: 'root' })
export class ServerClockService {
  private static readonly SYNC_MS = 60_000;

  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);

  readonly now = signal(new Date());
  readonly zoneId = signal('Asia/Manila');
  /** Angular DatePipe offset, e.g. +0800 */
  readonly datePipeTimezone = signal('+0800');

  private skewMs = 0;
  private started = false;

  constructor() {
    this.start();
  }

  start(): void {
    if (this.started || !isPlatformBrowser(this.platformId)) {
      return;
    }
    this.started = true;
    this.tick();
    this.sync();
    setInterval(() => this.tick(), 1000);
    setInterval(() => this.sync(), ServerClockService.SYNC_MS);
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        this.sync();
      }
    });
  }

  todayIso(): string {
    return this.now().toLocaleDateString('en-CA', { timeZone: this.zoneId() });
  }

  private tick(): void {
    this.now.set(new Date(Date.now() + this.skewMs));
  }

  private sync(): void {
    const sentAt = Date.now();
    this.http.get<ServerTimeResponse>(`${environment.apiBaseUrl}/kiosk/time`).subscribe({
      next: (res) => {
        const receivedAt = Date.now();
        const serverEpoch = Date.parse(res.serverTime);
        if (Number.isNaN(serverEpoch)) {
          return;
        }
        this.skewMs = serverEpoch + Math.floor((receivedAt - sentAt) / 2) - receivedAt;
        if (res.zoneId) {
          this.zoneId.set(res.zoneId);
        }
        this.datePipeTimezone.set(toDatePipeTimezone(res.utcOffset, res.zoneId));
        this.tick();
      },
      error: () => undefined,
    });
  }
}

function toDatePipeTimezone(utcOffset?: string, zoneId?: string): string {
  if (utcOffset === 'Z' || utcOffset === 'z') {
    return '+0000';
  }
  if (utcOffset) {
    const compact = utcOffset.replace(':', '');
    if (/^[+-]\d{4}$/.test(compact)) {
      return compact;
    }
  }
  if (zoneId) {
    return zoneId;
  }
  return '+0800';
}
