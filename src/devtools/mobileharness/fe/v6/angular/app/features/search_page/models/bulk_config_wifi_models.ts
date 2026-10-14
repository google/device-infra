import {Fleet} from '../../../core/models/search';

/** Input data structure passed into the BulkConfigWifiDialog. */
export interface BulkConfigWifiDialogData {
  deviceIds: string[];
  fleet?: Fleet;
}

/** Result returned when the BulkConfigWifiDialog closes. */
export interface BulkConfigWifiDialogResult {
  updated: boolean;
}
