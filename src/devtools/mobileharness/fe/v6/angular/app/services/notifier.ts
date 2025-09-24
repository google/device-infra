/**
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {LiveAnnouncer} from '@angular/cdk/a11y';
import {Injectable} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';
import {MatSnackBar} from '@angular/material/snack-bar';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';

import {EndpointDetailedError, ErrorDialog, ErrorDialogData} from './error_dialog';
import {NotifierDialog, NotifierDialogData} from './notifier_dialog';

/** Notification service */
@Injectable({
  providedIn: 'root',
})
export class Notifier {
  constructor(
      private readonly snackBar: MatSnackBar,
      private readonly dialog: MatDialog,
      private readonly liveAnnouncer: LiveAnnouncer) {}

  /** Displays a message that disappears after a set amount of time. */
  showMessage(message: string, action?: string): Observable<boolean> {
    const snackBarRef = this.snackBar.open(message, action, {
      panelClass: 'message',
      duration: 5000,  // ms
    });
    return snackBarRef.afterDismissed().pipe(
        map(result => result.dismissedByAction));
  }

  /** Displays a warning message. */
  showWarning(message: string): Observable<boolean> {
    const snackBarRef = this.snackBar.open(message, 'Close', {
      panelClass: 'warning',
    });
    return snackBarRef.afterDismissed().pipe(
        map(result => result.dismissedByAction));
  }

  /** Displays an error message. */
  showError(
      message: string, detailedError?: EndpointDetailedError,
      title = 'Error'): Observable<void> {
    this.liveAnnouncer.announce(title, 'assertive');
    const data: ErrorDialogData = {
      title,
      message,
      className: 'error',
      icon: 'error',
      rejectText: 'Close',
      detailedError,
    };
    const dialogRef = this.dialog.open(
        ErrorDialog, {minWidth: '400px', disableClose: true, data});
    return dialogRef.afterClosed();
  }

  /** Displays a yes/no confirmation popup. */
  confirm(
      message: string, title: string, confirmText = 'Yes',
      rejectText = 'No'): Observable<boolean> {
    this.liveAnnouncer.announce(title, 'assertive');
    const data: NotifierDialogData =
        {title, message, icon: 'error', confirmText, rejectText};
    const dialogRef = this.dialog.open(
        NotifierDialog, {width: '400px', disableClose: true, data});
    return dialogRef.afterClosed().pipe(map(result => !!result));
  }
}
