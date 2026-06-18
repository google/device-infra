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
import {openInNewTab} from '../../utils/safe_dom';

/** Data passed to the ActionErrorContent dialog. */
export interface ActionErrorDialogData {
  errorMessage: string;
  errorDetails: string;
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

  copyError() {
    const textToCopy = `Error: ${this.errorMessage}\nDetails: ${this.errorDetails}`;
    if (this.clipboard.copy(textToCopy)) {
      this.snackBar.showSuccess('Error copied to clipboard');
    } else {
      this.snackBar.showError('Failed to copy error');
    }
  }

  reportBug() {
    const maxDetailsLength = 1000;
    let detailsForBug = this.errorDetails;
    if (detailsForBug.length > maxDetailsLength) {
      detailsForBug =
        detailsForBug.substring(0, maxDetailsLength) +
        '\n\n... (details truncated, please use "Copy Error" button in the dialog to get full details)';
    }

    const title = encodeURIComponent(
      `[MHFE] Action failed: ${this.errorMessage}`,
    );
    const body = encodeURIComponent(
      `Action failed.\n\nError: ${this.errorMessage}\n\nDetails:\n${detailsForBug}`,
    );
    const url = `https://issuetracker.google.com/issues/new?component=94628&title=${title}&description=${body}`;
    openInNewTab(url);
  }
}
