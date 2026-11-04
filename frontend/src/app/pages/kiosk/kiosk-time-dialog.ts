import { AfterViewInit, Component, ElementRef, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import {
  HlmDialogDescription,
  HlmDialogFooter,
  HlmDialogHeader,
  HlmDialogTitle,
  HlmDialogService,
} from '@spartan-ng/helm/dialog';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInput } from '@spartan-ng/helm/input';

export interface KioskTimeResult {
  date: string;
  time: string;
}

const CAMPUS_ZONE = 'Asia/Manila';

@Component({
  selector: 'app-kiosk-time-dialog',
  imports: [
    FormsModule,
    HlmButton,
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogDescription,
    HlmDialogFooter,
    HlmFieldImports,
    HlmInput,
  ],
  templateUrl: './kiosk-time-dialog.html',
})
export class KioskTimeDialog implements AfterViewInit {
  @ViewChild('dateInput') private readonly dateInput?: ElementRef<HTMLInputElement>;

  private readonly dialogRef = inject<BrnDialogRef<KioskTimeResult | null>>(BrnDialogRef);
  private readonly context = injectBrnDialogContext<KioskTimeResult>();

  protected readonly error = signal<string | null>(null);
  protected readonly todayDate = manilaNowFields().date;
  protected date = this.context.date;
  protected time = this.context.time;

  static open(dialog: HlmDialogService, current: KioskTimeResult) {
    return dialog.open(KioskTimeDialog, {
      context: current,
      contentClass: 'sm:max-w-md',
    });
  }

  ngAfterViewInit(): void {
    queueMicrotask(() => {
      const input = this.dateInput?.nativeElement;
      input?.focus();
      try {
        input?.showPicker();
      } catch {
        // Some browsers only allow showPicker() from a direct click.
      }
    });
  }

  protected cancel(): void {
    this.dialogRef.close(null);
  }

  protected apply(): void {
    this.error.set(null);
    const date = this.date.trim();
    const time = this.time.trim();
    if (!date || !time) {
      this.error.set('Date and time are required.');
      return;
    }
    const picked = new Date(campusTimestamp(date, time));
    if (Number.isNaN(picked.getTime())) {
      this.error.set('Enter a valid date and time.');
      return;
    }
    if (picked.getTime() > Date.now() + 90_000) {
      this.error.set('Time cannot be in the future.');
      return;
    }
    this.dialogRef.close({ date, time });
  }
}

export function isKioskTimeResult(value: unknown): value is KioskTimeResult {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const picked = value as { date?: unknown; time?: unknown };
  return typeof picked.date === 'string' && typeof picked.time === 'string';
}

export function manilaNowFields(): KioskTimeResult {
  return manilaFields(new Date());
}

export function manilaFieldsFromTimestamp(iso: string): KioskTimeResult {
  return manilaFields(new Date(iso));
}

export function campusTimestamp(date: string, time: string): string {
  const clock = time.length === 5 ? `${time}:00` : time;
  return `${date}T${clock}+08:00`;
}

function manilaFields(when: Date): KioskTimeResult {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: CAMPUS_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(when);
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? '';
  return {
    date: `${value('year')}-${value('month')}-${value('day')}`,
    time: `${value('hour')}:${value('minute')}`,
  };
}
