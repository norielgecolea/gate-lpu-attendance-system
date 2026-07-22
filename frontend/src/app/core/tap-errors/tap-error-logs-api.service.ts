import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
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

  list(options?: { limit?: number; date?: string }): Observable<TapErrorLog[]> {
    let params = new HttpParams().set('limit', String(options?.limit ?? 500));
    if (options?.date) {
      params = params.set('date', options.date);
    }
    return this.http.get<TapErrorLog[]>(this.baseUrl, { params });
  }

  count(date?: string): Observable<number> {
    let params = new HttpParams();
    if (date) {
      params = params.set('date', date);
    }
    return this.http
      .get<{ count: number }>(`${this.baseUrl}/count`, { params })
      .pipe(map((res) => res.count));
  }

  clear(date?: string): Observable<{ message: string; deleted: number }> {
    let params = new HttpParams();
    if (date) {
      params = params.set('date', date);
    }
    return this.http.delete<{ message: string; deleted: number }>(this.baseUrl, { params });
  }
}
