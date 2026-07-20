import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface TapErrorLog {
  id: string;
  identifier: string;
  location: string | null;
  tappedAt: string;
}

@Injectable({ providedIn: 'root' })
export class TapErrorLogsApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/tap-errors`;

  list(limit = 500): Observable<TapErrorLog[]> {
    return this.http.get<TapErrorLog[]>(this.baseUrl, {
      params: { limit: String(limit) },
    });
  }

  clearAll(): Observable<{ message: string; deleted: number }> {
    return this.http.delete<{ message: string; deleted: number }>(this.baseUrl);
  }
}
