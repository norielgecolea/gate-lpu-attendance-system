import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type GuardDisplayMode = 'RECENT_TAPS' | 'VIDEO' | 'NONE';

export interface GuardVideo {
  id: string;
  url: string;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface GuardDisplaySettings {
  mode: GuardDisplayMode;
  videos: GuardVideo[];
}

/** Resolves a stored video path (/videos/...) to a browser URL under the WAR context. */
export function guardVideoUrl(url: string): string {
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url;
  }
  if (url.startsWith(environment.contextPath)) {
    return url;
  }
  return `${environment.contextPath}${url.startsWith('/') ? url : `/${url}`}`;
}

@Injectable({ providedIn: 'root' })
export class GuardDisplayApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/guard-display`;

  getDisplay(): Observable<GuardDisplaySettings> {
    return this.http.get<GuardDisplaySettings>(this.baseUrl);
  }

  setMode(mode: GuardDisplayMode): Observable<GuardDisplaySettings> {
    return this.http.put<GuardDisplaySettings>(`${this.baseUrl}/mode`, { mode });
  }

  uploadVideos(files: File[]): Observable<GuardVideo[]> {
    const form = new FormData();
    for (const file of files) {
      form.append('files', file, file.name);
    }
    return this.http.post<GuardVideo[]>(`${this.baseUrl}/videos`, form);
  }

  deleteVideo(id: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/videos/${id}`);
  }
}
