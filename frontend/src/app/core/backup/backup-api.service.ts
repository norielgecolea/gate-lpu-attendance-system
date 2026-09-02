import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface BackupRestoreResult {
  databaseRestored: boolean;
  picturesCopied: number;
  videosCopied: number;
  tonesCopied: number;
}

export interface PhotoCleanupResult {
  referenced: number;
  onDisk: number;
  unused: number;
  deleted: number;
  failed: number;
}

@Injectable({ providedIn: 'root' })
export class BackupApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/backup`;

  download(): Observable<Blob> {
    return this.http.get(this.baseUrl, { responseType: 'blob' });
  }

  restore(file: File): Observable<BackupRestoreResult> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<BackupRestoreResult>(`${this.baseUrl}/restore`, form);
  }

  unusedPictures(): Observable<PhotoCleanupResult> {
    return this.http.get<PhotoCleanupResult>(`${this.baseUrl}/pictures/unused`);
  }

  cleanupUnusedPictures(): Observable<PhotoCleanupResult> {
    return this.http.post<PhotoCleanupResult>(`${this.baseUrl}/pictures/cleanup`, {});
  }
}

export async function backupErrorMessage(err: unknown, fallback: string): Promise<string> {
  if (!(err instanceof HttpErrorResponse)) {
    return fallback;
  }
  if (err.error instanceof Blob) {
    const text = await err.error.text();
    try {
      const parsed = JSON.parse(text) as { message?: string };
      return parsed.message || fallback;
    } catch {
      return text || err.message || fallback;
    }
  }
  const body = err.error as { message?: string } | null;
  return body?.message || err.message || fallback;
}
