import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';

import {BulkWizardSession} from './bulk_wizard_session';

/**
 * Shared footer action buttons for bulk configuration wizard dialogs.
 *
 * Handles all wizard lifecycle modes (`'loading'`, `'step_error'`, `'review'`,
 * `'applying'`, `'success'`, `'errors'`, and active configuration steps).
 */
@Component({
  selector: 'app-bulk-wizard-footer-actions',
  standalone: true,
  templateUrl: './bulk_wizard_footer_actions.ng.html',
  styleUrl: './bulk_wizard_footer_actions.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BulkWizardFooterActions {
  /** Active wizard session state. */
  readonly wizard =
    input.required<
      Pick<
        BulkWizardSession,
        'viewMode' | 'reviewSummary' | 'applyRpcError' | 'retryStep' | 'close'
      >
    >();

  /** Whether the primary 'Next' button is enabled in the active configuration step. */
  readonly canProceed = input<boolean>(false);

  /** Whether the active configuration step shows 'Back' instead of 'Cancel'. */
  readonly showStepBack = input<boolean>(false);

  /** Emitted when the user clicks 'Back' in a multi-step configuration flow (e.g. Values -> Key). */
  readonly stepBack = output<void>();

  /** Emitted when the user clicks 'Next' in an active configuration step. */
  readonly stepNext = output<void>();

  /** Emitted when the user clicks 'Back' from the review or step_error screen. */
  readonly backFromReview = output<void>();

  /** Emitted when the user clicks 'Apply to N devices' or 'Retry Apply' on the review screen. */
  readonly apply = output<void>();
}
