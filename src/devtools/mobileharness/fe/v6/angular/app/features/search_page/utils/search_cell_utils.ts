import {
  Cell,
  Column,
  LinkCell,
  LinkEntry,
  NavTarget,
  Row,
} from '../../../core/models/search';
import {dateUtils} from '../../../shared/utils/date_utils';

/** Pre-compiled regex for identifying time/date column keys. */
const TIME_COLUMN_KEY_REGEX =
  /time|date|timestamp|created|updated|start_time|end_time|last_seen/i;

/** Helper to check if a column key represents a timestamp/date column. */
export function isTimeColumn(colKey: string | undefined): boolean {
  return !!colKey && TIME_COLUMN_KEY_REGEX.test(colKey);
}

/** Constant sets for O(1) status class evaluation to prevent GC allocations per cell render. */
const STATUS_OK_SET = new Set([
  'PASS',
  'PASSED',
  'DONE',
  'FINISHED',
  'SUCCEEDED',
  'COMPLETED',
  'HEALTHY',
  'IDLE',
  'READY',
]);
const STATUS_ACTIVE_SET = new Set(['RUNNING', 'ACTIVE']);
const STATUS_ERROR_SET = new Set([
  'FAIL',
  'ERROR',
  'FAILED',
  'ABORT',
  'TIMEOUT',
  'CANCELLED',
  'EXPIRED',
  'BUSY',
  'OFFLINE',
  'DRAINING',
]);

/** Mapping of protobuf Indicator enum values to CSS status classes. */
const INDICATOR_CLASS_MAP: Record<string | number, string> = {
  'INDICATOR_GOOD': 'status-ok',
  'INDICATOR_OK': 'status-ok',
  1: 'status-ok',

  'INDICATOR_ACTIVE': 'status-active',
  2: 'status-active',

  'INDICATOR_ERROR': 'status-error',
  3: 'status-error',
};

/** Resolves the Cell element for a column from a Row. */
export function getCell(
  row: Row | Record<string, unknown> | undefined,
  colKey: string,
  columns: Column[],
): Cell | null {
  if (!row) return null;
  const cells =
    (row as Row).cells || ((row as Record<string, unknown>)['cells'] as Cell[]);
  if (!cells || !Array.isArray(cells)) return null;
  const idx = columns.findIndex((c) => c.key === colKey);
  return idx === -1 ? null : cells[idx];
}

/** Extracts the string value representation of a Cell (strictly matching the 5 Proto Cell oneof fields). */
export function getTextValue(cell: Cell | undefined): string | null {
  if (!cell) return null;

  if (typeof cell.text === 'string') return cell.text || null;
  if (typeof cell.text === 'object' && cell.text?.value != null) {
    return cell.text.value;
  }
  if (cell.link?.text != null) return cell.link.text;
  if (cell.status?.text != null) return cell.status.text;
  if (cell.chips?.values?.length) return cell.chips.values.join(', ');
  if (cell.multiLink?.entries?.length) {
    return cell.multiLink.entries
      .map((e) => e.text)
      .filter(Boolean)
      .join(', ');
  }
  return null;
}

/** Determines the semantic type classification for a Cell (strictly matching the 5 Proto oneof kinds in search_common.proto). */
export function getCellType(
  cell: Cell | undefined,
): 'text' | 'link' | 'status' | 'chips' | 'multilink' | 'unknown' {
  if (!cell) return 'unknown';

  if (cell.link) return 'link';
  if (cell.status) return 'status';
  if (cell.chips) return 'chips';
  if (cell.multiLink) return 'multilink';
  if (cell.text) return 'text';

  return 'unknown';
}

/** Constructs RouterLink path segments for LinkCell, LinkEntry, or NavTarget (100% aligns with search_common.proto). */
export function getRouterLink(
  item: LinkCell | LinkEntry | NavTarget | undefined,
): string[] | null {
  if (!item) return null;

  const target: NavTarget | undefined =
    (item as LinkCell | LinkEntry).target || (item as NavTarget);

  if (!target) return null;

  if (target.device?.id) return ['/devices', target.device.id];
  if (target.host?.hostName) return ['/hosts', target.host.hostName];
  if (target.job?.jobId) return ['/jobs', target.job.jobId];
  if (target.session?.sessionId) return ['/sessions', target.session.sessionId];
  if (target.test?.testId) {
    const jobId = target.test.jobId || 'unknown_job';
    return ['/jobs', jobId, 'tests', target.test.testId];
  }

  return null;
}

/** Computes the CSS status class name for cell status indicators (100% aligns with search_common.proto StatusCell). */
export function getStatusClass(cell: Cell | undefined): string {
  if (!cell) return 'status-neutral';

  const indicator = cell.status?.indicator;
  if (indicator != null) {
    const indStr = String(indicator);
    if (indStr !== 'INDICATOR_UNSPECIFIED' && indStr !== '0') {
      return (
        INDICATOR_CLASS_MAP[indicator as string | number] || 'status-neutral'
      );
    }
  }

  const rawStatus = cell.status?.text || '';
  const txt = String(rawStatus).toUpperCase();

  if (STATUS_ACTIVE_SET.has(txt)) return 'status-active';
  if (STATUS_OK_SET.has(txt)) return 'status-ok';
  if (STATUS_ERROR_SET.has(txt)) return 'status-error';
  return 'status-neutral';
}

/** Formats a UTC timestamp string into a local detailed date-time string with timezone. */
export function formatTime(val: string | undefined | null): string | null {
  if (!val) return null;

  const date = dateUtils.parseUtcTimestamp(val);
  if (!date || isNaN(date.getTime())) return val;
  return dateUtils.formatDetailedLocal(date);
}

/**
 * Extracts a unique string identifier for a search result row.
 * Filters out default Protobuf all-zero placeholder UUIDs ('000000000000') and
 * falls back to primary cell text/link metadata to ensure rows are uniquely identifiable.
 */
export function getRowId(row: Row | undefined): string {
  if (!row) return '';
  const rawId = row.id || '';
  // Ignore default unassigned Protobuf placeholder UUIDs to prevent ID collisions in selection Set
  if (
    rawId &&
    rawId !== '000000000000' &&
    rawId !== '00000000-0000-0000-0000-000000000000'
  ) {
    return rawId;
  }

  // Fall back to primary cell metadata (device ID or host name) if rawId is a dummy placeholder
  const c0 = row.cells?.[0];
  if (c0) {
    const link = c0.link;
    const target = link?.target;
    const cellText =
      target?.device?.id ||
      target?.host?.hostName ||
      link?.text ||
      (typeof c0.text === 'string' ? c0.text : c0.text?.value);
    if (cellText) return String(cellText);
  }
  return rawId;
}

