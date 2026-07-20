import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, concatMap, from, last, map, of, switchMap, tap, throwError } from 'rxjs';
import {
  StudentsApiService,
  type StudentPayload,
} from '../../core/students/students-api.service';
import type { StudentFormResult } from './student-form-dialog';

export interface Student {
  id: string;
  name: string;
  studentNo: string;
  photo?: string | null;
  rfid: string | null;
  birthdate?: string | null;
  department: string;
  course: string;
  school: string;
}

const PAGE_SIZE = 50;

@Injectable({ providedIn: 'root' })
export class StudentsStore {
  private readonly api = inject(StudentsApiService);

  readonly students = signal<Student[]>([]);
  readonly total = signal(0);
  readonly hasMore = signal(false);
  readonly loading = signal(false);
  readonly loadingMore = signal(false);
  readonly error = signal<string | null>(null);

  private searchTerm = '';

  /** Loads the first page for the given search term (server-side paging). */
  load(search: string = this.searchTerm): Observable<Student[]> {
    this.searchTerm = search;
    this.loading.set(true);
    this.error.set(null);
    return this.api.page(search, 0, PAGE_SIZE).pipe(
      map((page) => {
        this.students.set(page.items);
        this.total.set(page.total);
        this.hasMore.set(page.items.length < page.total);
        this.loading.set(false);
        return page.items;
      }),
      catchError((err: { error?: { message?: string } }) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Failed to load students');
        return throwError(() => err);
      }),
    );
  }

  /** Appends the next page when scrolled near the bottom. */
  loadMore(): void {
    if (this.loading() || this.loadingMore() || !this.hasMore()) {
      return;
    }
    this.loadingMore.set(true);
    this.api.page(this.searchTerm, this.students().length, PAGE_SIZE).subscribe({
      next: (page) => {
        this.students.update((list) => [...list, ...page.items]);
        this.total.set(page.total);
        this.hasMore.set(this.students().length < page.total);
        this.loadingMore.set(false);
      },
      error: () => this.loadingMore.set(false),
    });
  }

  getById(id: string): Student | undefined {
    return this.students().find((s) => s.id === id);
  }

  create(payload: StudentPayload): Observable<Student> {
    return this.api.create(this.normalizePayload(payload)).pipe(
      tap((student) => {
        this.students.update((list) => [...list, student].sort(byName));
        this.total.update((t) => t + 1);
      }),
    );
  }

  update(id: string, payload: StudentPayload): Observable<Student> {
    return this.api.update(id, this.normalizePayload(payload)).pipe(
      tap((student) =>
        this.students.update((list) =>
          list.map((s) => (s.id === id ? student : s)).sort(byName),
        ),
      ),
    );
  }

  /** Uploads a new photo file when present, then creates or updates the student. */
  saveFromForm(mode: 'create' | 'edit', form: StudentFormResult, studentId?: string): Observable<Student> {
    const upload$ = form.photoFile
      ? this.api.uploadPhoto(form.photoFile).pipe(map((res) => res.photo))
      : of(form.clearPhoto ? null : form.photo);

    return upload$.pipe(
      switchMap((photo) => {
        const payload: StudentPayload = {
          name: form.name,
          studentNo: form.studentNo,
          photo,
          rfid: form.rfid,
          birthdate: form.birthdate,
          department: form.department,
          course: form.course,
          school: form.school,
        };
        return mode === 'create' ? this.create(payload) : this.update(studentId!, payload);
      }),
    );
  }

  delete(id: string): Observable<unknown> {
    return this.api.delete(id).pipe(
      tap(() => {
        this.students.update((list) => list.filter((s) => s.id !== id));
        this.total.update((t) => Math.max(t - 1, 0));
      }),
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

  private normalizePayload(payload: StudentPayload): StudentPayload {
    return {
      ...payload,
      photo: payload.photo?.trim() ? payload.photo.trim() : null,
      rfid: payload.rfid?.trim() ? payload.rfid.trim() : null,
      birthdate: payload.birthdate?.trim() ? payload.birthdate.trim() : null,
      name: payload.name.trim(),
      studentNo: payload.studentNo.trim(),
      department: payload.department.trim(),
      course: payload.course.trim(),
      school: payload.school.trim(),
    };
  }
}

function byName(a: Student, b: Student): number {
  return a.name.localeCompare(b.name);
}
