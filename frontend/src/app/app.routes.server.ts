import { RenderMode, ServerRoute } from '@angular/ssr';

/**
 * This app's session lives in browser storage, so the server cannot render
 * authenticated pages. Client rendering returns the app shell immediately
 * and lets the browser router decide the page.
 */
export const serverRoutes: ServerRoute[] = [
  {
    path: '**',
    renderMode: RenderMode.Client,
  },
];
