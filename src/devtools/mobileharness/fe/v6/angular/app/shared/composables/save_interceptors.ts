import {DestroyRef, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatDialog} from '@angular/material/dialog';
import {ConfirmDialog} from '../components/confirm_dialog/confirm_dialog';

/**
 * Composable function encapsulating save interception warning logic.
 * Opens a confirmation dialog when the user attempts to save with empty data.
 */
export function useSaveInterceptors() {
  const dialog = inject(MatDialog);
  const destroyRef = inject(DestroyRef);

  const promptEmptyData = (
    type: 'dimensions' | 'properties',
    onConfirm: () => void,
  ) => {
    const capitalizedType = type.charAt(0).toUpperCase() + type.slice(1);
    const dialogData = {
      title: `Incomplete ${capitalizedType}`,
      content: `Some of the ${type} you added are incomplete (missing a name or value). If you proceed, these incomplete ${type} will be discarded. Do you want to continue saving?`,
      type: 'warning',
      primaryButtonLabel: 'Save Anyway',
      secondaryButtonLabel: 'Cancel',
    };
    const dialogRef = dialog.open(ConfirmDialog, {
      data: dialogData,
      disableClose: true,
    });
    dialogRef
      .afterClosed()
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe((result) => {
        if (result === 'primary') {
          onConfirm();
        }
      });
  };

  return {promptEmptyData};
}
