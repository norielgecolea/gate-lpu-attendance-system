import { Component, inject, signal } from '@angular/core';
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
import {
  KIOSK_GROUPS,
  KIOSK_GROUP_LABELS,
  type KioskGroup,
} from '../../core/kiosk/kiosk-group';

export interface AttendanceExportContext {
  startDate: string;
  endDate: string;
  canPickKiosk: boolean;
  lockedKioskGroup: KioskGroup;
}

export interface AttendanceExportResult {
  allTime: boolean;
  startDate?: string;
  endDate?: string;
  kioskGroup: KioskGroup;
}

@Component({
  selector: 'app-attendance-export-dialog',
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
  templateUrl: './attendance-export-dialog.html',
})
export class AttendanceExportDialog {
  private readonly dialogRef = inject<BrnDialogRef<AttendanceExportResult | null>>(BrnDialogRef);
  private readonly context = injectBrnDialogContext<AttendanceExportContext>();

  protected readonly canPickKiosk = this.context.canPickKiosk;
  protected readonly kioskGroups = KIOSK_GROUPS;
  protected readonly kioskLabels = KIOSK_GROUP_LABELS;
  protected readonly error = signal<string | null>(null);

  protected rangeMode: 'all' | 'range' = 'range';
  protected startDate = this.context.startDate;
  protected endDate = this.context.endDate;
  protected kioskGroup: KioskGroup = this.context.lockedKioskGroup;

  static open(dialog: HlmDialogService, context: AttendanceExportContext) {
    return dialog.open(AttendanceExportDialog, {
      context,
      contentClass: 'sm:max-w-md',
    });
  }

  protected cancel(): void {
    this.dialogRef.close(null);
  }

  protected submit(): void {
    this.error.set(null);
    if (this.rangeMode === 'range') {
      if (!this.startDate || !this.endDate) {
        this.error.set('Start and end dates are required for a date-range export.');
        return;
      }
      if (this.startDate > this.endDate) {
        this.error.set('Start date must be on or before end date.');
        return;
      }
    }
    this.dialogRef.close({
      allTime: this.rangeMode === 'all',
      startDate: this.rangeMode === 'range' ? this.startDate : undefined,
      endDate: this.rangeMode === 'range' ? this.endDate : undefined,
      kioskGroup: this.kioskGroup,
    });
  }
}
