/**
 * @fileoverview Interfaces for the BFF TimeBreakdown contract rendered on the
 * Test Detail and Job Detail Timeline tabs.
 */

/** Visual fill token shared by split bar segments, swatches, and track bars. */
export type TimelineFill =
  | 'TIMELINE_FILL_UNSPECIFIED'
  | 'TIMELINE_FILL_WAIT'
  | 'TIMELINE_FILL_QUEUED'
  | 'TIMELINE_FILL_SETUP'
  | 'TIMELINE_FILL_RESOLVE'
  | 'TIMELINE_FILL_TEST'
  | 'TIMELINE_FILL_TEARDOWN'
  | 'TIMELINE_FILL_OPAQUE'
  | 'TIMELINE_FILL_UNRECORDED'
  | 'TIMELINE_FILL_UNKNOWN'
  | 'TIMELINE_FILL_MISSING';

/** Horizontal alignment of an axis tick label near the track edges. */
export type TimelineTickAlignment =
  | 'TIMELINE_TICK_ALIGNMENT_UNSPECIFIED'
  | 'TIMELINE_TICK_ALIGNMENT_CENTER'
  | 'TIMELINE_TICK_ALIGNMENT_START'
  | 'TIMELINE_TICK_ALIGNMENT_END';

/** Outcome tone for result chips and end markers. */
export type TimelineResultTone =
  | 'TIMELINE_RESULT_TONE_UNSPECIFIED'
  | 'TIMELINE_RESULT_TONE_PASS'
  | 'TIMELINE_RESULT_TONE_FAIL'
  | 'TIMELINE_RESULT_TONE_TIMEOUT'
  | 'TIMELINE_RESULT_TONE_NEUTRAL';

/** One tick mark on the shared relative time axis. */
export declare interface TimelineAxisTick {
  readonly leftPercent?: number;
  readonly label?: string;
  readonly alignment?: TimelineTickAlignment;
}

/** Shared relative time axis for all rows in a timeline table. */
export declare interface TimelineAxis {
  readonly tickStepPercent?: number;
  readonly ticks?: TimelineAxisTick[];
}

/** Key/value fact row shown inside a timeline hover tooltip. */
export declare interface TimelineTooltipFact {
  readonly label?: string;
  readonly value?: string;
}

/** One proportional segment of the top summary split bar and its legend entry. */
export declare interface TimelineSplitSegment {
  readonly focusKey?: string;
  readonly label?: string;
  readonly fill?: TimelineFill;
  readonly flexWeight?: number;
  readonly durationText?: string;
  readonly shareText?: string;
  readonly tooltipFacts?: TimelineTooltipFact[];
  readonly tooltipHint?: string;
}

/** Positioned time interval bar on the shared axis. */
export declare interface TimelineIntervalBar {
  readonly startTime?: string;
  readonly endTime?: string;
  readonly leftPercent?: number;
  readonly widthPercent?: number;
  readonly startOffsetText?: string;
  readonly endOffsetText?: string;
  readonly durationText?: string;
}

/** Direct child step row inside a collapsible test phase. */
export declare interface TestTimelineStepRow {
  readonly label?: string;
  readonly detail?: string;
  readonly focusKey?: string;
  readonly pairKey?: string;
  readonly fill?: TimelineFill;
  readonly durationText?: string;
  readonly shareText?: string;
  readonly bars?: TimelineIntervalBar[];
  readonly isEndedStep?: boolean;
  readonly resultTone?: TimelineResultTone;
  readonly resultLabel?: string;
  readonly endMarkLeftPercent?: number;
  readonly errorMessage?: string;
  readonly fullErrorMessage?: string;
  readonly tooltipHint?: string;
}

/** Collapsible phase header and its chronological child step rows. */
export declare interface TestTimelinePhase {
  readonly phaseId?: string;
  readonly focusKey?: string;
  readonly label?: string;
  readonly fill?: TimelineFill;
  readonly stepCountText?: string;
  readonly bracketLeftPercent?: number;
  readonly bracketWidthPercent?: number;
  readonly durationText?: string;
  readonly shareText?: string;
  readonly rows?: TestTimelineStepRow[];
}

/** Phase-grouped step timeline for a single test execution. */
export declare interface TestTimeline {
  readonly phases?: TestTimelinePhase[];
}

/** Contiguous job state interval in the top Job activity row. */
export declare interface JobActivityBar {
  readonly focusKey?: string;
  readonly label?: string;
  readonly fill?: TimelineFill;
  readonly interval?: TimelineIntervalBar;
  readonly shareText?: string;
}

/** Bar inside the Job setup row (queued, preparing, or file resolving). */
export declare interface JobSetupBar {
  readonly focusKey?: string;
  readonly label?: string;
  readonly detail?: string;
  readonly fill?: TimelineFill;
  readonly isInner?: boolean;
  readonly interval?: TimelineIntervalBar;
  readonly shareText?: string;
}

/** Job-level queue and pre-run setup row before device allocation. */
export declare interface JobSetupRow {
  readonly detail?: string;
  readonly durationText?: string;
  readonly bars?: JobSetupBar[];
}

/** Positioned bar segment inside a child test lane on the Job timeline. */
export declare interface JobLaneBar {
  readonly focusKey?: string;
  readonly fill?: TimelineFill;
  readonly leftPercent?: number;
  readonly widthPercent?: number;
}

/** Recorded run duration for a child test lane. */
export declare interface JobLaneRunRecorded {
  readonly durationText?: string;
}

/** Unrecorded run state for a child test lane ("Not started" or "Unknown"). */
export declare interface JobLaneRunUnrecorded {
  readonly reason?: string;
}

/** Display value for the Ran column of a child test lane. */
export declare interface JobLaneRunCell {
  readonly recorded?: JobLaneRunRecorded;
  readonly unrecorded?: JobLaneRunUnrecorded;
}

/** Child test run lane on the Job timeline. */
export declare interface JobTestLane {
  readonly testId?: string;
  readonly displayName?: string;
  readonly fullName?: string;
  readonly hasRecord?: boolean;
  readonly runTag?: string;
  readonly deviceIds?: string[];
  readonly resultTone?: TimelineResultTone;
  readonly resultLabel?: string;
  readonly waitedText?: string;
  readonly ranCell?: JobLaneRunCell;
  readonly bars?: JobLaneBar[];
  readonly hasEndMark?: boolean;
  readonly endMarkLeftPercent?: number;
  readonly tooltipRows?: TimelineTooltipFact[];
  readonly overheadHint?: string;
  readonly missingRecordHint?: string;
}

/** State activity breakdown and per-test lanes for a job execution. */
export declare interface JobTimeline {
  readonly totalDurationText?: string;
  readonly activityBars?: JobActivityBar[];
  readonly jobSetup?: JobSetupRow;
  readonly lanes?: JobTestLane[];
}

/** Pre-computed display model for the Test Detail and Job Detail Timeline tabs. */
export declare interface TimeBreakdown {
  readonly summaryLines?: string[];
  readonly originTime?: string;
  readonly finishTime?: string;
  readonly axis?: TimelineAxis;
  readonly splitSegments?: TimelineSplitSegment[];
  readonly testTimeline?: TestTimeline;
  readonly jobTimeline?: JobTimeline;
  /**
   * Whether the test or job is still running. Its timeline then runs up to the
   * time the response was built and keeps changing, so it is fetched again
   * until this is false.
   */
  readonly live?: boolean;
}
