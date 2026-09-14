import {inject, signal} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';
import {finalize, take} from 'rxjs/operators';

import {JOB_SERVICE} from '../../core/services/job/job_service';
import {ConfirmDialog} from '../components/confirm_dialog/confirm_dialog';
import {KillJobConfirmContent} from '../components/kill_job_confirm_content/kill_job_confirm_content';
import {SnackBarService} from '../services/snackbar_service';

/**
 * Composable function encapsulating job actions like killing a job,
 * managing the confirmation dialog, loading/in-progress state,
 * snackbar notifications, and reloading.
 */
export function useJobActions() {
  const jobService = inject(JOB_SERVICE);
  const dialog = inject(MatDialog);
  const snackBar = inject(SnackBarService);

  const isKillingJob = signal(false);

  const killJob = (jobId: string, onReload?: () => void) => {
    const dialogRef = dialog.open(ConfirmDialog, {
      panelClass: 'confirm-dialog-panel',
      data: {
        title: 'Kill Job?',
        contentComponent: KillJobConfirmContent,
        type: 'error',
        primaryButtonLabel: 'Kill Job',
        secondaryButtonLabel: 'Cancel',
      },
      disableClose: true,
    });

    dialogRef
      .afterClosed()
      .pipe(take(1))
      .subscribe((result) => {
        if (result === 'primary') {
          isKillingJob.set(true);
          jobService
            .killJob(jobId)
            .pipe(
              finalize(() => {
                isKillingJob.set(false);
              }),
            )
            .subscribe({
              next: () => {
                snackBar.showSuccess(
                  'Successfully sent kill job request to Master.',
                );
                onReload?.();
              },
              error: (err: unknown) => {
                console.error('Failed to kill job:', err);
                const e = err as {message?: string};
                snackBar.showError(e?.message || 'Failed to terminate job.');
              },
            });
        }
      });
  };

  return {
    isKillingJob: isKillingJob.asReadonly(),
    killJob,
  };
}
