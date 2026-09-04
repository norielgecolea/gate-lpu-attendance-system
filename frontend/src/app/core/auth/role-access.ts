import { USER_ROLES } from '../users/users-api.service';

/** Roles the signed-in user may assign when creating or editing accounts. */
export function assignableRolesFor(actorRole: string | null | undefined): readonly string[] {
  switch (actorRole) {
    case 'SUPERADMIN':
      return USER_ROLES;
    case 'OSAS':
      return ['OSAS'];
    case 'HR':
      return ['HR'];
    default:
      return [];
  }
}

export function canManageUserRole(
  actorRole: string | null | undefined,
  targetRole: string,
): boolean {
  return assignableRolesFor(actorRole).includes(targetRole);
}

/** Whether a route is reachable for the current admin-portal role. */
export function canAccessAdminRoute(role: string | null | undefined, route: string): boolean {
  if (!role) {
    return false;
  }
  if (route === '/dashboard') {
    return role === 'SUPERADMIN' || role === 'OSAS' || role === 'HR' || role === 'LIBRARIAN' || role === 'OLIVE';
  }
  if (route === '/rfid-checker' || route === '/daily-recap') {
    return role === 'SUPERADMIN' || role === 'OSAS' || role === 'HR';
  }
  if (route === '/attendance') {
    return role === 'LIBRARIAN' || role === 'OLIVE';
  }
  if (
    route === '/students/inactive' ||
    route === '/students/finance-tagged' ||
    route === '/students/rfid' ||
    route === '/students/attendance'
  ) {
    return role === 'SUPERADMIN' || role === 'OSAS';
  }
  if (route.startsWith('/students')) {
    return role === 'SUPERADMIN' || role === 'OSAS' || role === 'LIBRARIAN' || role === 'OLIVE';
  }
  if (route === '/employees/inactive' || route === '/employees/rfid' || route === '/employees/attendance') {
    return role === 'SUPERADMIN' || role === 'HR';
  }
  if (route.startsWith('/employees')) {
    return role === 'SUPERADMIN' || role === 'HR' || role === 'LIBRARIAN' || role === 'OLIVE';
  }
  if (route === '/users') {
    return role === 'SUPERADMIN' || role === 'OSAS' || role === 'HR';
  }
  if (route === '/settings/guard-display') {
    return role === 'SUPERADMIN' || role === 'OSAS';
  }
  if (route === '/settings/gate-tones') {
    return role === 'SUPERADMIN' || role === 'OSAS';
  }
  if (route === '/tap-errors') {
    return role === 'SUPERADMIN' || role === 'OSAS' || role === 'HR' || role === 'LIBRARIAN' || role === 'OLIVE';
  }
  if (route === '/audit-logs') {
    return role === 'SUPERADMIN';
  }
  if (route === '/backup') {
    return role === 'SUPERADMIN';
  }
  return false;
}
