import {DeviceTarget} from '../../../core/models/device_config_models';
import {Row} from '../../../core/models/search';

/**
 * Extracts a DeviceTarget ({deviceId, hostName}) from a search result Row.
 * Reads hostName from the locked device link cell (DeviceRef.hostName) or
 * from the host link cell (HostRef.hostName) without filtering out unrecognizable device IDs.
 */
export function extractDeviceTarget(
  row: Pick<Row, 'id' | 'cells'>,
): DeviceTarget {
  for (const cell of row.cells || []) {
    const hostName =
      cell.link?.target?.device?.hostName ||
      (cell.link?.target?.host?.hostName !== row.id
        ? cell.link?.target?.host?.hostName
        : undefined);
    if (hostName) {
      return {deviceId: row.id, hostName};
    }
  }
  return {deviceId: row.id};
}

/**
 * Returns a unique composite key (`deviceId@@hostName` or `deviceId`)
 * for either a DeviceTarget/review entry or a search result Row.
 */
export function getDeviceTargetKey(
  item: DeviceTarget | Pick<Row, 'id' | 'cells'>,
): string {
  const target =
    (item as DeviceTarget).deviceId !== undefined
      ? (item as DeviceTarget)
      : extractDeviceTarget(item as Pick<Row, 'id' | 'cells'>);
  return target.hostName
    ? `${target.deviceId}@@${target.hostName}`
    : target.deviceId;
}

/** Formats a device count with locale-aware grouping and pluralization (e.g. '1 device', '1,200 devices'). */
export function formatDeviceCount(count: number): string {
  return `${count.toLocaleString()} ${count === 1 ? 'device' : 'devices'}`;
}

/** Formats badge text indicating how many selected devices currently use a configuration. */
export function formatCurrentSelectionBadge(
  count: number,
  totalSelected: number,
  prefix: 'Current on' | 'Configured on' = 'Current on',
): string | null {
  if (count <= 0) return null;
  if (count === totalSelected && totalSelected > 1) {
    return `${prefix} all ${totalSelected} selected`;
  }
  return `${prefix} ${formatDeviceCount(count)} selected`;
}

/** Formats the informational banner text when `willUpdateCount === 0` in the review step. */
export function formatNothingToUpdateMessage(
  summary: {alreadyMatchCount: number; readOnlyCount: number},
  totalCount: number,
  allMatchPhrase: string,
  targetLabel: string,
): string {
  const {alreadyMatchCount: same, readOnlyCount: skipped} = summary;
  if (same === totalCount) {
    return `All ${formatDeviceCount(totalCount)} ${allMatchPhrase}. No update is needed.`;
  }
  if (same > 0 && skipped > 0) {
    return `${formatDeviceCount(same)} already match this ${targetLabel} and ${formatDeviceCount(skipped)} are read-only. No devices can be updated.`;
  }
  return `All ${formatDeviceCount(totalCount)} are read-only and cannot be updated.`;
}

/** Formats the supporting subtitle text displayed on the post-apply success screen. */
export function formatSuccessSupportText(
  summary: {alreadyMatchCount: number; readOnlyCount: number},
  allUpdatedMessage: string,
): string {
  const {alreadyMatchCount: same, readOnlyCount: skipped} = summary;
  const extraNotes: string[] = [];
  if (same > 0) {
    extraNotes.push(`${formatDeviceCount(same)} already matched`);
  }
  if (skipped > 0) {
    extraNotes.push(`${formatDeviceCount(skipped)} read-only`);
  }
  if (extraNotes.length > 0) {
    return `${extraNotes.join('; ')}.`;
  }
  return allUpdatedMessage;
}
