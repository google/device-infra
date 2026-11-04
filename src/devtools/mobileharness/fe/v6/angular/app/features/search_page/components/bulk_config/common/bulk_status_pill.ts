import {ChangeDetectionStrategy, Component, input} from '@angular/core';

import {DeviceConfigChangeStatus} from '../../../../../core/models/device_config_models';

/**
 * Shared status pill rendered in the review table of bulk configuration wizards
 * (`Will update`, `Already matches`, or `Read-only` with tooltip reason).
 */
@Component({
  selector: 'app-bulk-status-pill',
  standalone: true,
  templateUrl: './bulk_status_pill.ng.html',
  styleUrl: './bulk_status_pill.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BulkStatusPill {
  /** Change status for the device row. */
  readonly status = input<DeviceConfigChangeStatus | undefined>(undefined);

  /** Optional explanation shown in the native title when the device is read-only. */
  readonly readOnlyReason = input<string | undefined>(undefined);

  readonly DeviceConfigChangeStatus = DeviceConfigChangeStatus;
}
