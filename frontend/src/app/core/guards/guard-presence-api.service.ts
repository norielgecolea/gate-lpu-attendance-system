import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { KioskPings } from '../auth/auth.models';

export interface GuardPresenceSnapshot {
  locations: string[];
  kiosks: Record<string, string[]>;
  pings: KioskPings;
}

@Injectable({ providedIn: 'root' })
export class GuardPresenceApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/guards`;

  online(): Observable<GuardPresenceSnapshot> {
    return this.http
      .get<{
        locations: string[];
        kiosks?: Record<string, string[]>;
        pings?: KioskPings;
      }>(`${this.baseUrl}/online`)
      .pipe(
        map((res) => ({
          locations: res.locations ?? [],
          kiosks: res.kiosks ?? {},
          pings: res.pings ?? {},
        })),
      );
  }

  onlineLocations(): Observable<string[]> {
    return this.online().pipe(map((res) => res.locations));
  }
}
