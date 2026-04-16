import {Injectable, signal} from '@angular/core';

let initialSpinnerRemoved = false;

/**
 * A service to manage the global loading state of the application.
 */
@Injectable({
  providedIn: 'root',
})
export class LoadingService {
  readonly isLoading = signal(false);

  show() {
    this.isLoading.set(true);
  }

  hide() {
    this.isLoading.set(false);
    if (!initialSpinnerRemoved) {
      const spinner = document.getElementById('initial-loading-overlay');
      if (spinner) {
        spinner.remove();
        initialSpinnerRemoved = true;
      }
    }
  }
}

/**
 * Resets the initial spinner removed flag for testing purposes.
 */
export function resetInitialSpinnerRemovedForTest() {
  initialSpinnerRemoved = false;
}
