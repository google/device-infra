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
  const deviceId = row.id ?? '';
  for (const cell of row.cells || []) {
    const hostName =
      cell.link?.target?.device?.hostName ||
      (cell.link?.target?.host?.hostName !== deviceId
        ? cell.link?.target?.host?.hostName
        : undefined);
    if (hostName) {
      return {deviceId, hostName};
    }
  }
  return {deviceId};
}

/**
 * Returns a unique composite key (`deviceId@@hostName` or `deviceId`)
 * for either a DeviceTarget/review entry or a search result Row.
 */
export function getDeviceTargetKey(
  item: DeviceTarget | Pick<Row, 'id' | 'cells'>,
): string {
  const target: DeviceTarget =
    'id' in item || 'cells' in item
      ? extractDeviceTarget(item as Pick<Row, 'id' | 'cells'>)
      : {
          ...(item as DeviceTarget),
          deviceId: (item as DeviceTarget).deviceId ?? '',
        };
  return target.hostName
    ? `${target.deviceId}@@${target.hostName}`
    : target.deviceId;
}

/** Formats an entity count with locale-aware grouping and pluralization (e.g. '1 device', '3 hosts'). */
export function formatEntityCount(
  count: number,
  noun: 'device' | 'host' = 'device',
): string {
  const plural = noun === 'host' ? 'hosts' : 'devices';
  return `${count.toLocaleString()} ${count === 1 ? noun : plural}`;
}

/** Formats a device count with locale-aware grouping and pluralization (e.g. '1 device', '1,200 devices'). */
export function formatDeviceCount(count: number): string {
  return formatEntityCount(count, 'device');
}

/** Formats a host count with locale-aware grouping and pluralization (e.g. '1 host', '12 hosts'). */
export function formatHostCount(count: number): string {
  return formatEntityCount(count, 'host');
}

/** Formats badge text indicating how many selected entities currently use a configuration. */
export function formatCurrentSelectionBadge(
  count: number | undefined,
  totalSelected: number,
  prefix: 'Current on' | 'Configured on' = 'Current on',
  noun: 'device' | 'host' = 'device',
): string | null {
  if (!count || count <= 0) return null;
  if (count === totalSelected && totalSelected > 1) {
    return `${prefix} all ${totalSelected} selected`;
  }
  return `${prefix} ${formatEntityCount(count, noun)} selected`;
}

/** Formats the informational banner text when `willUpdateCount === 0` in the review step. */
export function formatNothingToUpdateMessage(
  summary: {alreadyMatchCount: number; readOnlyCount: number},
  totalCount: number,
  allMatchPhrase: string,
  targetLabel: string,
  noun: 'device' | 'host' = 'device',
): string {
  const {alreadyMatchCount: same, readOnlyCount: skipped} = summary;
  const plural = noun === 'host' ? 'hosts' : 'devices';
  if (same === totalCount) {
    return `All ${formatEntityCount(totalCount, noun)} ${allMatchPhrase}. No update is needed.`;
  }
  if (same > 0 && skipped > 0) {
    return `${formatEntityCount(same, noun)} already match this ${targetLabel} and ${formatEntityCount(skipped, noun)} are read-only. No ${plural} can be updated.`;
  }
  return `All ${formatEntityCount(totalCount, noun)} are read-only and cannot be updated.`;
}

/** Formats the supporting subtitle text displayed on the post-apply success screen. */
export function formatSuccessSupportText(
  summary: {alreadyMatchCount: number; readOnlyCount: number},
  allUpdatedMessage: string,
  noun: 'device' | 'host' = 'device',
): string {
  const {alreadyMatchCount: same, readOnlyCount: skipped} = summary;
  const extraNotes: string[] = [];
  if (same > 0) {
    extraNotes.push(`${formatEntityCount(same, noun)} already matched`);
  }
  if (skipped > 0) {
    extraNotes.push(`${formatEntityCount(skipped, noun)} read-only`);
  }
  if (extraNotes.length > 0) {
    return `${extraNotes.join('; ')}.`;
  }
  return allUpdatedMessage;
}
