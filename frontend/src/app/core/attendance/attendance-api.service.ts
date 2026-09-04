import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { Employee } from '../../pages/employees/employees.store';
import type { Student } from '../../pages/students/students.store';

export type PersonType = 'STUDENT' | 'EMPLOYEE';
export type AttendancePersonFilter = PersonType | 'ALL';
export type KioskGroup = 'MAIN_GATES' | 'LIBRARY' | 'OLIVE_HOTEL';

export interface TapResponse {
  action: 'TIME_IN' | 'TIME_OUT' | string;
  message: string;
  attendanceId: string;
  attendanceDate: string;
  timeIn: string;
  timeOut: string | null;
  location?: string | null;
  timeInLocation?: string | null;
  timeOutLocation?: string | null;
  birthday?: boolean;
  financeTagged?: boolean;
  warningMessage?: string | null;
  personType?: PersonType | string;
  kioskGroup?: KioskGroup | string;
  student?: Student | null;
  employee?: Employee | null;
}

export interface AttendanceDailyRecord {
  id: string;
  personType: PersonType | string;
  personId: string;
  name: string;
  personNo: string;
  photo?: string | null;
  department: string;
  course?: string | null;
  school?: string | null;
  position?: string | null;
  attendanceDate: string;
  timeIn: string;
  timeOut: string | null;
  timeInLocation?: string | null;
  timeOutLocation?: string | null;
  tapCount: number;
  status: 'COMPLETE' | 'OPEN' | string;
  lastAction: string;
}

export interface AttendanceEventRecord {
  id: string;
  personType: PersonType | string;
  personId: string;
  name: string;
  personNo: string;
  attendanceDate: string;
  action: 'TIME_IN' | 'TIME_OUT' | string;
  tappedAt: string;
  location?: string | null;
  tappedByUserId?: number | null;
  tappedByUsername?: string | null;
}

export interface AttendancePage {
  items: AttendanceDailyRecord[];
  total: number;
}

export interface AttendanceEventPage {
  items: AttendanceEventRecord[];
  total: number;
}

export interface AttendanceSummary {
  uniquePeople: number;
  completeDays: number;
  openDays: number;
  totalTaps: number;
  /** People whose latest tap today is a TIME_IN (physically inside right now). */
  currentlyIn: number;
}

export interface AttendanceDepartmentCount {
  department: string;
  count: number;
}

export interface AttendanceHourCount {
  hour: number;
  timeIn: number;
  timeOut: number;
}

export interface PersonAttendanceSummary {
  daysPresent: number;
  completeDays: number;
  openDays: number;
  totalTaps: number;
  firstDate: string | null;
  latestDate: string | null;
}

export interface AttendanceQuery {
  personType?: AttendancePersonFilter;
  personId?: string;
  startDate?: string;
  endDate?: string;
  search?: string;
  department?: string;
  location?: string;
  status?: string;
  action?: string;
  kioskGroup?: KioskGroup;
  sortBy?: string;
  sortDir?: string;
  offset?: number;
  limit?: number;
}

