import {Injectable, signal} from '@angular/core';

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
  }
}
