import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {openInNewTab} from 'app/shared/utils/safe_dom';

/**
 * Data passed to the ComingSoonDialog.
 */
export interface ComingSoonDialogData {
  title?: string;
  message?: string;
  icon?: string;
  legacyPageUrl?: string;
  legacyScreenshotLink?: string;
  confirmLabel?: string;
  confirmIcon?: string;
  onConfirm?: () => void;
}

/**
 * A dialog component for unimplemented features or "Coming Soon" alerts, with a modern visual style.
 */
@Component({
  selector: 'app-coming-soon-dialog',
  standalone: true,
  templateUrl: './coming_soon_dialog.ng.html',
  styleUrl: './coming_soon_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatDialogModule],
})
export class ComingSoonDialog {
  readonly data = inject<ComingSoonDialogData>(MAT_DIALOG_DATA, {
    optional: true,
  });
  private readonly dialogRef = inject(MatDialogRef<ComingSoonDialog>);

  get title(): string {
    return this.data?.title || 'Coming soon';
  }

  get message(): string {
    return (
      this.data?.message ||
      'This feature is not yet available in the new console. Please switch to the legacy page to use this feature.'
    );
  }

  get icon(): string {
    return this.data?.icon || 'construction';
  }

  get confirmLabel(): string {
    return this.data?.confirmLabel || 'Go to Legacy Page';
  }

  get confirmIcon(): string {
    return this.data?.confirmIcon || 'open_in_new';
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onConfirm(): void {
    if (this.data?.onConfirm) {
      this.data.onConfirm();
    } else if (this.data?.legacyPageUrl) {
      // Default action: navigate to the legacy page URL if provided.
      openInNewTab(this.data.legacyPageUrl);
    }
    this.dialogRef.close('confirm');
  }
}
