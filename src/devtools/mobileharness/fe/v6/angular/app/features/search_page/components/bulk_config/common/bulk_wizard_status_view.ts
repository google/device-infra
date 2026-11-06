import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTableModule} from '@angular/material/table';

import {BulkApplyError} from '../../../models';
import {formatEntityCount} from '../../../utils';

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
  imports: [MatIconModule, MatProgressSpinnerModule, MatTableModule],
})
export class BulkWizardStatusView {
  readonly errorColumns = ['deviceId', 'reason'] as const;

  /** Which status view to render. */
  readonly mode = input.required<
    'loading' | 'step_error' | 'applying' | 'success' | 'errors'
  >();

  /** Status message displayed in `'loading'` or `'step_error'` mode. */
  readonly message = input<string>('');

  /** Optional context headline displayed above the error detail in `'step_error'` mode. */
  readonly errorTitle = input<string | null>(null);

  /** Total number of devices targeted for the batch update. */
  readonly willUpdateCount = input<number>(0);

  /** Per-device failure rows when `mode()` is `'errors'` after applying. */
  readonly applyErrors = input<readonly BulkApplyError[]>([]);

  /** Support subtitle rendered below the headline in `'success'` mode. */
  readonly successSupportText = input<string>('');

  /** Singular entity noun used to pluralize counts (`'device'` or `'host'`). */
  readonly entityNoun = input<'device' | 'host'>('device');

  /** Column header for the identifier column in the failure table. */
  readonly entityHeader = input<string>('Device ID');

  /** Formatted label for the total number of entities targeted for the update. */
  readonly willUpdateLabel = computed<string>(() =>
    formatEntityCount(this.willUpdateCount(), this.entityNoun()),
  );

  /** Formatted label for the number of entities that succeeded when errors occurred. */
  readonly applySuccessLabel = computed<string>(() =>
    formatEntityCount(
      Math.max(0, this.willUpdateCount() - this.applyErrors().length),
      this.entityNoun(),
    ),
  );

  /** Formatted label for the number of entities that failed when errors occurred. */
  readonly applyErrorLabel = computed<string>(() =>
    formatEntityCount(this.applyErrors().length, this.entityNoun()),
  );
}
