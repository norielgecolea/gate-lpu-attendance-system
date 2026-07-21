import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import {
  HlmDialogDescription,
  HlmDialogFooter,
  HlmDialogHeader,
  HlmDialogTitle,
} from '@spartan-ng/helm/dialog';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInput } from '@spartan-ng/helm/input';
import { studentPhotoUrl } from '../../core/students/student-photo.util';
import type { Employee } from './employees.store';

export interface EmployeeFormContext {
  mode: 'create' | 'edit';
  employee?: Employee;
}

export interface EmployeeFormResult {
  name: string;
  employeeNo: string;
  photo: string | null;
  photoFile: File | null;
  clearPhoto: boolean;
  rfid: string | null;
  birthdate: string | null;
  department: string;
  position: string | null;
}

@Component({
  selector: 'app-employee-form-dialog',
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
  templateUrl: './employee-form-dialog.html',
})
export class EmployeeFormDialog {
  private readonly dialogRef = inject<BrnDialogRef<EmployeeFormResult | null>>(BrnDialogRef);
  private readonly context = injectBrnDialogContext<EmployeeFormContext>();

  protected readonly mode = this.context.mode;
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly previewUrl = signal<string | null>(
    studentPhotoUrl(this.context.employee?.photo ?? null),
  );
  protected readonly selectedFileName = signal<string | null>(null);
  protected readonly clearPhoto = signal(false);

  private photoFile: File | null = null;

  protected name = this.context.employee?.name ?? '';
  protected employeeNo = this.context.employee?.employeeNo ?? '';
  protected existingPhoto = this.context.employee?.photo ?? null;
  protected rfid = this.context.employee?.rfid ?? '';
  protected birthdate = this.context.employee?.birthdate ?? '';
  protected department = this.context.employee?.department ?? '';
  protected position = this.context.employee?.position ?? '';

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) {
      return;
    }
    if (!file.type.startsWith('image/')) {
      this.error.set('Please choose an image file (JPEG, PNG, WebP, or GIF).');
      input.value = '';
      return;
    }
    this.error.set(null);
    this.photoFile = file;
    this.clearPhoto.set(false);
    this.selectedFileName.set(file.name);
    const objectUrl = URL.createObjectURL(file);
    this.previewUrl.set(objectUrl);
  }

  protected removePhoto(): void {
    this.photoFile = null;
    this.selectedFileName.set(null);
    this.clearPhoto.set(true);
    this.previewUrl.set(null);
    this.existingPhoto = null;
  }

  protected cancel(): void {
    this.dialogRef.close(null);
  }

  protected submit(): void {
    this.error.set(null);
    if (
      !this.name.trim() ||
      !this.employeeNo.trim() ||
      !this.department.trim()
    ) {
      this.error.set('Name, employee number, and department are required.');
      return;
    }

    const photo = this.clearPhoto() ? null : (this.existingPhoto ?? null);

    this.dialogRef.close({
      name: this.name.trim(),
      employeeNo: this.employeeNo.trim(),
      photo,
      photoFile: this.photoFile,
      clearPhoto: this.clearPhoto(),
      rfid: this.rfid.trim() || null,
      birthdate: this.birthdate.trim() || null,
      department: this.department.trim(),
      position: this.position.trim() || null,
    });
  }
}
