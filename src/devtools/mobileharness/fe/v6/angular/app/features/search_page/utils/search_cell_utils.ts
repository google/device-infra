import {Indicator, Row} from '../../../core/models/search';
import {getDeviceTargetKey} from './bulk_config_utils';

/** Mapping of protobuf Indicator enum values to CSS status classes. */
const INDICATOR_CLASS_MAP: Record<string, string> = {
  [Indicator.INDICATOR_OK]: 'status-ok',
  [Indicator.INDICATOR_ACTIVE]: 'status-active',
  [Indicator.INDICATOR_ERROR]: 'status-error',
  [Indicator.INDICATOR_NEUTRAL]: 'status-neutral',
};

/** Computes the CSS status class name directly from protobuf Indicator. */
export function getStatusClass(indicator?: Indicator | string): string {
  return (indicator && INDICATOR_CLASS_MAP[indicator]) || 'status-neutral';
}

/** Returns true if a search result Row is present in the selected collection. */
export function isRowSelected(
  row: {id: string; cells?: Row['cells']},
  selected: ReadonlySet<string> | ReadonlyMap<string, unknown>,
): boolean {
  return selected.has(getDeviceTargetKey(row));
}

/** Computes tri-state checkbox selection state ({all, some}) for a page of rows. */
export function getRowsSelectionState(
  rows: ReadonlyArray<{id: string; cells?: Row['cells']}>,
  selected: ReadonlySet<string> | ReadonlyMap<string, unknown>,
): {all: boolean; some: boolean} {
  if (rows.length === 0) return {all: false, some: false};
  let count = 0;
  for (const r of rows) {
    if (isRowSelected(r, selected)) count++;
  }
  return {
    all: count === rows.length,
    some: count > 0 && count < rows.length,
  };
}

/** Formats pagination range text (e.g. "1–25 of 1,250" or "1–25 of 142 groups"). */
export function formatPaginationRange(
  start?: number,
  end?: number,
  total?: number,
  suffix = '',
): string {
  if (!start || !end || !total) return '';
  return `${start.toLocaleString()}–${end.toLocaleString()} of ${total.toLocaleString()}${suffix}`;
}
