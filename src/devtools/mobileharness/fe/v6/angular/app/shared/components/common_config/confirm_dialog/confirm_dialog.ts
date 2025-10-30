import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component, OnInit,
  inject
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MAT_DIALOG_DATA, MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';

/**
 * A dialog to confirm the user's action.
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  templateUrl: './confirm_dialog.ng.html',
  styleUrl: './confirm_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatDialogModule],
})
export class ConfirmDialog implements OnInit {
  readonly data = inject<{
    title: string;
    content: string;
    type: 'info' | 'success' | 'warning' | 'error';
    primaryButtonLabel: string;
    secondaryButtonLabel: string;
  }>(MAT_DIALOG_DATA);

  ngOnInit() {}

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
