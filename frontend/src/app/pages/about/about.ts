import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideArrowLeft } from '@ng-icons/lucide';
import { APP_NAME, APP_VERSION, RELEASES } from '../../core/app-info';

@Component({
  selector: 'app-about',
  imports: [RouterLink, NgIcon],
  viewProviders: [provideIcons({ lucideArrowLeft })],
  templateUrl: './about.html',
})
export class About {
  protected readonly appName = APP_NAME;
  protected readonly version = APP_VERSION;
  protected readonly releases = RELEASES;
}
