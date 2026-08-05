import {Clipboard} from '@angular/cdk/clipboard';
import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {SnackBarService} from '../../services/snackbar_service';
import {reportBug} from '../../utils/error_utils';

/** Data passed to the ActionErrorContent dialog. */
export interface ActionErrorDialogData {
  errorMessage: string;
  errorDetails: string;
  errorTitle?: string;
}

/**
 * Dialog component to display technical action error details.
 */
@Component({
  selector: 'app-action-error-content',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatDialogModule],
  templateUrl: './action_error_content.ng.html',
  styleUrls: ['./action_error_content.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActionErrorContent {
  private readonly clipboard = inject(Clipboard);
  private readonly snackBar = inject(SnackBarService);
  readonly dialogRef = inject(MatDialogRef<ActionErrorContent>);
  readonly data = inject<ActionErrorDialogData>(MAT_DIALOG_DATA);

  get errorMessage(): string {
    return this.data.errorMessage;
  }

  get errorDetails(): string {
    return this.data.errorDetails;
  }

  get errorTitle(): string {
    return this.data.errorTitle || 'Action Failed';
  }

  copyError() {
    const textToCopy = `Error: ${this.errorMessage}\nDetails: ${this.errorDetails}`;
    if (this.clipboard.copy(textToCopy)) {
      this.snackBar.showSuccess('Error copied to clipboard');
    } else {
      this.snackBar.showError('Failed to copy error');
    }
  }

  reportBug() {
    reportBug(
      this.errorTitle,
      this.errorMessage,
      this.errorDetails,
      'please use "Copy Error" button in the dialog to get full details',
    );
  }
}
