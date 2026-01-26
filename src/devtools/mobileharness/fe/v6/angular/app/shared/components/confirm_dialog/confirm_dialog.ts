import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnInit,
  signal,
  Type,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {Observable} from 'rxjs';
import {finalize} from 'rxjs/operators';

/**
 * A dialog to confirm the user's action.
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  templateUrl: './confirm_dialog.ng.html',
  styleUrl: './confirm_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatProgressSpinnerModule,
  ],
})
export class ConfirmDialog implements OnInit {
  readonly data = inject<{
    title: string;
    content?: string;
    contentComponent?: Type<{}>;
    contentComponentInputs?: Record<string, {}>;
    type: 'info' | 'success' | 'warning' | 'error';
    primaryButtonLabel: string;
    primaryButtonIcon?: string;
    secondaryButtonLabel?: string;
    onConfirm?: () => Observable<void>;
  }>(MAT_DIALOG_DATA);

  private readonly dialogRef = inject(MatDialogRef<ConfirmDialog>);

  isLoading = signal(false);

  ngOnInit() {}

  onConfirm() {
    if (this.data.onConfirm) {
      this.isLoading.set(true);
      this.data
        .onConfirm()
        .pipe(
          finalize(() => {
            this.isLoading.set(false);
          }),
        )
        .subscribe({
          next: () => {
            this.dialogRef.close('primary');
          },
          error: () => {
            // Optional: Handle error or keep dialog open
            // For now, we assume the caller handles error reporting (e.g. snackbar)
          },
        });
    } else {
      this.dialogRef.close('primary');
    }
  }

  get iconUI() {
    switch (this.data.type) {
      case 'info':
        return {
          icon: 'info',
          iconColorClass: 'info-icon',
          iconBgColorClass: 'bg-gray-100',
        };
      case 'success':
        return {
          icon: 'check_circle',
          iconColorClass: 'success-icon',
          iconBgColorClass: 'bg-green-100',
        };
      case 'warning':
        return {
          icon: 'warning_amber',
          iconColorClass: 'warning-icon',
          iconBgColorClass: 'bg-yellow-100',
        };
      case 'error':
      default:
        return {
          icon: 'error',
          iconColorClass: 'error-icon',
          iconBgColorClass: 'bg-red-100',
        };
    }
  }
}
