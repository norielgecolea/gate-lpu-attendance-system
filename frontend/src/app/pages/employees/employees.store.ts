import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, concatMap, from, last, map, of, switchMap, tap, throwError } from 'rxjs';
import {
  EmployeesApiService,
  type EmployeePayload,
} from '../../core/employees/employees-api.service';
import type { EmployeeFormResult } from './employee-form-dialog';

export interface Employee {
  id: string;
  name: string;
  employeeNo: string;
  photo?: string | null;
  rfid: string | null;
  birthdate?: string | null;
  lpuEmail?: string | null;
  department: string | null;
  position: string | null;
}

@Injectable({ providedIn: 'root' })
export class EmployeesStore {
  private readonly api = inject(EmployeesApiService);

  readonly employees = signal<Employee[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  load(): Observable<Employee[]> {
    this.loading.set(true);
    this.error.set(null);
    return this.api.list().pipe(
      tap((employees) => {
        this.employees.set(employees);
        this.loading.set(false);
      }),
      catchError((err: { error?: { message?: string } }) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Failed to load employees');
        return throwError(() => err);
      }),
    );
  }

  create(payload: EmployeePayload): Observable<Employee> {
    return this.api.create(this.normalizePayload(payload)).pipe(
      tap((employee) => this.employees.update((list) => [...list, employee].sort(byName))),
    );
  }

  update(id: string, payload: EmployeePayload): Observable<Employee> {
    return this.api.update(id, this.normalizePayload(payload)).pipe(
      tap((employee) =>
        this.employees.update((list) =>
          list.map((e) => (e.id === id ? employee : e)).sort(byName),
        ),
      ),
    );
  }

  /** Uploads a new photo file when present, then creates or updates the employee. */
  saveFromForm(
    mode: 'create' | 'edit',
    form: EmployeeFormResult,
    employeeId?: string,
  ): Observable<Employee> {
    const upload$ = form.photoFile
      ? this.api.uploadPhoto(form.photoFile).pipe(map((res) => res.photo))
      : of(form.clearPhoto ? null : form.photo);

    return upload$.pipe(
      switchMap((photo) => {
        const payload: EmployeePayload = {
          name: form.name,
          employeeNo: form.employeeNo,
          photo,
          rfid: form.rfid,
          birthdate: form.birthdate,
          lpuEmail: form.lpuEmail,
          department: form.department,
          position: form.position,
        };
        return mode === 'create' ? this.create(payload) : this.update(employeeId!, payload);
      }),
    );
  }

  delete(id: string): Observable<unknown> {
    return this.api.delete(id).pipe(
      tap(() => this.employees.update((list) => list.filter((e) => e.id !== id))),
    );
  }

  deleteMany(ids: string[]): Observable<unknown> {
    if (ids.length === 0) {
      return of(null);
    }
    return from(ids).pipe(
      concatMap((id) => this.delete(id)),
      last(),
    );
  }

  private normalizePayload(payload: EmployeePayload): EmployeePayload {
    return {
      ...payload,
      photo: payload.photo?.trim() ? payload.photo.trim() : null,
      rfid: payload.rfid?.trim() ? payload.rfid.trim() : null,
      birthdate: payload.birthdate?.trim() ? payload.birthdate.trim() : null,
      lpuEmail: payload.lpuEmail?.trim() ? payload.lpuEmail.trim() : null,
      name: payload.name.trim(),
      employeeNo: payload.employeeNo.trim(),
      department: payload.department?.trim() ? payload.department.trim() : null,
      position: payload.position?.trim() ? payload.position.trim() : null,
    };
  }
}

function byName(a: Employee, b: Employee): number {
  return a.name.localeCompare(b.name);
}
