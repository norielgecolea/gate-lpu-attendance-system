import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type AuditPersonType = 'STUDENT' | 'EMPLOYEE';
export type AuditAction = 'CREATED' | 'UPDATED' | 'PHOTO_UPDATED' | 'DELETED';

export interface AuditLog {
  id: string;
  personType: AuditPersonType;
  personId: string;
  personName: string;
  personNo: string;
  action: AuditAction | string;
  actorUserId: number | null;
  actorUsername: string | null;
  createdAt: string;
}

export interface AuditLogPage {
  items: AuditLog[];
  total: number;
}

@Injectable({ providedIn: 'root' })
export class AuditLogsApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/audit-logs`;

  list(options: {
    date: string;
    personType?: AuditPersonType | '';
    offset?: number;
    limit?: number;
  }): Observable<AuditLogPage> {
    let params = new HttpParams().set('date', options.date);
    if (options.personType) {
      params = params.set('personType', options.personType);
    }
    if (options.offset != null) {
      params = params.set('offset', options.offset);
    }
    if (options.limit != null) {
      params = params.set('limit', options.limit);
    }
    return this.http.get<AuditLogPage>(this.baseUrl, { params });
  }
}
