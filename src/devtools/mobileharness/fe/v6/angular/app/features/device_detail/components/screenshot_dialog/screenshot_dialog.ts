import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MAT_DIALOG_DATA, MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {dateUtils} from 'app/shared/utils/date_utils';
import {
  createSafeObjectURL,
  openInNewTab,
  revokeObjectURL,
  setSafeHref,
} from 'app/shared/utils/safe_dom';
import {ScreenshotDialogData} from '../../../../core/models/device_action';
import {Dialog} from '../../../../shared/components/config_common/dialog/dialog';
import {SnackBarService} from '../../../../shared/services/snackbar_service';

/**
 * Screenshot dialog component.
 */
@Component({
  selector: 'app-screenshot-dialog',
  standalone: true,
  templateUrl: './screenshot_dialog.ng.html',
  styleUrl: './screenshot_dialog.scss',
  // encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatDialogModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    Dialog,
  ],
})
export class ScreenshotDialog implements OnInit {
  readonly data: ScreenshotDialogData = inject(MAT_DIALOG_DATA);
  private readonly snackBar = inject(SnackBarService);
  isDownloading = signal(false);

  ngOnInit() {}

  getDisplayTimestamp(): string {
    return dateUtils.format(this.data.capturedAt);
  }

  async downloadImage() {
    this.isDownloading.set(true);
    try {
      const response = await fetch(this.data.screenshotUrl);
      const blob = await response.blob();
      const url = createSafeObjectURL(blob);
      const a = document.createElement('a');
      a.style.display = 'none';
      setSafeHref(a, url);
      a.download = `screenshot-${
        this.data.deviceId
      }-${dateUtils.formatFileTimestamp(new Date(this.data.capturedAt))}.png`;
      document.body.appendChild(a);
      a.click();
      revokeObjectURL(url);
      a.remove();
      this.snackBar.showSuccess('Screenshot downloaded');
    } catch (e) {
      console.error('Failed to download image', e);
      this.snackBar.showError('Error: Could not download image.');
    } finally {
      this.isDownloading.set(false);
    }
  }

  viewInNewTab() {
    openInNewTab(this.data.screenshotUrl);
  }

  toggleZoom(event: MouseEvent) {
    const container = document.getElementById('screenshot-image-container');
    if (container) {
      container.classList.toggle('zoomed');
      const contentWrapper = container.parentElement;
      if (contentWrapper) {
        contentWrapper.classList.toggle('zoomed');
      }
    }
  }
}
