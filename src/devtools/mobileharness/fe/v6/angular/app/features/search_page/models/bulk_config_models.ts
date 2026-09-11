import {
  DeviceConfigChangeStatus,
  DeviceWifi,
} from '../../../core/models/device_config_models';

/** Base input data structure passed into bulk device configuration dialogs. */
export interface BulkConfigDialogBaseData {
  deviceIds: string[];
  universe?: string;
  fleet?: string;
}

/** Standard result returned when a bulk device configuration dialog closes. */
export interface BulkConfigDialogResult {
  updated: boolean;
}

/** Consolidated counts and target rows for the bulk configuration review step. */
export interface BulkConfigReviewSummary<
  T extends {status?: DeviceConfigChangeStatus},
> {
  willUpdateRows: T[];
  willUpdateCount: number;
  alreadyMatchCount: number;
  readOnlyCount: number;
}

/** Per-device failure entry from a batch configuration update. */
export interface BulkApplyError {
  deviceId: string;
  reason: string;
}

/** Terminal outcome of a bulk configuration apply operation. */
export type BulkApplyResult = 'success' | 'errors';

/** Mutually exclusive view states derived from a bulk wizard's semantic phases (`TPhase`). */
export type BulkWizardViewMode<TPhase extends string> =
  | TPhase
  | `${TPhase}_loading`
  | `${TPhase}_error`
  | 'applying'
  | 'success'
  | 'errors';

/** Semantic wizard phases for bulk Wi-Fi configuration. */
export type WifiWizardPhase = 'network' | 'review';

/** Option in the Wi-Fi network selection list (current on selected devices or suggested by backend). */
export interface BulkWifiOption {
  wifi: DeviceWifi;
  subtitle?: string;
  badge?: string;
}
