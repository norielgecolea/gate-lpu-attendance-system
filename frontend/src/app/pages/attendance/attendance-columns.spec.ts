import { describe, expect, it } from 'vitest';
import type { ColumnDef } from '@tanstack/angular-table';
import type { AttendanceDailyRecord } from '../../core/attendance/attendance-api.service';

function visibleColumns(isStudent: boolean): string[] {
  const columns: ColumnDef<AttendanceDailyRecord>[] = [
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
      if (col.id === 'course' || col.id === 'school') return isStudent;
      if (col.id === 'position') return !isStudent;
      return true;
    })
    .map((col) => col.id!);
}

describe('attendance page column mode', () => {
  it('shows course/school for students', () => {
    const ids = visibleColumns(true);
    expect(ids).toContain('course');
    expect(ids).toContain('school');
    expect(ids).not.toContain('position');
  });

  it('shows position for employees', () => {
    const ids = visibleColumns(false);
    expect(ids).toContain('position');
    expect(ids).not.toContain('course');
    expect(ids).not.toContain('school');
  });
});
