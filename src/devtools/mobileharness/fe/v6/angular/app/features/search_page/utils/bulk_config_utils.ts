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
  const {deviceId = '', hostName} =
    'deviceId' in item ? item : extractDeviceTarget(item);
  return hostName ? `${deviceId}@@${hostName}` : deviceId;
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
