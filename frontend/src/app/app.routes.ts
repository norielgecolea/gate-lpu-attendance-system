import { Routes } from '@angular/router';
import { Login } from './pages/login/login';
import { AdminLayout } from './layouts/admin-layout/admin-layout';
import { Dashboard } from './pages/dashboard/dashboard';
import { Students } from './pages/students/students';
import { InactiveStudents } from './pages/students/inactive-students';
import { StudentRfidRegistration } from './pages/students/student-rfid-registration';
import { PersonAttendance } from './pages/attendance/person-attendance';
import { AttendancePage } from './pages/attendance/attendance-page';
import { Employees } from './pages/employees/employees';
import { InactiveEmployees } from './pages/employees/inactive-employees';
import { EmployeeRfidRegistration } from './pages/employees/employee-rfid-registration';
import { Users } from './pages/users/users';
import { GuardDisplaySettings } from './pages/settings/guard-display-settings';
import { TapErrorLogs } from './pages/tap-errors/tap-error-logs';
import { GateKiosk } from './pages/guard/gate-kiosk';
import { Monitor } from './pages/monitor/monitor';
import {
  guestGuard,
  guardRoleGuard,
  monitoringGuard,
  superAdminGuard,
} from './core/auth/auth.guards';

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
    canActivate: [superAdminGuard],
    children: [
      { path: 'dashboard', component: Dashboard },
      { path: 'students/inactive', component: InactiveStudents },
      { path: 'students/rfid', component: StudentRfidRegistration },
      {
        path: 'students/attendance',
        component: AttendancePage,
        data: { personType: 'STUDENT' },
      },
      {
        path: 'students',
        component: Students,
        children: [
          { path: ':id/attendance', component: PersonAttendance, data: { personType: 'STUDENT' } },
          { path: ':id/logs', redirectTo: ':id/attendance' },
        ],
      },
      { path: 'employees/inactive', component: InactiveEmployees },
      { path: 'employees/rfid', component: EmployeeRfidRegistration },
      {
        path: 'employees/attendance',
        component: AttendancePage,
        data: { personType: 'EMPLOYEE' },
      },
      {
        path: 'employees',
        component: Employees,
        children: [
          { path: ':id/attendance', component: PersonAttendance, data: { personType: 'EMPLOYEE' } },
        ],
      },
      { path: 'users', component: Users },
      { path: 'settings/guard-display', component: GuardDisplaySettings },
      { path: 'tap-errors', component: TapErrorLogs },
      { path: 'attendance', redirectTo: 'students/attendance' },
      { path: 'deleted-students', redirectTo: 'students/inactive' },
    ],
  },
  { path: '**', redirectTo: '' },
];
