/**
 * @fileoverview Pure utility functions for bulk device configuration (target extraction, dimension token parsing, and review summaries).
 */

import {
  CandidateDimensionKey,
  CandidateDimensionValue,
  CandidateWifi,
  DeviceConfigChangeStatus,
  DeviceDimensionConfigEntry,
  DeviceTarget,
  DeviceWifi,
} from '../../../core/models/device_config_models';
import {Row} from '../../../core/models/search';
import {BulkConfigReviewSummary} from '../models';

/** Scans a search result row's cells to extract the primary deviceId, hostName, and universe. */
function scanRowLinkTarget(row: Pick<Row, 'id' | 'cells'>): {
  deviceId: string;
  hostName?: string;
  universe?: string;
} {
  const deviceId = row.id ?? '';
  let hostName: string | undefined;
  let universe: string | undefined;
  for (const cell of row.cells || []) {
    const target = cell.link?.target;
    if (!target) continue;
    hostName ??=
      target.device?.hostName ||
      (target.host?.hostName !== deviceId ? target.host?.hostName : undefined);
    universe ??= target.device?.universe || target.host?.universe;
    if (hostName && universe) break;
  }
  return {deviceId, hostName, universe};
}

/**
 * Extracts a DeviceTarget ({deviceId, hostName}) from a search result Row.
 * Reads hostName from the locked device link cell (DeviceRef.hostName) or
 * from the host link cell (HostRef.hostName) without filtering out unrecognizable device IDs.
 */
export function extractDeviceTarget(
  row: Pick<Row, 'id' | 'cells'>,
): DeviceTarget {
  const {deviceId, hostName} = scanRowLinkTarget(row);
  return hostName ? {deviceId, hostName} : {deviceId};
}

/**
 * Returns a unique composite key (`deviceId@@hostName@@universe`,
 * `deviceId@@hostName`, or `deviceId`) for either a DeviceTarget/review entry
 * or a search result Row.
 */
export function getDeviceTargetKey(
  item: DeviceTarget | Pick<Row, 'id' | 'cells'>,
): string {
  const {
    deviceId = '',
    hostName,
    universe,
  } = 'deviceId' in item
    ? {...item, universe: undefined}
    : scanRowLinkTarget(item);
  const baseKey = hostName ? `${deviceId}@@${hostName}` : deviceId;
  return universe ? `${baseKey}@@${universe}` : baseKey;
}

/** Formats a device count with locale-aware grouping and pluralization (e.g. '1 device', '1,200 devices'). */
export function formatDeviceCount(count: number): string {
  return `${count.toLocaleString()} ${count === 1 ? 'device' : 'devices'}`;
}

/** Formats badge text indicating how many selected devices have a dimension configured. */
export function formatCurrentSelectionBadge(
  count: number | undefined,
  totalSelected: number,
): string | null {
  if (!count || count <= 0) return null;
  if (count === totalSelected && totalSelected > 1) {
    return `Configured on all ${totalSelected} selected`;
  }
  return `Configured on ${formatDeviceCount(count)} selected`;
}

/** Resolves the dialog headline across terminal apply states and active step phases. */
export function formatWizardHeadline(
  viewMode: string,
  targetName: string,
  stepHeadline: string,
): string {
  if (viewMode === 'applying') return `Applying ${targetName}`;
  if (viewMode === 'success') return `${targetName} updated`;
  if (viewMode === 'errors') return `Configure ${targetName}`;
  return stepHeadline;
}

/** Resolves the dialog support subtitle across terminal apply states and active step phases. */
export function formatWizardSupportText(
  viewMode: string,
  stepSupportText: string,
): string {
  if (viewMode === 'applying' || viewMode === 'success') return '';
  if (viewMode === 'errors') return 'Apply completed with errors';
  return stepSupportText;
}

