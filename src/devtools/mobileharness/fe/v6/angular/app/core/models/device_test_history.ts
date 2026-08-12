/**
 * @fileoverview Models for the Device Detail "Test history" tab.
 *
 * These mirror the `search` result-table proto (Column / Row / Cell) returned by
 * DeviceService.GetDeviceTestHistory as proto JSON. The table is generic: the
 * backend decides the columns and fills each row with typed cells parallel to
 * those columns, so the frontend renders via the cell kind without per-column
 * logic. This is the same table the test search page renders.
 */

/** A column header. Row cells are parallel to the columns array. */
export declare interface TableColumn {
  key?: string;
  displayName?: string;
}

/** Navigation identity for a test. */
export declare interface TestRef {
  testId?: string;
  jobId?: string;
}

/** Navigation identity for a device. */
export declare interface DeviceRef {
  id?: string;
  hostName?: string;
  hostIp?: string;
}

/** Navigation identity for a host. */
export declare interface HostRef {
  hostName?: string;
  hostIp?: string;
}

/** Navigation identity for a job. */
export declare interface JobRef {
  jobId?: string;
}

/** Navigation identity for a session. */
export declare interface SessionRef {
  sessionId?: string;
}

/** Semantic navigation target; exactly one field is set. */
export declare interface NavTarget {
  device?: DeviceRef;
  host?: HostRef;
  test?: TestRef;
  job?: JobRef;
  session?: SessionRef;
}

/** Plain text cell. */
export declare interface TextCell {
  value?: string;
}

/** A clickable text cell that navigates to another page. */
export declare interface LinkCell {
  text?: string;
  target?: NavTarget;
}

/**
 * Semantic indicator for a status/result cell. The frontend maps it to a
 * color/icon; it is not a severity ranking.
 */
export type Indicator =
  | 'INDICATOR_UNSPECIFIED'
  | 'INDICATOR_OK'
  | 'INDICATOR_ACTIVE'
  | 'INDICATOR_ERROR'
  | 'INDICATOR_NEUTRAL';

/** A status/result cell with a display label and a semantic indicator. */
export declare interface StatusCell {
  text?: string;
  indicator?: Indicator;
}

/** Multi-value plain-text chip cell. */
export declare interface ChipsCell {
  values?: string[];
}

/** A single entry in a multi-link cell. */
export declare interface LinkEntry {
  text?: string;
  target?: NavTarget;
}

/** A cell with multiple clickable entries. */
export declare interface MultiLinkCell {
  entries?: LinkEntry[];
}

/** A typed, self-describing table cell; exactly one field is set. */
export declare interface TableCell {
  text?: TextCell;
  link?: LinkCell;
  status?: StatusCell;
  chips?: ChipsCell;
  multiLink?: MultiLinkCell;
}

/** A single row; cells are parallel to the response columns. */
export declare interface TableRow {
  id?: string;
  cells?: TableCell[];
}

/** Response for GetDeviceTestHistory: a generic result table plus a cursor. */
export declare interface DeviceTestHistoryResponse {
  columns?: TableColumn[];
  rows?: TableRow[];
  /** Cursor for the next page; empty/absent when this is the last page. */
  nextPageToken?: string;
}
