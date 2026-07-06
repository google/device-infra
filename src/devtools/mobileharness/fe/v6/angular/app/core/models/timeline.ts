/**
 * @fileoverview Defines interfaces for Timeline / Timing breakdown data,
 * structured for presentation in the UI, corresponding to test/job execution.
 */

/** Represents a single mark point inside a stage (e.g. checkpoint). */
export interface TimelineStageMarkPoint {
  readonly name: string;
  readonly time: string;
}

/** Represents a logical stage of execution (e.g. Allocation, Run). */
export interface TimelineStage {
  /** The structured name of the stage (e.g., "Device Allocation", "Pre-run Test"). */
  readonly name: string;
  /** Optional tag for extra context (e.g., the test ID being allocated, or file path). */
  readonly tag?: string;
  /** Start timestamp of this specific phase. */
  readonly startTime: string;
  /** End timestamp of this specific phase. */
  readonly endTime?: string;
  /** Optional progress checkpoints inside this stage. */
  readonly markPoints?: TimelineStageMarkPoint[];
}

/** The timing breakdown of a test or job. */
export interface TimeBreakdown {
  readonly createTime: string;
  readonly startTime?: string;
  readonly endTime?: string;
  readonly totalDuration?: string;
  readonly stages?: TimelineStage[];
}
