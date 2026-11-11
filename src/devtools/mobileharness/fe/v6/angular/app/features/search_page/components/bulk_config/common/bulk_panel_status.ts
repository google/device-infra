import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';

import {BulkInlineTask} from './bulk_wizard_session';

/**
 * Shared non-blocking inline status row for Step 1 suggestion panels in bulk
 * configuration wizard dialogs. Renders an inline loading spinner while the
 * panel task is in flight, or an inline error alert with a Retry button if the
 * task fails.
 */
@Component({
  selector: 'app-bulk-panel-status',
  standalone: true,
  templateUrl: './bulk_panel_status.ng.html',
  styleUrl: './bulk_panel_status.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, MatProgressSpinnerModule],
})
export class BulkPanelStatus {
  /** Inline async task tracker created by `useInlineTask`. */
  readonly task =
    input.required<Pick<BulkInlineTask, 'isLoading' | 'error' | 'retry'>>();

  /** Status text displayed next to the spinner while the task is loading. */
  readonly loadingText = input.required<string>();
}
