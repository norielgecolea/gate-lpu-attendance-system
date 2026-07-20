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
import type { Student } from './students.store';

export interface StudentFormContext {
  mode: 'create' | 'edit';
  student?: Student;
}

export interface StudentFormResult {
  name: string;
  studentNo: string;
  photo: string | null;
  photoFile: File | null;
  clearPhoto: boolean;
  rfid: string | null;
  birthdate: string | null;
  department: string;
  course: string;
  school: string;
}

@Component({
  selector: 'app-student-form-dialog',
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
  templateUrl: './student-form-dialog.html',
})
export class StudentFormDialog {
  private readonly dialogRef = inject<BrnDialogRef<StudentFormResult | null>>(BrnDialogRef);
  private readonly context = injectBrnDialogContext<StudentFormContext>();

  protected readonly mode = this.context.mode;
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly previewUrl = signal<string | null>(
    studentPhotoUrl(this.context.student?.photo ?? null),
  );
  protected readonly selectedFileName = signal<string | null>(null);
  protected readonly clearPhoto = signal(false);

  private photoFile: File | null = null;

  protected name = this.context.student?.name ?? '';
  protected studentNo = this.context.student?.studentNo ?? '';
  protected existingPhoto = this.context.student?.photo ?? null;
  protected rfid = this.context.student?.rfid ?? '';
  protected birthdate = this.context.student?.birthdate ?? '';
  protected department = this.context.student?.department ?? '';
  protected course = this.context.student?.course ?? '';
  protected school = this.context.student?.school ?? 'LPL';

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
      !this.studentNo.trim() ||
      !this.department.trim() ||
      !this.course.trim() ||
      !this.school.trim()
    ) {
      this.error.set('Name, student number, department, course, and school are required.');
      return;
    }

    const photo = this.clearPhoto()
      ? null
      : this.photoFile
        ? this.existingPhoto
        : (this.existingPhoto ?? null);

    this.dialogRef.close({
      name: this.name.trim(),
      studentNo: this.studentNo.trim(),
      photo,
      photoFile: this.photoFile,
      clearPhoto: this.clearPhoto(),
      rfid: this.rfid.trim() || null,
      birthdate: this.birthdate.trim() || null,
      department: this.department.trim(),
      course: this.course.trim(),
      school: this.school.trim(),
    });
  }
}
