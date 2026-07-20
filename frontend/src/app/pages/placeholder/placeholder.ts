import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideConstruction } from '@ng-icons/lucide';

@Component({
  selector: 'app-placeholder',
  imports: [NgIcon],
  viewProviders: [provideIcons({ lucideConstruction })],
  template: `
    <div class="mx-auto max-w-7xl">
      <div
        class="border-border bg-card flex min-h-[60vh] flex-col items-center justify-center gap-4 rounded-lg border border-dashed p-10 text-center"
      >
        <div
          class="bg-primary/10 text-primary flex size-16 items-center justify-center rounded-lg"
        >
          <ng-icon name="lucideConstruction" class="text-3xl" />
        </div>
        <h2 class="text-foreground text-2xl font-bold tracking-tight">{{ title }}</h2>
        <p class="text-muted-foreground max-w-md text-sm">
          This page is under construction. Content will be available soon.
        </p>
      </div>
    </div>
  `,
})
export class Placeholder {
  private readonly route = inject(ActivatedRoute);
  protected readonly title = this.route.snapshot.data['title'] ?? 'Coming soon';
}
