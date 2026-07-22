import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { Employee } from '../../pages/employees/employees.store';

export type EmployeePayload = Omit<Employee, 'id'>;

export interface EmployeeImportResult {
  imported: number;
  updated: number;
  skippedDuplicates: number;
}

export interface PhotoBulkUploadResult {
  updated: number;
  notFound: number;
  skippedInvalid: number;
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

  create(payload: EmployeePayload): Observable<Employee> {
    return this.http.post<Employee>(this.baseUrl, payload);
  }

  importCsv(payload: EmployeePayload[]): Observable<EmployeeImportResult> {
    return this.http.post<EmployeeImportResult>(`${this.baseUrl}/import`, payload);
  }

  exportCsv(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/export`, { responseType: 'blob' });
  }

  bulkUploadPhotos(files: File[]): Observable<PhotoBulkUploadResult> {
    const formData = new FormData();
    for (const file of files) {
      formData.append('files', file, file.name);
    }
    return this.http.post<PhotoBulkUploadResult>(`${this.baseUrl}/photos/bulk`, formData);
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
    const formData = new FormData();
    formData.append('file', file, file.name);
    return this.http.post<{ photo: string }>(`${this.baseUrl}/photo`, formData);
  }
}
