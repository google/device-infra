import {DeviceTarget} from '../../../core/models/device_config_models';

/** Input data structure passed into bulk device configuration dialogs. */
export interface BulkConfigDialogData {
  devices: DeviceTarget[];
  universe?: string;
  fleet?: string;
}

/** Standard result returned when a bulk device configuration dialog closes. */
export interface BulkConfigDialogResult {
  updated: boolean;
}

/** Consolidated counts and formatted labels for the bulk configuration review step. */
export interface BulkConfigReviewSummary {
  willUpdateCount: number;
  willUpdateLabel: string;
  alreadyMatchCount: number;
  alreadyMatchLabel: string;
  readOnlyCount: number;
  readOnlyLabel: string;
}

/** Per-device failure entry from a batch configuration update. */
export interface BulkApplyError {
  deviceId: string;
  reason: string;
}
