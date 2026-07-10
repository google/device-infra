import {inject} from '@angular/core';
import {MatDialog, MatDialogRef} from '@angular/material/dialog';

import {ConfirmDialog} from '../components/confirm_dialog/confirm_dialog';

/** Options for useConfigDialogActions hook. */
export interface ConfigDialogActionsOptions<T = unknown> {
  dialogRef?: MatDialogRef<T>;
  onCancelSelfLockout: () => void;
  onSubmitOverride: () => void;
  onSuccessClose?: () => void;
}

/** Composable for handling wizard configuration save/error dialog actions. */
export function useConfigDialogActions<T>(
  options: ConfigDialogActionsOptions<T>,
) {
  const dialog = inject(MatDialog);

  /** Handles the save success action by showing a success dialog. */
  const success = () => {
    const dialogData = {
      title: 'Configuration Saved',
      content: 'Your configuration has been saved successfully. ',
      type: 'success',
      primaryButtonLabel: 'OK',
    };
    dialog
      .open(ConfirmDialog, {
        data: dialogData,
        disableClose: true,
      })
      .afterClosed()
      .subscribe(() => {
        // Close the parent dialog if dialogRef was provided (used by
        // wizards).
        options.dialogRef?.close(true);
        // Call the optional success callback (used by settings to reload
        // config).
        options.onSuccessClose?.();
      });
  };

  /** Handles the self-lockout warning by showing a confirmation dialog. */
  const selfLockout = () => {
    const dialogData = {
      title: 'Permission Warning',
      content:
        'The new owners list does not contain your username, and you are not a member of any of the specified owner groups. Proceeding will remove your ability to configure this device in the future.',
      type: 'warning',
      primaryButtonLabel: 'Proceed Anyway',
      secondaryButtonLabel: 'Go Back',
    };
    dialog
      .open(ConfirmDialog, {
        data: dialogData,
        disableClose: true,
      })
      .afterClosed()
      .subscribe((result) => {
        if (result === 'secondary') {
          // If user cancels, trigger the cancel callback (returns to
          // permissions tab).
          options.onCancelSelfLockout();
          return;
        }
        if (result === 'primary') {
          // If user proceeds, trigger the submit override callback.
          options.onSubmitOverride();
        }
      });
  };

  /**
   * Handles general save failures, redirecting to selfLockout warning if
   * appropriate.
   */
  const error = (errorCode?: string) => {
    if (errorCode === 'SELF_LOCKOUT_DETECTED') {
      selfLockout();
      return;
    }

    const errorMessage = errorCode
      ? ` with error code ${errorCode}. Please try again.`
      : '. Please try again.';
    const dialogData = {
      title: 'Configuration Failed',
      content: `Your configuration has failed to save` + errorMessage,
      type: 'error',
      primaryButtonLabel: 'OK',
    };

    dialog.open(ConfirmDialog, {
      data: dialogData,
      disableClose: true,
    });
  };

  /** Handles empty owner warning by showing a confirmation dialog. */
  const emptyOwnerWarning = (
    configType: 'device' | 'host',
    onConfirm: () => void,
    onCancel?: () => void,
  ) => {
    const dialogData = {
      title: 'Security Warning',
      content:
        configType === 'device'
          ? 'No owners are set for this device configuration. This device will be public, allowing anyone to view and edit it. Are you sure you want to proceed?'
          : 'No host admins are set for this host configuration. This host will be public, allowing anyone to view and edit it. Are you sure you want to proceed?',
      type: 'warning',
      primaryButtonLabel: 'Proceed anyway',
      secondaryButtonLabel: 'Go back',
    };
    dialog
      .open(ConfirmDialog, {
        data: dialogData,
        disableClose: true,
      })
      .afterClosed()
      .subscribe((result) => {
        if (result === 'secondary') {
          (onCancel || options.onCancelSelfLockout)();
          return;
        }
        if (result === 'primary') {
          onConfirm();
        }
      });
  };

  return {
    success,
    error,
    selfLockout,
    emptyOwnerWarning,
  };
}
