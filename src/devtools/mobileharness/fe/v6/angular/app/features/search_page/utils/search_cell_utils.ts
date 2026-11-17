/**
 * @fileoverview Pure helpers for table cell variant classification, row selection state, pagination range formatting, and group utilization metrics.
 */

import {
  Cell,
  Column,
  FleetGroup,
  Indicator,
  Row,
} from '../../../core/models/search';
import {CellKind, SelectableRowItem, UtilizationViewModel} from '../models';
import {getDeviceTargetKey} from './bulk_config_utils';

/** Identifies the cell variant conforming to protobuf Cell oneof kind. */
export function computeCellKind(c?: Cell | null): CellKind {
  if (!c) return 'empty';
  if (c.link) return 'link';
  if (c.status) return 'status';
  if (c.chips?.values && c.chips.values.length > 0) return 'chips';
  if (c.multiLink?.entries && c.multiLink.entries.length > 0) {
    return 'multiLink';
  }
  if (c.text?.value) return 'text';
  return 'empty';
}

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

/** Maps search result rows into selectable row items with pre-computed composite keys. */
export function toSelectableRows(
  rows: readonly Row[],
  selected: ReadonlySet<string> | ReadonlyMap<string, unknown>,
): SelectableRowItem[] {
  return rows.map((row) => {
    const key = getDeviceTargetKey(row);
    return {row, key, selected: selected.has(key)};
  });
}

/** Computes structured utilization progress and legend metrics from a group's utilization proto. */
export function buildUtilizationViewModel(
  util?: FleetGroup['utilization'],
): UtilizationViewModel | null {
  const total = util?.total || 0;
  if (!util || total <= 0) return null;

  const toMetric = (v = 0) => {
    const width = Math.min(100, Math.max(0, (100 * v) / total));
    return {count: v, pct: Math.round(width), width};
  };

  const busy = toMetric(util.busy);
  const idle = toMetric(util.idle);
  const other = toMetric(util.other);

  return {
    busyCount: busy.count,
    busyPct: busy.pct,
    busyWidth: busy.width,
    idleCount: idle.count,
    idlePct: idle.pct,
    idleWidth: idle.width,
    otherCount: other.count,
    otherPct: other.pct,
    otherWidth: other.width,
  };
}

/** Resolves effective columns for an expanded group's inner table with pre-merged header descriptions. */
export function resolveGroupInnerColumns(
  rawCols: readonly Column[] | undefined,
  displayCols: readonly Column[],
  descriptions: ReadonlyMap<string, string>,
): Column[] {
  const cols = rawCols && rawCols.length > 0 ? rawCols : displayCols;
  return cols.map((col) => {
    const description = col.description || descriptions.get(col.key);
    return description ? {...col, description} : col;
  });
}
