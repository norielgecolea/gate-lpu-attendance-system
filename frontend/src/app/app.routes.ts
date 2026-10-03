import { Routes } from '@angular/router';
import {
  adminPortalGuard,
  allowRoles,
  guardRoleGuard,
  guestGuard,
  monitoringGuard,
} from './core/auth/auth.guards';

const ADMIN_ROLES = ['SUPERADMIN', 'OSAS', 'HR'] as const;
const PORTAL_ROLES = ['SUPERADMIN', 'OSAS', 'HR', 'LIBRARIAN', 'OLIVE'] as const;
const SUPERADMIN_ROLES = ['SUPERADMIN'] as const;
const OSAS_ROLES = ['SUPERADMIN', 'OSAS'] as const;
const HR_ROLES = ['SUPERADMIN', 'HR'] as const;
const OSAS_ADMIN_ROLES = ['SUPERADMIN', 'OSAS'] as const;
const TAP_ERROR_ROLES = ['SUPERADMIN', 'OSAS', 'HR', 'LIBRARIAN', 'OLIVE'] as const;
const STUDENT_DIRECTORY_ROLES = ['SUPERADMIN', 'OSAS', 'LIBRARIAN', 'OLIVE'] as const;
const EMPLOYEE_DIRECTORY_ROLES = ['SUPERADMIN', 'HR', 'LIBRARIAN', 'OLIVE'] as const;
const VENUE_ADMIN_ROLES = ['LIBRARIAN', 'OLIVE'] as const;

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
    canActivate: [guestGuard],
    pathMatch: 'full',
  },
  { path: 'login', redirectTo: '' },
  {
    path: 'about',
    loadComponent: () => import('./pages/about/about').then((m) => m.About),
  },
  {
    path: 'guard',
    loadComponent: () => import('./pages/guard/gate-kiosk').then((m) => m.GateKiosk),
    canActivate: [guardRoleGuard],
  },
  {
    path: 'monitor',
    loadComponent: () => import('./pages/monitor/monitor').then((m) => m.Monitor),
    canActivate: [monitoringGuard],
  },
  {
    path: '',
    loadComponent: () => import('./layouts/admin-layout/admin-layout').then((m) => m.AdminLayout),
    canActivate: [adminPortalGuard],
    children: [
      {
        path: 'dashboard/library',
        loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard),
        data: { kioskGroup: 'LIBRARY' },
        canActivate: [allowRoles(...SUPERADMIN_ROLES)],
      },
      {
        path: 'dashboard/olive',
        loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard),
        data: { kioskGroup: 'OLIVE_HOTEL' },
        canActivate: [allowRoles(...SUPERADMIN_ROLES)],
      },
      {
        path: 'dashboard',
        loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard),
        canActivate: [allowRoles(...PORTAL_ROLES)],
      },
      {
        path: 'kiosk',
        loadComponent: () => import('./pages/kiosk/admin-kiosk').then((m) => m.AdminKiosk),
        canActivate: [allowRoles(...SUPERADMIN_ROLES)],
      },
      {
        path: 'rfid-checker',
        loadComponent: () => import('./pages/rfid-checker/rfid-checker').then((m) => m.RfidChecker),
        canActivate: [allowRoles(...ADMIN_ROLES)],
      },
      {
        path: 'daily-recap',
        loadComponent: () => import('./pages/daily-recap/daily-recap').then((m) => m.DailyRecap),
        canActivate: [allowRoles(...ADMIN_ROLES)],
      },
      {
        path: 'students/inactive',
        loadComponent: () =>
          import('./pages/students/inactive-students').then((m) => m.InactiveStudents),
        canActivate: [allowRoles(...OSAS_ROLES)],
      },
      {
        path: 'students/finance-tagged',
        loadComponent: () =>
          import('./pages/students/finance-tagged-students').then((m) => m.FinanceTaggedStudents),
        canActivate: [allowRoles(...OSAS_ROLES)],
      },
      {
        path: 'students/alarm',
        loadComponent: () => import('./pages/students/alarm-students').then((m) => m.AlarmStudents),
        canActivate: [allowRoles(...SUPERADMIN_ROLES)],
      },
      {
        path: 'students/rfid',
        loadComponent: () =>
          import('./pages/students/student-rfid-registration').then((m) => m.StudentRfidRegistration),
        canActivate: [allowRoles(...OSAS_ROLES)],
      },
      {
        path: 'students/attendance',
        loadComponent: () =>
          import('./pages/attendance/attendance-page').then((m) => m.AttendancePage),
        data: { personType: 'STUDENT' },
        canActivate: [allowRoles(...OSAS_ROLES)],
      },
      {
        path: 'attendance',
        loadComponent: () =>
          import('./pages/attendance/attendance-page').then((m) => m.AttendancePage),
        data: { personType: 'ALL' },
        canActivate: [allowRoles(...VENUE_ADMIN_ROLES)],
      },
      {
        path: 'students',
        loadComponent: () => import('./pages/students/students').then((m) => m.Students),
        canActivate: [allowRoles(...STUDENT_DIRECTORY_ROLES)],
        children: [
          {
            path: ':id/attendance',
            loadComponent: () =>
              import('./pages/attendance/person-attendance').then((m) => m.PersonAttendance),
            data: { personType: 'STUDENT' },
            canActivate: [allowRoles(...STUDENT_DIRECTORY_ROLES)],
          },
          { path: ':id/logs', redirectTo: ':id/attendance' },
        ],
      },
      {
        path: 'employees/inactive',
        loadComponent: () =>
          import('./pages/employees/inactive-employees').then((m) => m.InactiveEmployees),
        canActivate: [allowRoles(...HR_ROLES)],
      },
      {
        path: 'employees/alarm',
        loadComponent: () =>
          import('./pages/employees/alarm-employees').then((m) => m.AlarmEmployees),
        canActivate: [allowRoles(...SUPERADMIN_ROLES)],
      },
      {
        path: 'employees/rfid',
        loadComponent: () =>
          import('./pages/employees/employee-rfid-registration').then(
            (m) => m.EmployeeRfidRegistration,
          ),
        canActivate: [allowRoles(...HR_ROLES)],
      },
      {
        path: 'employees/attendance',
        loadComponent: () =>
          import('./pages/attendance/attendance-page').then((m) => m.AttendancePage),
        data: { personType: 'EMPLOYEE' },
        canActivate: [allowRoles(...HR_ROLES)],
      },
      {
        path: 'employees',
        loadComponent: () => import('./pages/employees/employees').then((m) => m.Employees),
        canActivate: [allowRoles(...EMPLOYEE_DIRECTORY_ROLES)],
        children: [
          {
            path: ':id/attendance',
            loadComponent: () =>
              import('./pages/attendance/person-attendance').then((m) => m.PersonAttendance),
            data: { personType: 'EMPLOYEE' },
            canActivate: [allowRoles(...EMPLOYEE_DIRECTORY_ROLES)],
          },
        ],
      },
      {
        path: 'users',
        loadComponent: () => import('./pages/users/users').then((m) => m.Users),
        canActivate: [allowRoles(...ADMIN_ROLES)],
      },
      {
        path: 'settings/guard-display',
        loadComponent: () =>
          import('./pages/settings/guard-display-settings').then((m) => m.GuardDisplaySettings),
        canActivate: [allowRoles(...OSAS_ADMIN_ROLES)],
      },
      {
        path: 'settings/gate-tones',
        loadComponent: () =>
          import('./pages/settings/gate-tones-settings').then((m) => m.GateTonesSettings),
        canActivate: [allowRoles(...OSAS_ADMIN_ROLES)],
      },
      {
        path: 'tap-errors',
        loadComponent: () => import('./pages/tap-errors/tap-error-logs').then((m) => m.TapErrorLogs),
        canActivate: [allowRoles(...TAP_ERROR_ROLES)],
      },
      {
        path: 'audit-logs',
        loadComponent: () => import('./pages/audit/audit-logs').then((m) => m.AuditLogs),
        canActivate: [allowRoles(...SUPERADMIN_ROLES)],
      },
      {
        path: 'backup',
        loadComponent: () => import('./pages/backup/backup').then((m) => m.Backup),
        canActivate: [allowRoles(...SUPERADMIN_ROLES)],
      },
      { path: 'deleted-students', redirectTo: 'students/inactive' },
    ],
  },
  { path: '**', redirectTo: '' },
];
