export const APP_NAME = 'Attendance System';

export interface ReleaseNote {
  title: string;
  detail: string;
}

export interface Release {
  version: string;
  notes: ReleaseNote[];
}

/**
 * Newest first. Add a new `{ version, notes }` object at the top;
 * leave older releases in place so their patch notes stay on the page.
 */
export const RELEASES: Release[] = [
  {
    version: '2.3.6',
    notes: [
      {
        title: 'Unused pictures',
        detail:
          'Replacing a photo deletes the old file. Superadmin can scan and remove leftover pictures from earlier uploads on Backup & Restore.',
      },
    ],
  },
  {
    version: '2.3.5',
    notes: [
      {
        title: 'Bulk picture upload',
        detail:
          'Student and employee pictures upload in batches with a progress bar, so large folders no longer fail.',
      },
    ],
  },
  {
    version: '2.3.0',
    notes: [
      {
        title: 'Backup & Restore',
        detail:
          'Superadmin can download a zip of the database, photos, guard videos, and gate tones, then restore from that zip when needed.',
      },
      {
        title: 'Change password',
        detail: 'Signed-in users can change their own password from the account menu.',
      },
    ],
  },
  {
    version: '2.2.5',
    notes: [
      {
        title: 'Cleaner ID taps',
        detail:
          'An accidental keyboard press at the gate no longer gets mixed into the next student or employee tap.',
      },
      {
        title: 'View photos',
        detail: 'Click a student or employee photo in admin pages to see it full size.',
      },
    ],
  },
  {
    version: '2.2.0',
    notes: [
      {
        title: 'Server time',
        detail: 'Kiosk and monitor clocks follow the Tomcat host clock, not the browser.',
      },
      {
        title: 'About this system',
        detail: 'Login shows the version and opens patch notes for every release.',
      },
    ],
  },
  {
    version: '2.1.0',
    notes: [
      {
        title: 'Gate scanning',
        detail: 'Tap an ID at the gate to record time in or time out.',
      },
      {
        title: 'Birthday alerts',
        detail: 'Celebrates when someone taps on their birthday.',
      },
      {
        title: 'Finance alerts',
        detail: 'Warns the gate when a finance-tagged student taps.',
      },
      {
        title: 'Customizable gate sounds',
        detail: 'Different sounds for time in, time out, errors, birthday, and finance.',
      },
      {
        title: 'Guard side panel',
        detail: 'Shows a video, or nothing—whichever you choose.',
      },
      {
        title: 'Live monitoring wall',
        detail:
          'Big screen of today’s campus activity and who is still inside for internal monitoring purposes.',
      },
      {
        title: 'Online guards',
        detail: 'Shows which gate locations currently have a guard signed in.',
      },
      {
        title: 'Admin dashboard',
        detail: 'Quick live overview of campus gate activity.',
      },
      {
        title: 'RFID Checker',
        detail: 'Tap a card to look up and verify the person’s record.',
      },
      {
        title: 'Daily Recap',
        detail: 'Superadmin can review the same campus stats for any past day.',
      },
      {
        title: 'Attendance reports',
        detail: 'View history and export records when needed.',
      },
      {
        title: 'RFID registration',
        detail: 'Link an ID card to a student or employee quickly.',
      },
      {
        title: 'Finance-tagged list',
        detail: 'Manage students flagged for finance follow-up.',
      },
      {
        title: 'Inactive lists',
        detail: 'See deactivated students or employees and restore them if needed.',
      },
      {
        title: 'Staff accounts',
        detail: 'Create and manage who can log in and what they can access.',
      },
      {
        title: 'Guard display settings',
        detail: 'Control what appears on the guard screen’s side panel.',
      },
      {
        title: 'Gate tones library',
        detail: 'Upload short audio files and assign them to gate events.',
      },
      {
        title: 'Failed tap log',
        detail: 'Review unrecognized or failed card reads.',
      },
      {
        title: 'Stay signed in',
        detail: 'Gate computers can stay logged in for long shifts.',
      },
    ],
  },
];

export const APP_VERSION = RELEASES[0]?.version ?? '2.1.0';
