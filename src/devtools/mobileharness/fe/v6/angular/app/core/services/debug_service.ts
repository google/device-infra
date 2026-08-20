import {DOCUMENT} from '@angular/common';
import {Injectable, inject} from '@angular/core';

/**
 * Service to provide debug mode status based on URL query parameters.
 * In debug mode, additional features are available for debugging, such as a
 * refresh button to re-trigger the page load.
 */
@Injectable({providedIn: 'root'})
export class DebugService {
  private readonly isDebugMode: boolean;
  private readonly win: Window | null;
  private readonly document = inject(DOCUMENT, {optional: true});

  /**
   * Initializes the DebugService, checking the URL query parameters
   * for the 'debug=true' flag.
   */
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
