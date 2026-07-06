/** Represents a parsed timeline event for UI display. */
export interface TestTimelineEvent {
  readonly name: string;
  readonly duration: number;
  readonly offset: number;
  readonly inProgress?: boolean;
}

/**
 * Detailed self-contained lifecycle summary and timing breakdown for standalone timeline rendering.
 */
export interface TimelineSelfContainedSummary {
  readonly createTimeStr: string;
  readonly startTimeStr: string;
  readonly endTimeStr: string;
  readonly totalDurationStr: string;
  readonly totalDurationSec: number;
}
