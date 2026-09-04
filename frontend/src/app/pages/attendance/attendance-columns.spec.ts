import { describe, expect, it } from 'vitest';
import type { ColumnDef } from '@tanstack/angular-table';
import type { AttendanceDailyRecord } from '../../core/attendance/attendance-api.service';

function visibleColumns(mode: 'student' | 'employee' | 'combined'): string[] {
  const columns: ColumnDef<AttendanceDailyRecord>[] = [
    { id: 'personType' },
    { id: 'name' },
    { id: 'department' },
    { id: 'course' },
    { id: 'school' },
    { id: 'position' },
    { id: 'date' },
    { id: 'actions' },
  ];
  return columns
    .filter((col) => {
      if (col.id === 'personType') return mode === 'combined';
      if (col.id === 'course') return mode === 'student' || mode === 'combined';
      if (col.id === 'school') return mode === 'student';
      if (col.id === 'position') return mode === 'employee' || mode === 'combined';
      return true;
    })
    .map((col) => col.id!);
}

describe('attendance page column mode', () => {
  it('shows course/school for students', () => {
    const ids = visibleColumns('student');
    expect(ids).toContain('course');
    expect(ids).toContain('school');
    expect(ids).not.toContain('position');
    expect(ids).not.toContain('personType');
  });

  it('shows position for employees', () => {
    const ids = visibleColumns('employee');
    expect(ids).toContain('position');
    expect(ids).not.toContain('course');
    expect(ids).not.toContain('school');
    expect(ids).not.toContain('personType');
  });

  it('shows type, course, and position when combined', () => {
    const ids = visibleColumns('combined');
    expect(ids).toContain('personType');
    expect(ids).toContain('course');
    expect(ids).toContain('position');
    expect(ids).not.toContain('school');
  });
});
