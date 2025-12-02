import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MAT_DIALOG_DATA, MatDialogModule} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {dateUtils} from 'app/shared/utils/date_utils';
import {
  createSafeObjectURL,
  openInNewTab,
  revokeObjectURL,
  setSafeHref,
} from 'app/shared/utils/safe_dom';
import {LogcatDialogData} from '../../../../core/models/device_action';
import {Dialog} from '../../../../shared/components/config_common/dialog/dialog';
import {SnackBarService} from '../../../../shared/services/snackbar_service';

/**
 * Logcat dialog component.
 */
@Component({
  selector: 'app-logcat-dialog',
  standalone: true,
  templateUrl: './logcat_dialog.ng.html',
  styleUrl: './logcat_dialog.scss',
  // encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    Dialog,
  ],
})
export class LogcatDialog implements OnInit {
  readonly data: LogcatDialogData = inject(MAT_DIALOG_DATA);
  private readonly snackBar = inject(SnackBarService);
  isDownloading = signal(false);
  searchTerm = '';
  allLogLines: string[] = [];
  filteredLogLines = signal<string[]>([]);

  ngOnInit() {
    this.allLogLines = this.data.logContent.split('\n');
    this.filteredLogLines.set(this.allLogLines);
  }

  getDisplayTimestamp(): string {
    return dateUtils.format(this.data.capturedAt);
  }

  filterLogs() {
    if (!this.searchTerm) {
      this.filteredLogLines.set(this.allLogLines);
      return;
    }
    const searchLower = this.searchTerm.toLowerCase();
    this.filteredLogLines.set(
      this.allLogLines.filter((line) =>
        line.toLowerCase().includes(searchLower),
      ),
    );
  }

  async downloadLog() {
    this.isDownloading.set(true);
    try {
      const response = await fetch(this.data.logUrl);
      const blob = await response.blob();
      const url = createSafeObjectURL(blob);
      const a = document.createElement('a');
      a.style.display = 'none';
      setSafeHref(a, url);
      a.download = `logcat-${this.data.deviceId}-${dateUtils.formatFileTimestamp(
        new Date(this.data.capturedAt),
      )}.log`;
      document.body.appendChild(a);
      a.click();
      revokeObjectURL(url);
      a.remove();
      this.snackBar.showSuccess('Log downloaded successfully');
    } catch (e) {
      console.error('Failed to download log', e);
      this.snackBar.showError('Error: Could not download log.');
    } finally {
      this.isDownloading.set(false);
    }
  }

  viewInNewTab() {
    const blob = new Blob([this.data.logContent], {
      type: 'text/plain;charset=utf-8',
    });
    const url = createSafeObjectURL(blob);
    openInNewTab(url);
    // NOTE: We don't call `revokeObjectURL` here. The browser will automatically
    // release the object URL when the document in the new tab is closed.
    // Revoking it immediately can cause a race condition where the new tab
    // fails to load the content.
  }
}
