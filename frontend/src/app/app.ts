import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { PhotoPreviewOverlay } from './shared/photo-preview/photo-preview-overlay';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, PhotoPreviewOverlay],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly title = signal('lpu-gate-attendance');
}