@Injectable({ providedIn: 'root' })
export class AttendanceApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/attendance`;
  private readonly studentsUrl = `${environment.apiBaseUrl}/students`;
  private readonly employeesUrl = `${environment.apiBaseUrl}/employees`;

  tap(identifier: string): Observable<TapResponse> {
    return this.http.post<TapResponse>(`${this.baseUrl}/tap`, { identifier });
  }

  recent(limit = 20, offset = 0): Observable<TapResponse[]> {
    return this.http.get<TapResponse[]>(`${this.baseUrl}/recent`, {
      params: { limit: String(limit), offset: String(offset) },
    });
  }

  page(query: AttendanceQuery): Observable<AttendancePage> {
    return this.http.get<AttendancePage>(this.baseUrl, { params: this.toParams(query) });
  }

  summary(query: AttendanceQuery): Observable<AttendanceSummary> {
    return this.http.get<AttendanceSummary>(`${this.baseUrl}/summary`, {
      params: this.toParams(query),
    });
  }

  byHour(date?: string): Observable<AttendanceHourCount[]> {
    let params = new HttpParams();
    if (date) {
      params = params.set('date', date);
    }
    return this.http.get<AttendanceHourCount[]>(`${this.baseUrl}/by-hour`, { params });
  }

  byDepartment(
    personType: AttendancePersonFilter,
    startDate?: string,
    endDate?: string,
    kioskGroup?: KioskGroup,
  ): Observable<AttendanceDepartmentCount[]> {
    let params = new HttpParams().set('personType', personType);
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    if (kioskGroup) params = params.set('kioskGroup', kioskGroup);
    return this.http.get<AttendanceDepartmentCount[]>(`${this.baseUrl}/by-department`, { params });
  }

  events(query: AttendanceQuery): Observable<AttendanceEventPage> {
    return this.http.get<AttendanceEventPage>(`${this.baseUrl}/events`, {
      params: this.toParams(query),
    });
  }

  locations(personType: PersonType, startDate?: string, endDate?: string): Observable<string[]> {
    let params = new HttpParams().set('personType', personType);
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    return this.http.get<string[]>(`${this.baseUrl}/locations`, { params });
  }

  exportCsv(query: AttendanceQuery): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/export`, {
      params: this.toParams(query),
      responseType: 'blob',
    });
  }

  personSummary(
    personType: PersonType,
    personId: string,
    startDate?: string,
    endDate?: string,
  ): Observable<PersonAttendanceSummary> {
    const base = personType === 'STUDENT' ? this.studentsUrl : this.employeesUrl;
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    return this.http.get<PersonAttendanceSummary>(`${base}/${personId}/attendance/summary`, {
      params,
    });
  }

  personDaily(
    personType: PersonType,
    personId: string,
    query: Omit<AttendanceQuery, 'personType' | 'personId'>,
  ): Observable<AttendancePage> {
    const base = personType === 'STUDENT' ? this.studentsUrl : this.employeesUrl;
    return this.http.get<AttendancePage>(`${base}/${personId}/attendance`, {
      params: this.toParams({ ...query, personType }),
    });
  }

  personEvents(
    personType: PersonType,
    personId: string,
    query: Omit<AttendanceQuery, 'personType' | 'personId'>,
  ): Observable<AttendanceEventPage> {
    const base = personType === 'STUDENT' ? this.studentsUrl : this.employeesUrl;
    return this.http.get<AttendanceEventPage>(`${base}/${personId}/attendance/events`, {
      params: this.toParams({ ...query, personType }),
    });
  }

  exportPersonEvents(
    personType: PersonType,
    personId: string,
    query: Omit<AttendanceQuery, 'personType' | 'personId'>,
  ): Observable<Blob> {
    const base = personType === 'STUDENT' ? this.studentsUrl : this.employeesUrl;
    return this.http.get(`${base}/${personId}/attendance/export`, {
      params: this.toParams({ ...query, personType }),
      responseType: 'blob',
    });
  }

  private toParams(query: AttendanceQuery): HttpParams {
    let params = new HttpParams();
    if (query.personType) {
      params = params.set('personType', query.personType);
    }
    const entries: [string, string | number | undefined][] = [
      ['personId', query.personId],
      ['startDate', query.startDate],
      ['endDate', query.endDate],
      ['search', query.search],
      ['department', query.department],
      ['location', query.location],
      ['status', query.status],
      ['action', query.action],
      ['kioskGroup', query.kioskGroup],
      ['sortBy', query.sortBy],
      ['sortDir', query.sortDir],
      ['offset', query.offset],
      ['limit', query.limit],
    ];
    for (const [key, value] of entries) {
      if (value !== undefined && value !== null && `${value}` !== '') {
        params = params.set(key, String(value));
      }
    }
    return params;
  }
}
