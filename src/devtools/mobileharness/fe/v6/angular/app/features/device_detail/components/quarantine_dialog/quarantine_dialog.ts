import {CommonModule, DatePipe} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {finalize} from 'rxjs/operators';
import {
  QuarantineDeviceRequest,
  QuarantineDialogData,
} from '../../../../core/models/device_action';
import {DEVICE_SERVICE} from '../../../../core/services/device/device_service';
import {Dialog} from '../../../../shared/components/config_common/dialog/dialog';

/**
 * Dialog for quarantining a device or updating its quarantine duration.
 */
@Component({
  selector: 'app-quarantine-dialog',
  standalone: true,
  templateUrl: './quarantine_dialog.ng.html',
  styleUrls: ['./quarantine_dialog.scss'],
  // encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    DatePipe,
    Dialog,
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
})
export class QuarantineDialog implements OnInit {
  private readonly deviceService = inject(DEVICE_SERVICE);
  private readonly dialogRef = inject(MatDialogRef<QuarantineDialog>);
  readonly dialogData: QuarantineDialogData = inject(MAT_DIALOG_DATA);

  durationHours = 1;
  durationError = '';
  expiryTime = '';
  quarantining = signal(false);

  ngOnInit() {
    this.onDurationChange();
  }

  onDurationChange() {
    this.validateDuration();
    if (!this.durationError) {
      const expiry = new Date(Date.now() + this.durationHours * 3600 * 1000);
      this.expiryTime = expiry.toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        hour12: true,
      });
    } else {
      this.expiryTime = '';
    }
  }

  validateDuration(): boolean {
    const duration = this.durationHours;
    if (
      duration === null ||
      isNaN(duration) ||
      duration < 1 ||
      duration > 168 ||
      !Number.isInteger(duration)
    ) {
      this.durationError = 'Duration must be an integer between 1 and 168.';
      return false;
    }
    this.durationError = '';
    return true;
  }

  setDuration(hours: number) {
    this.durationHours = hours;
    this.onDurationChange();
  }

  applyQuarantine() {
    if (!this.validateDuration()) {
      return;
    }
    this.quarantining.set(true);
    const request: QuarantineDeviceRequest = {
      durationHours: this.durationHours,
    };
    this.deviceService
      .quarantineDevice(this.dialogData.deviceId, request)
      .pipe(
        finalize(() => {
          this.quarantining.set(false);
        }),
      )
      .subscribe({
        next: (response) => {
          this.dialogRef.close(true);
        },
        error: (err) => {
          // TODO: Show error in snackbar or dialog
          console.error('Failed to quarantine device:', err);
        },
      });
  }
}
