import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';

/**
 * Shared Step 1 banner displayed when the user selects the clear/unset option
 * (e.g. "No default Wi-Fi" or "Not set" dimension), with an action button to
 * switch back to value entry mode.
 */
@Component({
  selector: 'app-bulk-clear-banner',
  standalone: true,
  templateUrl: './bulk_clear_banner.ng.html',
  styleUrl: './bulk_clear_banner.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule],
})
export class BulkClearBanner {
  /** Material Symbol icon name displayed on the left of the banner. */
  readonly icon = input.required<string>();

  /** Primary title text of the clear state (e.g. 'No default Wi-Fi' or 'Not set'). */
  readonly title = input.required<string>();

  /** Supporting explanation text below the title. */
  readonly subtitle = input.required<string>();

  /** Label for the trailing action button that exits clear mode. */
  readonly actionLabel = input.required<string>();

  /** Emitted when the user clicks the trailing action button to exit clear mode. */
  readonly action = output<void>();
}
