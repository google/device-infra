import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MAT_DIALOG_DATA, MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';

/**
 * Data passed to the Logcat Link Dialog.
 */
export interface LogcatLinkDialogData {
  logUrl: string;
}

/**
 * Dialog to show the logcat stream link when it was opened (or blocked).
 */
@Component({
  selector: 'app-logcat-link-dialog',
  standalone: true,
  templateUrl: './logcat_link_dialog.ng.html',
  styleUrl: './logcat_link_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatButtonModule, MatDialogModule, MatIconModule],
})
export class LogcatLinkDialog {
  readonly data: LogcatLinkDialogData = inject(MAT_DIALOG_DATA);
}
