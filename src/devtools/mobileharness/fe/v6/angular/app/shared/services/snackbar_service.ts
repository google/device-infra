import {Injectable, inject} from '@angular/core';
import {MatSnackBar, MatSnackBarRef} from '@angular/material/snack-bar';

import {
  SnackBar,
  SnackBarData,
} from 'app/shared/components/snackbar/snackbar';

const SNACKBAR_DURATION_MS_DEFAULT = 4000; // unit is milliseconds

/** A service to show snack bar notifications. */
@Injectable({
  providedIn: 'root',
})
export class SnackBarService {
  private readonly snackBar = inject(MatSnackBar);

  /** Shows an informational snackbar message. */
  showInfo(message: string, duration = SNACKBAR_DURATION_MS_DEFAULT) {
    this.snackBar.openFromComponent(SnackBar, {
      duration,
      verticalPosition: 'bottom',
      horizontalPosition: 'center',
      panelClass: ['custom-snackbar', 'snackbar-info'],
      data: {message, icon: 'info', type: 'info'} as SnackBarData,
    });
  }

  /** Shows a success-themed snackbar message. */
  showSuccess(message: string, duration = SNACKBAR_DURATION_MS_DEFAULT) {
    this.snackBar.openFromComponent(SnackBar, {
      duration,
      verticalPosition: 'bottom',
      horizontalPosition: 'center',
      panelClass: ['custom-snackbar', 'snackbar-success'],
      data: {message, icon: 'check_circle', type: 'success'} as SnackBarData,
    });
  }

  /** Shows an error-themed snackbar message. */
  showError(message: string, duration = SNACKBAR_DURATION_MS_DEFAULT) {
    this.snackBar.openFromComponent(SnackBar, {
      duration,
      panelClass: ['custom-snackbar', 'snackbar-error'],
      verticalPosition: 'bottom',
      horizontalPosition: 'center',
      data: {message, icon: 'error', type: 'error'} as SnackBarData,
    });
  }

  /** Shows an in-progress/loading snackbar message that can be dismissed manually. */
  showInProgress(message: string): MatSnackBarRef<SnackBar> {
    return this.snackBar.openFromComponent(SnackBar, {
      // No duration means it's persistent until dismissed
      verticalPosition: 'bottom',
      horizontalPosition: 'center',
      panelClass: ['custom-snackbar', 'snackbar-inprogress'],
      data: {message, icon: 'sync', type: 'inprogress'} as SnackBarData,
    });
  }
}
