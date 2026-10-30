import {Signal, computed} from '@angular/core';
import {ExecutionDetails} from '../../core/models/common_models';
import {dateUtils} from '../utils/date_utils';

/** Representation of a timestamp key and its human-readable label. */
export interface TimestampKeyItem {
  key: 'createTime' | 'startTime' | 'endTime' | 'updateTime';
  label: string;
}

/** Formatted timestamp metadata used for UI display. */
export interface TimestampInfo {
  rawValue: string;
  displayValue: string;
  durationText: string;
  localStr: string;
  utcStr: string;
  elapsedHtml: string;
}

/** Standard set of timestamp key definitions for timeline displays. */
export const STANDARD_TIMESTAMP_KEYS: readonly TimestampKeyItem[] = [
  {key: 'createTime', label: 'Create Time'},
  {key: 'startTime', label: 'Start Time'},
  {key: 'endTime', label: 'End Time'},
  {key: 'updateTime', label: 'Last Update Time'},
] as const;

/**
 * Calculates detailed timestamp information for a single timestamp key.
 */
export function getSingleTimestampInfo(
  key: 'createTime' | 'startTime' | 'endTime' | 'updateTime',
  executionDetails?: ExecutionDetails | null,
  baseCreateDate: Date | null = null,
): TimestampInfo {
  let rawValue: string | undefined;
  if (executionDetails) {
    switch (key) {
      case 'createTime':
        rawValue = executionDetails.createTime;
        break;
      case 'startTime':
        rawValue = executionDetails.startTime;
        break;
      case 'endTime':
        rawValue = executionDetails.endTime;
        break;
      case 'updateTime':
        rawValue = executionDetails.updateTime;
        break;
      default:
        rawValue = undefined;
    }
  }

  const date = rawValue ? dateUtils.parseUtcTimestamp(rawValue) : null;
  const isValid = date && !isNaN(date.getTime());

  if (!rawValue || !isValid) {
    return {
      rawValue: rawValue ?? '',
      displayValue: rawValue ?? 'N/A',
      durationText: '',
      localStr: '',
      utcStr: '',
      elapsedHtml: '',
    };
  }

  const createDate =
    baseCreateDate ??
    (executionDetails?.createTime
      ? dateUtils.parseUtcTimestamp(executionDetails.createTime)
      : null);

  const elapsed =
    key === 'createTime'
      ? {durationText: '(base)', elapsedHtml: ''}
      : dateUtils.getElapsedTimeText(date, createDate, 'Create Time');

  return {
    rawValue,
    displayValue: dateUtils.formatPdt(date),
    durationText: elapsed.durationText,
    localStr: dateUtils.formatDetailedLocal(date),
    utcStr: dateUtils.formatDetailedUtc(date),
    elapsedHtml: elapsed.elapsedHtml,
  };
}

/**
 * Creates a Signal mapping timestamp keys to their detailed TimestampInfo.
 */
export function createTimestampInfoMap(
  executionDetailsSignal: Signal<ExecutionDetails | null | undefined>,
  keys: readonly TimestampKeyItem[] = STANDARD_TIMESTAMP_KEYS,
): Signal<Record<string, TimestampInfo>> {
  return computed(() => {
    const details = executionDetailsSignal();
    const createVal = details?.createTime;
    const baseCreateDate = createVal
      ? dateUtils.parseUtcTimestamp(createVal)
      : null;
    const result: Record<string, TimestampInfo> = {};
    for (const item of keys) {
      result[item.key] = getSingleTimestampInfo(
        item.key,
        details,
        baseCreateDate,
      );
    }
    return result;
  });
}

/**
 * Formats the elapsed duration between two UTC timestamp strings (e.g. '2m 5s'),
 * or returns 'N/A' when either timestamp is missing, unparseable, or negative.
 */
export function formatExecutionDuration(
  startTime?: string,
  endTime?: string,
): string {
  if (!startTime || !endTime) return 'N/A';
  const start = dateUtils.parseUtcTimestamp(startTime);
  const end = dateUtils.parseUtcTimestamp(endTime);
  let diffSec = -1;
  if (!start || !end || isNaN(start.getTime()) || isNaN(end.getTime())) {
    const startIso = new Date(startTime);
    const endIso = new Date(endTime);
    if (isNaN(startIso.getTime()) || isNaN(endIso.getTime())) return 'N/A';
    diffSec = Math.round((endIso.getTime() - startIso.getTime()) / 1000);
  } else {
    diffSec = Math.round((end.getTime() - start.getTime()) / 1000);
  }

  if (diffSec < 0) return 'N/A';
  if (diffSec < 60) return `${diffSec}s`;
  if (diffSec < 3600) {
    const m = Math.floor(diffSec / 60);
    const s = diffSec % 60;
    return `${m}m ${s}s`;
  }
  const h = Math.floor(diffSec / 3600);
  const m = Math.floor((diffSec % 3600) / 60);
  const s = diffSec % 60;
  return `${h}h ${m}m ${s}s`;
}

/**
 * Formats a test start time string in detailed local time, falling back to the
 * raw string if parsing fails or 'N/A' if empty.
 */
export function formatExecutionStartTime(startTime?: string): string {
  if (!startTime) return 'N/A';
  const date = dateUtils.parseUtcTimestamp(startTime);
  if (!date || isNaN(date.getTime())) {
    const fallback = new Date(startTime);
    if (!isNaN(fallback.getTime())) {
      return dateUtils.formatDetailedLocal(fallback);
    }
    return startTime;
  }
  return dateUtils.formatDetailedLocal(date);
}
