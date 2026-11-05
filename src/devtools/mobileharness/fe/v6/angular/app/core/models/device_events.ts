/**
 * @fileoverview Models for the Device Detail "Device Events" tab.
 *
 * Mirrors the proto JSON messages returned by DeviceService.GetDeviceEvents.
 */

/** A single field transition captured from device monitoring snapshots. */
export declare interface DeviceChangeRecord {
  changeTime?: string;
  field?: string;
  fromValue?: string;
  toValue?: string;
  meta?: string;
}

/** A manual repair record logged by a lab technician in Arsenal. */
export declare interface DeviceRecoveryRecord {
  createTime?: string;
  author?: string;
  recoveryAction?: string;
  offlineReason?: string;
  note?: string;
}

/** An asset lifecycle or location record from the inventory system. */
export declare interface DeviceInventoryRecord {
  eventTime?: string;
  author?: string;
  subject?: string;
  body?: string;
}

/** Response for GetDeviceEvents. */
export declare interface DeviceEventsResponse {
  changes?: DeviceChangeRecord[];
  recoveryRecords?: DeviceRecoveryRecord[];
  inventoryRecords?: DeviceInventoryRecord[];
  hasOlderEvents?: boolean;
}
