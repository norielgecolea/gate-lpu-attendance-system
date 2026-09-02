import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, from, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  postPhotoChunk,
  uploadPhotosInChunks,
  type PhotoBulkUploadProgress,
  type PhotoBulkUploadResult,
} from '../media/bulk-photo-upload';
import { compressImageFile } from '../media/compress-image';
import type { Employee } from '../../pages/employees/employees.store';

export type { PhotoBulkUploadProgress, PhotoBulkUploadResult } from '../media/bulk-photo-upload';

export type EmployeePayload = Omit<Employee, 'id'>;
export interface EmployeeImportPayload {
  employeeNo: string;
  name?: string | null;
  photo?: string | null;
  rfid?: string | null;
  birthdate?: string | null;
  lpuEmail?: string | null;
  department?: string | null;
  position?: string | null;
}

export interface EmployeeImportResult {
  imported: number;
  updated: number;
  skippedDuplicates: number;
  skippedIncomplete: number;
}

export interface EmployeeAuditEvent {
  id: string;
  action: string;
  actorUserId: number | null;
  actorUsername: string | null;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class EmployeesApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/employees`;

  list(): Observable<Employee[]> {
    return this.http.get<Employee[]>(this.baseUrl);
  }

  listInactive(): Observable<Employee[]> {
    return this.http.get<Employee[]>(`${this.baseUrl}/inactive`);
  }

  restore(id: string): Observable<Employee> {
    return this.http.post<Employee>(`${this.baseUrl}/${id}/restore`, {});
  }

  getById(id: string): Observable<Employee> {
    return this.http.get<Employee>(`${this.baseUrl}/${id}`);
  }

  getAuditEvents(id: string): Observable<EmployeeAuditEvent[]> {
    return this.http.get<EmployeeAuditEvent[]>(`${this.baseUrl}/${id}/audit`);
  }

  create(payload: EmployeePayload): Observable<Employee> {
    return this.http.post<Employee>(this.baseUrl, payload);
  }

  importCsv(payload: EmployeeImportPayload[]): Observable<EmployeeImportResult> {
    return this.http.post<EmployeeImportResult>(`${this.baseUrl}/import`, payload);
  }

  exportCsv(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/export`, { responseType: 'blob' });
  }

  bulkUploadPhotos(
    files: File[],
    onProgress?: (progress: PhotoBulkUploadProgress) => void,
  ): Observable<PhotoBulkUploadResult> {
    return uploadPhotosInChunks(
      files,
      (chunk, onHttpProgress) =>
        postPhotoChunk(this.http, `${this.baseUrl}/photos/bulk`, chunk, onHttpProgress),
      onProgress,
    );
  }

  update(id: string, payload: EmployeePayload): Observable<Employee> {
    return this.http.put<Employee>(`${this.baseUrl}/${id}`, payload);
  }

  delete(id: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/${id}`);
  }

  permanentlyDelete(id: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/${id}/permanent`);
  }

  uploadPhoto(file: File): Observable<{ photo: string }> {
    return from(compressImageFile(file)).pipe(
      switchMap((compressed) => {
        const formData = new FormData();
        formData.append('file', compressed, compressed.name);
        return this.http.post<{ photo: string }>(`${this.baseUrl}/photo`, formData);
      }),
    );
  }
}
