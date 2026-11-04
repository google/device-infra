import {NgTemplateOutlet} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  contentChild,
  input,
  TemplateRef,
} from '@angular/core';
import {MatTableModule} from '@angular/material/table';

import {DeviceConfigChangeStatus} from '../../../../../core/models/device_config_models';
import {BulkStatusPill} from './bulk_status_pill';

/**
 * Shared Step 2/3 device review table for bulk configuration wizards.
 *
 * Encapsulates the Device ID column (`deviceId` + `hostName`), Status column
 * (`<app-bulk-status-pill>`), and sticky `mat-table` container, while projecting
 * the domain-specific current value cell via `#currentValueCell`.
 */
@Component({
  selector: 'app-bulk-review-table',
  standalone: true,
  templateUrl: './bulk_review_table.ng.html',
  styleUrl: './bulk_review_table.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BulkStatusPill, MatTableModule, NgTemplateOutlet],
})
export class BulkReviewTable<
  TEntry extends {
    deviceId: string;
    hostName?: string;
    status?: DeviceConfigChangeStatus;
    readOnlyReason?: string;
  },
> {
  readonly columns = ['deviceId', 'currentValue', 'status'] as const;

  /** Device rows to render in the review table. */
  readonly rows = input.required<readonly TEntry[]>();

  /** Header text for the middle current-configuration column. */
  readonly currentHeader = input.required<string>();

  /** Accessible label for the review table. */
  readonly ariaLabel = input<string>('Device change review table');

  /** Projected template rendering the current configuration cell for each row. */
  readonly currentValueCell =
    contentChild.required<TemplateRef<{$implicit: TEntry}>>('currentValueCell');
}
