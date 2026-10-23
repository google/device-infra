import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';

import {BulkApplyError} from '../../../models';
import {formatDeviceCount} from './bulk_config_utils';

/**
 * Shared presentational component for the loading, error, applying, success,
 * and partial/full error completion views of a bulk configuration wizard dialog.
 *
 * Projects the domain-specific target summary card (`<div class="bw-new-card bw-success-card">`)
 * inside the `'success'` state via `<ng-content />`.
 */
@Component({
  selector: 'app-bulk-wizard-status-view',
  standalone: true,
  templateUrl: './bulk_wizard_status_view.ng.html',
  styleUrl: './bulk_wizard_status_view.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, MatProgressSpinnerModule],
})
export class BulkWizardStatusView {
  /** Which status view to render. */
  readonly mode = input.required<
    'loading' | 'error' | 'applying' | 'success' | 'errors'
  >();

  /** Status message displayed in `'loading'` or `'error'` mode. */
  readonly message = input<string>('');

  /** Total number of devices targeted for the batch update. */
  readonly willUpdateCount = input<number>(0);

  /** Number of devices that succeeded when partial/full errors occurred. */
  readonly applySuccessCount = input<number>(0);

  /** Per-device failure rows when `mode()` is `'errors'`. */
  readonly applyErrors = input<readonly BulkApplyError[]>([]);

  /** Support subtitle rendered below the headline in `'success'` mode. */
  readonly successSupportText = input<string>('');

  readonly formatDeviceCount = formatDeviceCount;
}
