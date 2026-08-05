import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
  inject,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {RouterModule} from '@angular/router';
import {DebugService} from '../../../core/services/debug_service';
import {ClipboardService} from '../../services/clipboard_service';
import {SnackBarService} from '../../services/snackbar_service';
import {reportBug} from '../../utils/error_utils';

/**
 * Component to display a user-friendly "Error" page for entities like Hosts or Devices.
 */
@Component({
  selector: 'app-error-content',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule, RouterModule],
  templateUrl: './error_content.ng.html',
  styleUrls: ['./error_content.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ErrorContent {
  @Input() errorMessage = 'An unexpected error occurred.';
  @Input() errorDetails = '';
  @Input() errorTitle = 'Error Occurred';
  @Input() pageType: 'host' | 'device' = 'host';

  @Output() readonly refresh = new EventEmitter<void>();

  readonly debugService = inject(DebugService);
  private readonly clipboardService = inject(ClipboardService);
  private readonly snackBar = inject(SnackBarService);

  showDetails = false;

  /**
   * Toggles the visibility of the detailed error/stack trace view.
   */
  toggleDetails() {
    this.showDetails = !this.showDetails;
  }

  /**
   * Copies the error message and technical details to the user's clipboard and
   * shows a snackbar notification.
   */
  copyError() {
    const textToCopy = `Error: ${this.errorMessage}\nDetails: ${this.errorDetails}`;
    if (this.clipboardService.copyToClipboard(textToCopy)) {
      this.snackBar.showSuccess('Error copied to clipboard');
    } else {
      this.snackBar.showError('Failed to copy error');
    }
  }

  /**
   * Generates a pre-filled Buganizer ticket URL with the error information and
   * opens it in a new browser tab.
   */
  reportBug() {
    reportBug(this.errorTitle, this.errorMessage, this.errorDetails);
  }
}
