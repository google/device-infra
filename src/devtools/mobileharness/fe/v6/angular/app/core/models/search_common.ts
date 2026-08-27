/**
 * @fileoverview Common data models 100% strictly matched with search_common.proto.
 */

/** Structured text segment for rich suggestion rendering. */
export declare interface TextSegment {
  text: string;
  emphasized?: boolean;
}

/** Semantic indicator for status/result cells.
 *  OK      → green  (healthy/success: IDLE, PASS)
 *  ACTIVE  → blue   (in-progress: RUNNING, BUSY)
 *  ERROR   → red    (failure: FAILED, MISSING, ERROR)
 *  NEUTRAL → gray   (no judgment: NEW, PREPPING, SUSPENDED)
 */
export enum Indicator {
  INDICATOR_UNSPECIFIED = 'INDICATOR_UNSPECIFIED',
  INDICATOR_OK = 'INDICATOR_OK',
  INDICATOR_ACTIVE = 'INDICATOR_ACTIVE',
  INDICATOR_ERROR = 'INDICATOR_ERROR',
  INDICATOR_NEUTRAL = 'INDICATOR_NEUTRAL',
}

/** Column header in search results. */
export declare interface Column {
  key: string;
  displayName: string;
}

/** A single row in a search results table. */
export declare interface Row {
  id: string;
  cells?: Cell[];
}

/** A typed, self-describing table cell. */
export declare interface Cell {
  text?: TextCell;
  link?: LinkCell;
  status?: StatusCell;
  chips?: ChipsCell;
  multiLink?: MultiLinkCell;
}

/** Plain text cell. */
export declare interface TextCell {
  value: string;
}

/** A clickable text cell that navigates to another page. */
export declare interface LinkCell {
  text?: string;
  target?: NavTarget;
}

/** A status/result cell with a semantic indicator. */
export declare interface StatusCell {
  text: string;
  indicator?: Indicator;
}

/** Multi-value chip cell. */
export declare interface ChipsCell {
  values?: string[];
}

/** A multi-link cell: multiple clickable entries in one cell. */
export declare interface MultiLinkCell {
  entries?: LinkEntry[];
}

/** A single entry in a MultiLinkCell. */
export declare interface LinkEntry {
  text: string;
  target?: NavTarget;
}

/** Semantic navigation identity for a link. */
export declare interface NavTarget {
  device?: DeviceRef;
  host?: HostRef;
  test?: TestRef;
  job?: JobRef;
  session?: SessionRef;
}

/** Navigation identity for a device. */
export declare interface DeviceRef {
  id: string;
  hostName?: string;
  hostIp?: string;
}

/** Navigation identity for a host. */
export declare interface HostRef {
  hostName: string;
  hostIp?: string;
}

/** Navigation identity for a test. */
export declare interface TestRef {
  testId: string;
  jobId?: string;
}

/** Navigation identity for a job. */
export declare interface JobRef {
  jobId: string;
}

/** Navigation identity for a session. */
export declare interface SessionRef {
  sessionId: string;
}

/** Key identity descriptor. */
export declare interface KeyDescriptor {
  key: string;
  displayName: string;
}