/** Formats the informational banner text when `willUpdateCount === 0` in the review step. */
export function formatNothingToUpdateMessage(
  summary: {alreadyMatchCount: number; readOnlyCount: number},
  allMatchPhrase: string,
  targetLabel: string,
): string {
  const {alreadyMatchCount: same, readOnlyCount: skipped} = summary;
  if (same > 0 && skipped === 0) {
    return `All ${formatDeviceCount(same)} ${allMatchPhrase}. No update is needed.`;
  }
  if (same > 0 && skipped > 0) {
    return `${formatDeviceCount(same)} already match this ${targetLabel} and ${formatDeviceCount(skipped)} are read-only. No devices can be updated.`;
  }
  return `All ${formatDeviceCount(skipped)} are read-only and cannot be updated.`;
}

/**
 * Formats the supporting subtitle text displayed on the post-apply success screen
 * when some selected devices were skipped (already matched or read-only).
 * Returns an empty string when all selected devices were updated.
 */
export function formatSuccessSupportText(summary: {
  alreadyMatchCount: number;
  readOnlyCount: number;
}): string {
  const {alreadyMatchCount: same, readOnlyCount: skipped} = summary;
  const extraNotes: string[] = [];
  if (same > 0) {
    extraNotes.push(`${formatDeviceCount(same)} already matched`);
  }
  if (skipped > 0) {
    extraNotes.push(`${formatDeviceCount(skipped)} read-only`);
  }
  return extraNotes.length > 0 ? `${extraNotes.join('; ')}.` : '';
}

const DIMENSION_TOKEN_REGEX =
  /"([^"]*)"?|'([^']*)'?|([^,;\r\n"'\s][^,;\r\n"']*)/g;
