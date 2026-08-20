export const APP_NAME = 'Attendance System';

export interface ReleaseNote {
  title: string;
  detail: string;
}

export interface Release {
  version: string;
  notes: ReleaseNote[];
}

/** Newest first — add new versions at the top and keep older notes below. */
export const RELEASES: Release[] = [
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
