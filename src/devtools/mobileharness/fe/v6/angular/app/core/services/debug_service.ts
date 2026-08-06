import {DOCUMENT} from '@angular/common';
import {Injectable, inject} from '@angular/core';

/**
 * Service to provide debug mode status based on URL query parameters.
 * In debug mode, we can have lots of additional features available for
 * debugging, like a refresh button to re-trigger the page load.
 */
@Injectable({providedIn: 'root'})
export class DebugService {
  private readonly isDebugMode: boolean;
  private readonly win: Window | null;
  private readonly document = inject(DOCUMENT, {optional: true});

  constructor() {
    this.win =
      this.document?.defaultView ??
      (typeof window !== 'undefined' ? window : null);

    const search = this.win?.location?.search ?? '';
    const params = new URLSearchParams(search);
    this.isDebugMode = params.get('debug') === 'true';
  }

  /**
   * Returns true if the application is in debug mode.
   *
   * @return True if the `debug=true` query parameter is present, false otherwise.
   */
  isDebug(): boolean {
    return this.isDebugMode;
  }
}
