import {
  DeviceConfigChangeStatus,
  UpdateError,
} from '../../../../../core/models/device_config_models';
import {BulkApplyError, BulkConfigReviewSummary} from '../../../models';

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

/** Single-pass aggregation of device review entries by backend-evaluated change status. */
export function summarizeConfigEntries<
  T extends {status?: DeviceConfigChangeStatus},
>(entries: readonly T[]): BulkConfigReviewSummary<T> {
  let alreadyMatchCount = 0;
  let readOnlyCount = 0;
  const willUpdateRows: T[] = [];

  for (const entry of entries) {
    switch (entry.status) {
      case DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE:
        willUpdateRows.push(entry);
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
    willUpdateRows,
    willUpdateCount: willUpdateRows.length,
    alreadyMatchCount,
    readOnlyCount,
  };
}

/** Maps a backend batch update error map into an array of per-device error entries. */
export function extractBatchApplyErrors(
  errorsMap: Record<string, UpdateError> | undefined,
): BulkApplyError[] {
  return Object.entries(errorsMap || {}).map(([deviceId, err]) => ({
    deviceId,
    reason: err.message || err.code,
  }));
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

/**
 * Quote-aware token parser for dimension values:
 * - Preserves commas, semicolons, spaces, and newlines inside quotes (`'...'` or `"..."`).
 * - Commas, semicolons, and newlines outside quotes act as delimiters.
 * - Enclosing quotes are stripped from the resulting token.
 */
export function parseDimensionTokens(raw: string): string[] {
  if (!raw) return [];
  const tokens: string[] = [];
  let cur = '';
  let inQuote: string | null = null;

  for (const ch of raw) {
    if (inQuote) {
      if (ch === inQuote) {
        inQuote = null;
      } else {
        cur += ch;
      }
    } else if (ch === '"' || ch === "'") {
      inQuote = ch;
    } else if (ch === ',' || ch === ';' || ch === '\n' || ch === '\r') {
      const trimmed = cur.trim();
      if (trimmed) tokens.push(trimmed);
      cur = '';
    } else {
      cur += ch;
    }
  }

  const last = cur.trim();
  if (last) tokens.push(last);
  return tokens;
}
