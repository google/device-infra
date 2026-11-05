/** Single labeled row inside a timeline hover tooltip. */
export interface TimelineTooltipRow {
  readonly label: string;
  readonly value: string;
  readonly muted?: string;
}

/** Structured tooltip content displayed when hovering a timeline bar, row, or split segment. */
export interface TimelineTooltipView {
  readonly title: string;
  readonly subtitle?: string;
  readonly rows: readonly TimelineTooltipRow[];
  readonly hints: readonly string[];
}
