import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import type { PersonType } from '../../core/attendance/attendance-api.service';
import { PersonAttendanceDialog } from './person-attendance-dialog';

/**
 * Route-driven opener for /students/:id/attendance and /employees/:id/attendance.
 */
@Component({
  selector: 'app-person-attendance',
  template: '',
})
export class PersonAttendance {
  private readonly dialog = inject(HlmDialogService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  constructor() {
    const personId = this.route.snapshot.paramMap.get('id') ?? '';
    const personType = (this.route.snapshot.data['personType'] as PersonType) ?? 'STUDENT';
    const backRoute = personType === 'STUDENT' ? '/students' : '/employees';
    const ref = this.dialog.open(PersonAttendanceDialog, {
      context: { personType, personId },
      contentClass: 'sm:max-w-3xl',
    });
    ref.closed$.subscribe(() => this.router.navigate([backRoute]));
  }
}
