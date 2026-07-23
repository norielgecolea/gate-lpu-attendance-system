import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class GuardPresenceApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/guards`;

  onlineLocations(): Observable<string[]> {
    return this.http
      .get<{ locations: string[] }>(`${this.baseUrl}/online`)
      .pipe(map((res) => res.locations ?? []));
  }
}
