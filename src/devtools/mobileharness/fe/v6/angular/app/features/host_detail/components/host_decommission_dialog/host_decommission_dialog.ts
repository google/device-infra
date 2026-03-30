import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {Router} from '@angular/router';
import {HOST_SERVICE} from 'app/core/services/host/host_service';
import {SnackBarService} from 'app/shared/services/snackbar_service';

/** Data for DecommissionHostDialog. */
export interface HostDecommissionDialogData {
  hostName: string;
}

/**
 * Dialog for decommissioning a host.
 */
@Component({
  selector: 'app-host-decommission-dialog',
  standalone: true,
  templateUrl: './host_decommission_dialog.ng.html',
  styleUrl: './host_decommission_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatProgressSpinnerModule,
  ],
})
export class HostDecommissionDialog {
  readonly data = inject<HostDecommissionDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<HostDecommissionDialog>);
  private readonly hostService = inject(HOST_SERVICE);
  private readonly snackBar = inject(SnackBarService);
  private readonly router = inject(Router);

  isDecommissioning = signal(false);

  onConfirm() {
    this.isDecommissioning.set(true);
    this.hostService.decommissionHost(this.data.hostName).subscribe({
      next: () => {
        this.snackBar.showInfo(
          `Host ${this.data.hostName} successfully decommissioned.`,
        );
        this.router.navigate(['/'], {queryParamsHandling: 'preserve'});
        this.dialogRef.close(true);
      },
      error: (error: {message?: string}) => {
        this.snackBar.showError(
          `Failed to decommission host ${this.data.hostName}: ${
            error.message || 'Unknown error'
          }`,
        );
        this.isDecommissioning.set(false);
      },
    });
  }

  onCancel() {
    this.dialogRef.close(false);
  }
}
