import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type GateToneEvent = 'TIME_IN' | 'TIME_OUT' | 'ERROR' | 'FINANCE_TAGGED' | 'BIRTHDAY';

export interface GateTone {
  id: string;
  url: string;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface GateToneSettings {
  tones: GateTone[];
  /** Event type → tone id, or null for built-in default. */
  assignments: Partial<Record<GateToneEvent, string | null>>;
}

/** Resolves a stored tone path (/tones/...) to a browser URL under the WAR context. */
export function gateToneUrl(url: string): string {
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url;
  }
  if (url.startsWith(environment.contextPath)) {
    return url;
  }
  return `${environment.contextPath}${url.startsWith('/') ? url : `/${url}`}`;
}

@Injectable({ providedIn: 'root' })
export class GateTonesApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/gate-tones`;

  getSettings(): Observable<GateToneSettings> {
    return this.http.get<GateToneSettings>(this.baseUrl);
  }

  upload(files: File[]): Observable<GateTone[]> {
    const form = new FormData();
    for (const file of files) {
      form.append('files', file, file.name);
    }
    return this.http.post<GateTone[]>(this.baseUrl, form);
  }

  setAssignments(
    assignments: Partial<Record<GateToneEvent, string | null>>,
  ): Observable<GateToneSettings> {
    // Normalize null → "" so the backend always receives string values for every key.
    const body: Record<string, string> = {};
    for (const [key, value] of Object.entries(assignments)) {
      body[key] = value == null ? '' : String(value);
    }
    return this.http.put<GateToneSettings>(`${this.baseUrl}/assignments`, body);
  }

  delete(id: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/${id}`);
  }
}
