import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';

import {DeviceConfigChangeStatus} from '../../../../../core/models/device_config_models';
import {BulkConfigReviewSummary} from '../../../models';
import {formatDeviceCount} from './bulk_config_utils';

/**
 * Shared Step 2 review summary banner for bulk configuration wizards.
 *
 * Renders the target icon, projected target headline (`<ng-content />`),
 * target subtitle, status count chips (`will update`, `already match`, `read-only`),
 * and the conditional informational banner when `summary().willUpdateCount === 0`.
 */
@Component({
  selector: 'app-bulk-review-header-banner',
  standalone: true,
  templateUrl: './bulk_review_header_banner.ng.html',
  styleUrl: './bulk_review_header_banner.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule],
})
export class BulkReviewHeaderBanner {
  /** Material icon name displayed in the target badge. */
  readonly icon = input.required<string>();

  /** Subtitle displayed beneath the projected target headline. */
  readonly subtitle = input.required<string>();

  /** Aggregated device status summary for the review step. */
  readonly summary =
    input.required<
      BulkConfigReviewSummary<{status?: DeviceConfigChangeStatus}>
    >();

  /** Informational message shown when `willUpdateCount === 0`. */
  readonly nothingToUpdateMessage = input<string>('');

  readonly formatDeviceCount = formatDeviceCount;
}
