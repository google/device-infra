/** Represents a parsed timeline event for UI display. */
export interface TimelineEvent {
  label: string;
  duration: number;
  offset: number;
  inProgress: boolean;
  trackIndex: number;
  startTime: string;
  endTime?: string;
}

/** Represents a row of timeline events grouped under the same logical stage name. */
export interface StageRow {
  stageName: string;
  rowCount: number;
  tracks: number[];
  events: TimelineEvent[];
}
