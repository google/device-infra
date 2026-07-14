import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatButtonModule} from '@angular/material/button';
import {MAT_DIALOG_DATA, MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {RolloutResponse} from '@deviceinfra/app/core/models/host_action';
import {SnackBarService} from '@deviceinfra/app/shared/services/snackbar_service';
import {openInNewTab} from '@deviceinfra/app/shared/utils/safe_dom';
import {Observable} from 'rxjs';
import {LabServerOperation} from '../lab_server_operation_content/lab_server_operation_content';

/**
 * Data required for the TrackingDialog to monitor the release process.
 */
export interface TrackingDialogData {
  hostName: string;
  version: string;
  flags?: string;
  response$: Observable<RolloutResponse>;
  operation: LabServerOperation;
}

/**
 * Dialog component to track the progress of a Lab Server release.
 * It displays the rollout status and provides tracking links.
 */
@Component({
  selector: 'app-tracking-dialog',
  standalone: true,
  templateUrl: './tracking_dialog.ng.html',
  styleUrl: './tracking_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatDialogModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
})
export class TrackingDialog implements OnInit {
  readonly data: TrackingDialogData = inject(MAT_DIALOG_DATA);
  private readonly snackBar = inject(SnackBarService);
  private readonly destroyRef = inject(DestroyRef);

  readonly isInitialized = signal<boolean>(false);
  readonly trackingUrl = signal<string>('');
  readonly errorMessage = signal<string>('');

  ngOnInit() {
    this.data.response$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (res) => {
        this.isInitialized.set(true);
        this.trackingUrl.set(res.trackingUrl || '');
      },
      error: (err) => {
        this.isInitialized.set(true);
        this.errorMessage.set(
          err.statusText || err.message || 'Failed to generate link',
        );
      },
    });
  }

  copyLink() {
    if (this.trackingUrl()) {
      navigator.clipboard.writeText(this.trackingUrl());
      this.snackBar.showSuccess('Link copied to clipboard');
    }
  }

  openLink() {
    if (this.trackingUrl()) {
      openInNewTab(this.trackingUrl());
    }
  }
}
