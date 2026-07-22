import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { Student } from '../../pages/students/students.store';

export type StudentPayload = Omit<Student, 'id' | 'financeTagged'>;
export interface StudentImportResult {
  imported: number;
  updated: number;
  skippedDuplicates: number;
}
export interface PhotoBulkUploadResult {
  updated: number;
  notFound: number;
  skippedInvalid: number;
}
export interface StudentFinanceTagImportResult {
  tagged: number;
  alreadyTagged: number;
  notFound: number;
}
export interface StudentPage {
  items: Student[];
  total: number;
}

@Injectable({ providedIn: 'root' })
export class StudentsApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/students`;

  list(): Observable<Student[]> {
    return this.http.get<Student[]>(this.baseUrl);
  }

  /** Server-side paged + searched listing so large tables are never fetched whole. */
  page(search: string, offset: number, limit: number): Observable<StudentPage> {
    const params = new HttpParams()
      .set('search', search)
      .set('offset', offset)
      .set('limit', limit);
    return this.http.get<StudentPage>(`${this.baseUrl}/page`, { params });
  }

  listInactive(): Observable<Student[]> {
    return this.http.get<Student[]>(`${this.baseUrl}/inactive`);
  }

  listFinanceTagged(): Observable<Student[]> {
    return this.http.get<Student[]>(`${this.baseUrl}/finance-tagged`);
  }

  restore(id: string): Observable<Student> {
    return this.http.post<Student>(`${this.baseUrl}/${id}/restore`, {});
  }

  getById(id: string): Observable<Student> {
    return this.http.get<Student>(`${this.baseUrl}/${id}`);
  }

  create(payload: StudentPayload): Observable<Student> {
    return this.http.post<Student>(this.baseUrl, payload);
  }

  importCsv(payload: StudentPayload[]): Observable<StudentImportResult> {
    return this.http.post<StudentImportResult>(`${this.baseUrl}/import`, payload);
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

  importFinanceTagged(studentNumbers: string[]): Observable<StudentFinanceTagImportResult> {
    return this.http.post<StudentFinanceTagImportResult>(
      `${this.baseUrl}/finance-tagged/import`,
      studentNumbers,
    );
  }

  financeTag(id: string): Observable<Student> {
    return this.http.post<Student>(`${this.baseUrl}/${id}/finance-tagged`, {});
  }

  financeUntag(id: string): Observable<Student> {
    return this.http.delete<Student>(`${this.baseUrl}/${id}/finance-tagged`);
  }

  update(id: string, payload: StudentPayload): Observable<Student> {
    return this.http.put<Student>(`${this.baseUrl}/${id}`, payload);
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
