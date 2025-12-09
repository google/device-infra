import {CommonModule, DatePipe} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnInit,
  signal,
  ViewEncapsulation,
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
import {MatTabsModule} from '@angular/material/tabs';
import {dateUtils} from 'app/shared/utils/date_utils';
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
  encapsulation: ViewEncapsulation.None,
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
    MatTabsModule,
  ],
})
export class QuarantineDialog implements OnInit {
  private readonly deviceService = inject(DEVICE_SERVICE);
  private readonly dialogRef = inject(MatDialogRef<QuarantineDialog>);
  readonly dialogData: QuarantineDialogData = inject(MAT_DIALOG_DATA);

  formattedCurrentExpiry = '';
  durationMinutes = 60;
  durationError = '';
  endTime = '';
  endTimeError = '';
  expiryTime = '';
  quarantining = signal(false);
  selectedTabIndex = 0;
  isFormValid = signal(true);

  timeZoneOffset = '';

  ngOnInit() {
    this.timeZoneOffset = this.getTimeZoneOffset();
    this.onDurationChange();
    if (this.dialogData.currentExpiry) {
      this.formattedCurrentExpiry = dateUtils.format(
        this.dialogData.currentExpiry,
      );
    }
  }

  private formatToDateTimeLocal(date: Date): string {
    const YYYY = date.getFullYear();
    const MM = String(date.getMonth() + 1).padStart(2, '0');
    const DD = String(date.getDate()).padStart(2, '0');
    const hh = String(date.getHours()).padStart(2, '0');
    const mm = String(date.getMinutes()).padStart(2, '0');
    return `${YYYY}-${MM}-${DD}T${hh}:${mm}`;
  }

  private getTimeZoneOffset(): string {
    const now = new Date();
    const offsetMinutes = now.getTimezoneOffset();
    const offsetHours = Math.abs(Math.floor(offsetMinutes / 60));
    const offsetMin = Math.abs(offsetMinutes % 60);
    const sign = offsetMinutes > 0 ? '-' : '+';
    return `GMT${sign}${offsetHours}${offsetMin > 0 ? ':' + String(offsetMin).padStart(2, '0') : ''}`;
  }

  onDurationChange() {
    const isValid = this.validateDuration();
    this.isFormValid.set(isValid);
    if (isValid) {
      const expiry = new Date(Date.now() + this.durationMinutes * 60 * 1000);
      this.expiryTime = dateUtils.format(expiry.toISOString());
      this.endTime = this.formatToDateTimeLocal(expiry);
    } else {
      this.expiryTime = '';
    }
  }

  onEndTimeChange() {
    const isValid = this.validateEndTime();
    this.isFormValid.set(isValid);
    if (isValid) {
      const endTime = new Date(this.endTime);
      const durationMs = endTime.getTime() - Date.now();
      this.durationMinutes = Math.round(durationMs / 60000);
      this.expiryTime = ''; // Hide preview when editing end time
    }
  }

  onTabChange() {
    if (this.selectedTabIndex === 0) {
      this.isFormValid.set(this.validateDuration());
    } else {
      this.isFormValid.set(this.validateEndTime());
    }
  }

  validateDuration(): boolean {
    const duration = this.durationMinutes;
    if (
      duration === null ||
      isNaN(duration) ||
      duration < 10 ||
      duration > 10080 || // 7 days
      !Number.isInteger(duration)
    ) {
      this.durationError = 'Duration must be between 10 and 10080 minutes.';
      return false;
    }
    this.durationError = '';
    return true;
  }

  validateEndTime(): boolean {
    if (!this.endTime) {
      this.endTimeError = 'End time is required.';
      return false;
    }
    const now = new Date();
    const minTime = new Date(now.getTime() + 10 * 60 * 1000); // 10 minutes from now
    const maxTime = new Date(now.getTime() + 10080 * 60 * 1000); // 7 days from now
    const selectedTime = new Date(this.endTime);

    if (selectedTime < minTime || selectedTime > maxTime) {
      this.endTimeError =
        'End time must be at least 10 minutes in the future and within 7 days.';
      return false;
    }
    this.endTimeError = '';
    return true;
  }

  setDuration(minutes: number) {
    this.durationMinutes = minutes;
    this.onDurationChange();
  }

  applyQuarantine() {
    let endTime: string;
    if (this.selectedTabIndex === 0) {
      // By Duration
      if (!this.validateDuration()) {
        return;
      }
      const expiry = new Date(Date.now() + this.durationMinutes * 60 * 1000);
      endTime = expiry.toISOString();
    } else {
      // By End Time
      if (!this.validateEndTime()) {
        return;
      }
      // The `this.endTime` from datetime-local input is a string like
      // 'YYYY-MM-DDTHH:mm'. new Date() will parse this string in the browser's
      // local timezone.
      const localDate = new Date(this.endTime);
      endTime = localDate.toISOString();
    }

    const request: QuarantineDeviceRequest = {endTime};

    this.quarantining.set(true);

    this.deviceService
      .quarantineDevice(this.dialogData.deviceId, request)
      .pipe(
        finalize(() => {
          this.quarantining.set(false);
        }),
      )
      .subscribe({
        next: () => {
          this.dialogRef.close(true);
        },
        error: (err) => {
          // TODO: Show error in snackbar or dialog
          console.error('Failed to quarantine device:', err);
        },
      });
  }
}
