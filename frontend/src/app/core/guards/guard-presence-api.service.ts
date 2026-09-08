import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class GuardPresenceApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/guards`;

  online(): Observable<{ locations: string[]; kiosks: Record<string, string[]> }> {
    return this.http
      .get<{ locations: string[]; kiosks?: Record<string, string[]> }>(`${this.baseUrl}/online`)
      .pipe(
        map((res) => ({
          locations: res.locations ?? [],
          kiosks: res.kiosks ?? {},
        })),
      );
  }

  onlineLocations(): Observable<string[]> {
    return this.online().pipe(map((res) => res.locations));
  }
}