const CLOSED_QUOTES_REGEX = /^(?:[^"']+|"[^"]*"|'[^']*')*$/;

/** Extracts trimmed, non-empty dimension value tokens from user input or pasted text. */
export function extractDimensionTokens(raw: string): string[] {
  if (!raw) return [];
  return Array.from(
    raw.matchAll(DIMENSION_TOKEN_REGEX),
    ([, dq, sq, unquoted]) => (dq ?? sq ?? unquoted).trim(),
  ).filter(Boolean);
}

/** Returns true if all single and double quotes in `text` are balanced/closed. */
export function hasClosedQuotes(text: string): boolean {
  return CLOSED_QUOTES_REGEX.test(text);
}

/**
 * Produces a canonical order-insensitive key for a normalized set of dimension
 * values (sorted case-sensitively and joined with `'::'`).
 */
export function canonicalizeValueSet(values: readonly string[]): string {
  return [...values].sort().join('::');
}

const VALID_CUSTOM_DIM_KEY_REGEX = /^[a-z0-9_\-\.]+$/;

/** Validates a custom dimension key against the system blocklist and character rules. */
export function validateCustomDimensionKey(
  cleanKey: string,
  systemDimensionNames: ReadonlySet<string>,
): string {
  if (!cleanKey) return '';
  if (systemDimensionNames.has(cleanKey)) {
    return `"${cleanKey}" is automatically detected by OmniLab and cannot be configured manually.`;
  }
  if (!VALID_CUSTOM_DIM_KEY_REGEX.test(cleanKey)) {
    return 'Dimension key may only contain lowercase letters, numbers, underscores, dashes, and dots.';
  }
  return '';
}

/** Maps device dimension review entries into table rows with value truncation and expansion state. */
export function buildDimensionReviewRows(
  entries: readonly DeviceDimensionConfigEntry[],
  expandedKeys: ReadonlySet<string>,
) {
  return entries.map((entry) => {
    const values = entry.values ?? [];
    const isExpanded = expandedKeys.has(getDeviceTargetKey(entry));
    const hiddenCount = Math.max(0, values.length - 2);
    return {
      ...entry,
      isExpanded,
      visibleValues: isExpanded ? values : values.slice(0, 2),
      hiddenCount,
      hiddenTooltip: isExpanded ? '' : values.slice(2).join(', '),
    };
  });
}

/** Filters and enriches candidate dimension keys with fleet count text and selection badges. */
export function filterCandidateDimensionKeys(
  candidates: readonly CandidateDimensionKey[],
  query: string,
  totalSelected: number,
) {
  const matched = query
    ? candidates.filter((c) => c.dimensionName.includes(query))
    : candidates;
  return matched.slice(0, 50).map((c) => ({
    ...c,
    fleetSupportText: `${formatDeviceCount(c.fleetDeviceCount ?? 0)} in fleet`,
    badge: formatCurrentSelectionBadge(c.selectedDeviceCount, totalSelected),
    isActive: c.dimensionName === query,
  }));
}

/** Enriches candidate dimension value suggestions with canonical keys and active state. */
export function buildDimensionSuggestionRows(
  candidates: readonly CandidateDimensionValue[],
  isClearDimension: boolean,
  targetValues: readonly string[],
) {
  const targetCanon = isClearDimension
    ? null
    : canonicalizeValueSet(targetValues);
  return candidates.map((sug) => {
    const canonKey = canonicalizeValueSet(sug.values);
    return {
      ...sug,
      fleetSupportText: `${formatDeviceCount(sug.deviceCount ?? 0)} in fleet`,
      canonKey,
      isActive: canonKey === targetCanon,
    };
  });
}

/** Filters candidate Wi-Fi networks and enriches them with subtitle and active selection state. */
export function filterCandidateWifiOptions(
  list: readonly CandidateWifi[],
  rawQuery: string,
  isClear: boolean,
  target: DeviceWifi,
) {
  const q = rawQuery.trim().toLowerCase();
  const targetSsid = target.ssid.trim();
  const matched = q
    ? list.filter((c) => c.wifi.ssid.toLowerCase().includes(q))
    : list;
  return matched.map((c) => ({
    wifi: c.wifi,
    subtitle:
      c.fleetInUse?.deviceCount != null
        ? `${formatDeviceCount(c.fleetInUse.deviceCount)} in fleet`
        : c.curatedPreset
          ? 'Preset network'
          : '',
    isActive:
      !isClear &&
      c.wifi.ssid === targetSsid &&
      (c.wifi.psk || '') === target.psk &&
      Boolean(c.wifi.scanSsid) === target.scanSsid,
  }));
}

/** Computes a single-pass review summary consolidating device status counts and labels. */
export function computeBulkReviewSummary(
  entries: ReadonlyArray<{status?: DeviceConfigChangeStatus}>,
): BulkConfigReviewSummary {
  let willUpdateCount = 0;
  let alreadyMatchCount = 0;
  let readOnlyCount = 0;

  for (const entry of entries) {
    switch (entry.status) {
      case DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE:
        willUpdateCount++;
        break;
      case DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES:
        alreadyMatchCount++;
        break;
      case DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY:
        readOnlyCount++;
        break;
      default:
        break;
    }
  }

  return {
    willUpdateCount,
    willUpdateLabel: formatDeviceCount(willUpdateCount),
    alreadyMatchCount,
    alreadyMatchLabel: formatDeviceCount(alreadyMatchCount),
    readOnlyCount,
    readOnlyLabel: formatDeviceCount(readOnlyCount),
  };
}

/** Extracts DeviceTarget items marked as WILL_UPDATE from review entries. */
export function extractWillUpdateTargets(
  entries: ReadonlyArray<{
    deviceId: string;
    hostName?: string;
    status?: DeviceConfigChangeStatus;
  }>,
): DeviceTarget[] {
  const updateTargets: DeviceTarget[] = [];
  for (const r of entries) {
    if (
      r.status ===
      DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE
    ) {
      updateTargets.push(
        r.hostName
          ? {deviceId: r.deviceId, hostName: r.hostName}
          : {deviceId: r.deviceId},
      );
    }
  }
  return updateTargets;
}
