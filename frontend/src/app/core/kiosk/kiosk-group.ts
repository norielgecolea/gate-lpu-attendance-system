export type KioskGroup = 'MAIN_GATES' | 'LIBRARY' | 'OLIVE_HOTEL';

export const KIOSK_GROUPS: readonly KioskGroup[] = ['MAIN_GATES', 'LIBRARY', 'OLIVE_HOTEL'];

export const KIOSK_GROUP_LABELS: Record<KioskGroup, string> = {
  MAIN_GATES: 'Main Gates',
  LIBRARY: 'Library',
  OLIVE_HOTEL: 'Olive Hotel',
};

export function kioskGroupFromRole(role: string | null | undefined): KioskGroup {
  switch (role) {
    case 'LIBRARIAN':
    case 'LIBRARY_KIOSK':
      return 'LIBRARY';
    case 'OLIVE':
    case 'OLIVE_KIOSK':
      return 'OLIVE_HOTEL';
    default:
      return 'MAIN_GATES';
  }
}

export function canPickExportKiosk(role: string | null | undefined): boolean {
  return role === 'SUPERADMIN' || role === 'OSAS' || role === 'HR';
}

export function isKioskRole(role: string | null | undefined): boolean {
  return role === 'GUARD' || role === 'LIBRARY_KIOSK' || role === 'OLIVE_KIOSK';
}

export function isVenueAdmin(role: string | null | undefined): boolean {
  return role === 'LIBRARIAN' || role === 'OLIVE';
}

export function kioskGroupSlug(group: KioskGroup): string {
  switch (group) {
    case 'MAIN_GATES':
      return 'main-gates';
    case 'LIBRARY':
      return 'library';
    case 'OLIVE_HOTEL':
      return 'olive-hotel';
  }
}
