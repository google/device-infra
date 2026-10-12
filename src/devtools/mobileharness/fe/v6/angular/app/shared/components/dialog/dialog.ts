import {NgTemplateOutlet} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  TemplateRef,
  computed,
  contentChild,
  input,
  output,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';

import {WritePermissionResult} from '../../../core/models/action_common';
import {SafeHtmlPipe} from '../../pipes/safe_html_pipe';
import {Footer} from '../config_common/footer/footer';

/** Entity type for permission-checked dialogs. */
export type DialogEntityType = 'device' | 'host';

/** Footer layout variant for common dialog. */
export type DialogFooterType = 'normal' | 'permission-check' | 'none';

/** Parameters identifying the target resource for write permission checks. */
export interface DialogPermissionParam {
  deviceId: string;
  hostName: string;
  universe?: string;
}

/**
 * Common configuration dialog component.
 *
 * Implements Angular 20+ signal-based reactivity:
 * - Signal inputs, outputs, and content child queries.
 * - Host accessibility metadata (`role="dialog"`, `aria-modal="true"`, `aria-label`).
 * - Computed dimension helpers and header visibility signals.
 * - CSS custom property design tokens (`--dialog-*`) for clean styling without prop bloat.
 */
@Component({
  selector: 'app-dialog',
  standalone: true,
  templateUrl: './dialog.ng.html',
  styleUrl: './dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    'role': 'dialog',
    '[attr.aria-modal]': 'true',
    '[attr.aria-label]': 'dialogTitle() || null',
  },
  imports: [
    NgTemplateOutlet,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    Footer,
    SafeHtmlPipe,
  ],
})
export class Dialog {
  // ===========================================================================
  // 1. Dimensions & Sizing
  // ===========================================================================

  readonly width = input<string>('48rem');
  readonly height = input<string>('');
  readonly maxHeight = input<string>('');
  readonly maxWidth = input<string>('');

  /** Effective maximum width defaulting to width when maxWidth is unspecified. */
  readonly effectiveMaxWidth = computed<string>(
    () => this.maxWidth() || this.width(),
  );

  // ===========================================================================
  // 2. Header Content & Visibility
  // ===========================================================================

  readonly dialogTitle = input<string>('');
  readonly subtitle = input<string>('');
  readonly showHeader = input<boolean>(true);

  /** Whether the default header has non-empty title or subtitle content to render. */
  readonly hasHeaderContent = computed<boolean>(() =>
    Boolean(this.dialogTitle().trim() || this.subtitle().trim()),
  );

  // ===========================================================================
  // 3. Footer & Permission Authorization
  // ===========================================================================

  readonly footerType = input<DialogFooterType>('permission-check');
  readonly type = input<DialogEntityType>('device');
  readonly param = input<DialogPermissionParam>({
    deviceId: '',
    hostName: '',
  });
  readonly grantedMessage = input<string>();
  readonly deniedMessage = input<string>();

  // ===========================================================================
  // 4. Queries & Outputs
  // ===========================================================================

  readonly onPermissionChange = output<WritePermissionResult>();

  readonly actionsTemplate =
    contentChild<TemplateRef<unknown>>('actionsTemplate');
}
