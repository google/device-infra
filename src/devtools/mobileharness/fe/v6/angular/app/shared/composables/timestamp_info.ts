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
