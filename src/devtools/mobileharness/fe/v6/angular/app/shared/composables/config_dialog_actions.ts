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
export function useConfigDialogActions<T>(options: ConfigDialogActionsOptions<T>) {
  const dialog = inject(MatDialog);

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
        options.dialogRef?.close(true);
        options.onSuccessClose?.();
      });
  };

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
          options.onCancelSelfLockout();
          return;
        }
        if (result === 'primary') {
          options.onSubmitOverride();
        }
      });
  };

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

  return {
    success,
    error,
    selfLockout,
  };
}
