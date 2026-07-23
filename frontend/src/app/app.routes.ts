import { Routes } from '@angular/router';
import { Login } from './pages/login/login';
import { AdminLayout } from './layouts/admin-layout/admin-layout';
import { Dashboard } from './pages/dashboard/dashboard';
import { Students } from './pages/students/students';
import { InactiveStudents } from './pages/students/inactive-students';
import { FinanceTaggedStudents } from './pages/students/finance-tagged-students';
import { StudentRfidRegistration } from './pages/students/student-rfid-registration';
import { PersonAttendance } from './pages/attendance/person-attendance';
import { AttendancePage } from './pages/attendance/attendance-page';
import { Employees } from './pages/employees/employees';
import { InactiveEmployees } from './pages/employees/inactive-employees';
import { EmployeeRfidRegistration } from './pages/employees/employee-rfid-registration';
import { Users } from './pages/users/users';
import { GuardDisplaySettings } from './pages/settings/guard-display-settings';
import { GateTonesSettings } from './pages/settings/gate-tones-settings';
import { TapErrorLogs } from './pages/tap-errors/tap-error-logs';
import { RfidChecker } from './pages/rfid-checker/rfid-checker';
import { GateKiosk } from './pages/guard/gate-kiosk';
import { Monitor } from './pages/monitor/monitor';
import {
  adminPortalGuard,
  allowRoles,
  guardRoleGuard,
  guestGuard,
  monitoringGuard,
} from './core/auth/auth.guards';

const ADMIN_ROLES = ['SUPERADMIN', 'OSAS', 'HR'] as const;
const OSAS_ROLES = ['SUPERADMIN', 'OSAS'] as const;
const HR_ROLES = ['SUPERADMIN', 'HR'] as const;
const OSAS_ADMIN_ROLES = ['SUPERADMIN', 'OSAS'] as const;
const HR_ADMIN_ROLES = ['SUPERADMIN', 'OSAS', 'HR'] as const;

export const routes: Routes = [
  { path: '', component: Login, canActivate: [guestGuard], pathMatch: 'full' },
  { path: 'login', redirectTo: '' },
  {
    path: 'guard',
    component: GateKiosk,
    canActivate: [guardRoleGuard],
  },
  {
    path: 'monitor',
    component: Monitor,
    canActivate: [monitoringGuard],
  },
  {
    path: '',
    component: AdminLayout,
    canActivate: [adminPortalGuard],
    children: [
      {
        path: 'dashboard',
        component: Dashboard,
        canActivate: [allowRoles(...ADMIN_ROLES)],
      },
      {
        path: 'rfid-checker',
        component: RfidChecker,
        canActivate: [allowRoles(...ADMIN_ROLES)],
      },
      {
        path: 'students/inactive',
        component: InactiveStudents,
        canActivate: [allowRoles(...OSAS_ROLES)],
      },
      {
        path: 'students/finance-tagged',
        component: FinanceTaggedStudents,
        canActivate: [allowRoles(...OSAS_ROLES)],
      },
      {
        path: 'students/rfid',
        component: StudentRfidRegistration,
        canActivate: [allowRoles(...OSAS_ROLES)],
      },
      {
        path: 'students/attendance',
        component: AttendancePage,
        data: { personType: 'STUDENT' },
        canActivate: [allowRoles(...OSAS_ROLES)],
      },
      {
        path: 'students',
        component: Students,
        canActivate: [allowRoles(...OSAS_ROLES)],
        children: [
          {
            path: ':id/attendance',
            component: PersonAttendance,
            data: { personType: 'STUDENT' },
            canActivate: [allowRoles(...OSAS_ROLES)],
          },
          { path: ':id/logs', redirectTo: ':id/attendance' },
        ],
      },
      {
        path: 'employees/inactive',
        component: InactiveEmployees,
        canActivate: [allowRoles(...HR_ROLES)],
      },
      {
        path: 'employees/rfid',
        component: EmployeeRfidRegistration,
        canActivate: [allowRoles(...HR_ROLES)],
      },
      {
        path: 'employees/attendance',
        component: AttendancePage,
        data: { personType: 'EMPLOYEE' },
        canActivate: [allowRoles(...HR_ROLES)],
      },
      {
        path: 'employees',
        component: Employees,
        canActivate: [allowRoles(...HR_ROLES)],
        children: [
          {
            path: ':id/attendance',
            component: PersonAttendance,
            data: { personType: 'EMPLOYEE' },
            canActivate: [allowRoles(...HR_ROLES)],
          },
        ],
      },
      {
        path: 'users',
        component: Users,
        canActivate: [allowRoles(...ADMIN_ROLES)],
      },
      {
        path: 'settings/guard-display',
        component: GuardDisplaySettings,
        canActivate: [allowRoles(...OSAS_ADMIN_ROLES)],
      },
      {
        path: 'settings/gate-tones',
        component: GateTonesSettings,
        canActivate: [allowRoles(...OSAS_ADMIN_ROLES)],
      },
      {
        path: 'tap-errors',
        component: TapErrorLogs,
        canActivate: [allowRoles(...HR_ADMIN_ROLES)],
      },
      { path: 'attendance', redirectTo: 'students/attendance' },
      { path: 'deleted-students', redirectTo: 'students/inactive' },
    ],
  },
  { path: '**', redirectTo: '' },
];
