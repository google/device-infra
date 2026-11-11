import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTableModule} from '@angular/material/table';

import {formatDeviceCount, formatSuccessSupportText} from '../../../utils';
import {BulkWizardSession} from './bulk_wizard_session';

/**
 * Shared presentational component for the loading, error, applying, success,
 * and partial/full error completion views of a bulk configuration wizard dialog.
 *
 * Projects the domain-specific target summary card body inside the `'success'`
 * state via `<ng-content />`.
 */
@Component({
  selector: 'app-bulk-wizard-status-view',
  standalone: true,
  templateUrl: './bulk_wizard_status_view.ng.html',
  styleUrl: './bulk_wizard_status_view.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, MatProgressSpinnerModule, MatTableModule],
})
export class BulkWizardStatusView {
  readonly errorColumns = ['deviceId', 'reason'] as const;

  /** Active wizard session state. */
  readonly wizard =
    input.required<
      Pick<
        BulkWizardSession,
        | 'viewMode'
        | 'stepError'
        | 'stepErrorTitle'
        | 'reviewSummary'
        | 'applyErrors'
      >
    >();

  /** Header label inside the target summary card in `'success'` mode. */
  readonly successCardLabel = input<string>('');

  /** Support subtitle rendered below the headline in `'success'` mode when devices were skipped. */
  readonly successSupportText = computed<string>(() =>
    formatSuccessSupportText(this.wizard().reviewSummary()),
  );

  /** Formatted label for the number of devices that succeeded when errors occurred. */
  readonly applySuccessLabel = computed<string>(() => {
    const w = this.wizard();
    return formatDeviceCount(
      Math.max(0, w.reviewSummary().willUpdateCount - w.applyErrors().length),
    );
  });

  /** Formatted label for the number of devices that failed when errors occurred. */
  readonly applyErrorLabel = computed<string>(() =>
    formatDeviceCount(this.wizard().applyErrors().length),
  );
}
