import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  {
    path: '',
    renderMode: RenderMode.Server,
  },
  {
    path: 'about',
    renderMode: RenderMode.Server,
  },
  {
    path: 'students/:id/attendance',
    renderMode: RenderMode.Client,
  },
  {
    path: 'students/:id/logs',
    renderMode: RenderMode.Client,
  },
  {
    path: 'employees/:id/attendance',
    renderMode: RenderMode.Client,
  },
  {
    path: '**',
    renderMode: RenderMode.Prerender,
  },
];
