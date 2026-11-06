import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  linkedSignal,
  signal,
} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {
  JobActivityBar,
  JobSetupBar,
  JobTestLane,
  TestTimelinePhase,
  TestTimelineStepRow,
  TestTimelineSubTestRow,
  TimeBreakdown,
  TimelineFill,
  TimelineIntervalBar,
  TimelineResultTone,
  TimelineSplitSegment,
  TimelineTickAlignment,
} from '../../../core/models/timeline';
import {NavLink} from '../nav_link/nav_link';
import {TimelineTooltipRow, TimelineTooltipView} from './timeline_chart_models';

/** Maximum sub-tests shown inline under a driver row before an expand toggle. */
const SUBTEST_VISIBLE_CAP = 12;

/** Maximum child test lanes shown initially on a Job timeline before "Show more". */
const JOB_LANE_VISIBLE_CAP = 40;

const TOOLTIP_OFFSET_PX = 14;
const TOOLTIP_MAX_WIDTH_PX = 360;
const TOOLTIP_ESTIMATED_HEIGHT_PX = 160;

const CLOCK_FORMATTER = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
  second: '2-digit',
  timeZoneName: 'short',
});

/** Renders the Test Detail and Job Detail Timeline view from a BFF `TimeBreakdown`. */
@Component({
  selector: 'app-timeline-chart',
  standalone: true,
  imports: [MatIconModule, NavLink],
  templateUrl: './timeline_chart.ng.html',
  styleUrl: './timeline_chart.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TimelineChart {
  readonly timingBreakdown = input<TimeBreakdown | undefined>();
  readonly jobId = input<string>('');
  readonly loading = input<boolean>(false);
  readonly errorMessage = input<string | null>(null);
  readonly emptyMessage = input<string>('No timing was recorded.');
  /** Says how a live timeline keeps itself current; empty when it is final. */
  readonly liveNote = input<string>('');

  readonly subTestCap = SUBTEST_VISIBLE_CAP;

  /** Identifies the test or job being shown so live polls keep open rows expanded. */
  private readonly timelineIdentity = computed<string>(() => {
    const breakdown = this.timingBreakdown();
    if (!breakdown) {
      return '';
    }
    const kind = breakdown.jobTimeline ? 'job' : 'test';
    return `${kind}|${this.jobId()}|${breakdown.originTime ?? ''}`;
  });

  readonly focusedKey = linkedSignal<string | null>(() => {
    this.timingBreakdown();
    return null;
  });

  readonly pairedKey = linkedSignal<string | null>(() => {
    this.timingBreakdown();
    return null;
  });

  readonly collapsedPhaseIds = linkedSignal<ReadonlySet<string>>(() => {
    this.timelineIdentity();
    return new Set<string>();
  });

  readonly expandedSubTestRowKeys = linkedSignal<ReadonlySet<string>>(() => {
    this.timelineIdentity();
    return new Set<string>();
  });

  readonly showAllLanes = linkedSignal<boolean>(() => {
    this.timelineIdentity();
    return false;
  });

  readonly activeTooltip = linkedSignal<TimelineTooltipView | null>(() => {
    this.timingBreakdown();
    return null;
  });

  readonly tooltipPosition = signal<{left: number; top: number}>({
    left: 0,
    top: 0,
  });

  readonly hasContent = computed<boolean>(() => {
    const breakdown = this.timingBreakdown();
    return Boolean(
      (breakdown?.testTimeline?.phases &&
        breakdown.testTimeline.phases.length > 0) ||
        breakdown?.jobTimeline,
    );
  });

  readonly headline = computed<string>(
    () => this.timingBreakdown()?.summaryLines?.[0] ?? '',
  );

  readonly subheadline = computed<string>(() => {
    const lines = this.timingBreakdown()?.summaryLines ?? [];
    return lines.length > 1 ? lines.slice(1).join(' ') : '';
  });

  readonly originClockText = computed<string>(() =>
    formatClock(this.timingBreakdown()?.originTime),
  );

  readonly trackTickSize = computed<string>(() => {
    const raw = this.timingBreakdown()?.axis?.tickStepPercent;
    const pct = raw && raw > 0 ? raw : 25;
    return `${pct}% 100%`;
  });

  readonly visibleJobLanes = computed<readonly JobTestLane[]>(() => {
    const lanes = this.timingBreakdown()?.jobTimeline?.lanes ?? [];
    return this.showAllLanes() ? lanes : lanes.slice(0, JOB_LANE_VISIBLE_CAP);
  });

  readonly hiddenJobLaneCount = computed<number>(() => {
    const total = this.timingBreakdown()?.jobTimeline?.lanes?.length ?? 0;
    return Math.max(0, total - JOB_LANE_VISIBLE_CAP);
  });

  readonly hasOpaqueLaneBar = computed<boolean>(() => {
    const lanes = this.timingBreakdown()?.jobTimeline?.lanes ?? [];
    return lanes.some((lane) =>
      (lane.bars ?? []).some((bar) => bar.fill === 'TIMELINE_FILL_OPAQUE'),
    );
  });

  readonly hasMissingLaneBar = computed<boolean>(() => {
    const lanes = this.timingBreakdown()?.jobTimeline?.lanes ?? [];
    return lanes.some((lane) =>
      (lane.bars ?? []).some((bar) => bar.fill === 'TIMELINE_FILL_MISSING'),
    );
  });

  fillClass(fill?: TimelineFill): string {
    switch (fill) {
      case 'TIMELINE_FILL_WAIT':
        return 'tl-fill-wait';
      case 'TIMELINE_FILL_QUEUED':
        return 'tl-fill-queued';
      case 'TIMELINE_FILL_SETUP':
        return 'tl-fill-setup';
      case 'TIMELINE_FILL_RESOLVE':
        return 'tl-fill-resolve';
      case 'TIMELINE_FILL_TEST':
        return 'tl-fill-test';
      case 'TIMELINE_FILL_TEARDOWN':
        return 'tl-fill-teardown';
      case 'TIMELINE_FILL_OPAQUE':
        return 'tl-fill-opaque';
      case 'TIMELINE_FILL_UNRECORDED':
        return 'tl-fill-unrecorded';
      case 'TIMELINE_FILL_UNKNOWN':
        return 'tl-fill-unknown';
      case 'TIMELINE_FILL_MISSING':
        return 'tl-fill-missing';
      default:
        return 'tl-fill-setup';
    }
  }

  tickAlignmentClass(alignment?: TimelineTickAlignment): string {
    switch (alignment) {
      case 'TIMELINE_TICK_ALIGNMENT_START':
        return 'tl-tick--start';
      case 'TIMELINE_TICK_ALIGNMENT_END':
        return 'tl-tick--end';
      default:
        return '';
    }
  }

  toneSuffix(tone?: TimelineResultTone): string {
    switch (tone) {
      case 'TIMELINE_RESULT_TONE_PASS':
        return 'pass';
      case 'TIMELINE_RESULT_TONE_FAIL':
        return 'fail';
      case 'TIMELINE_RESULT_TONE_TIMEOUT':
        return 'timeout';
      default:
        return 'neutral';
    }
  }

  isPhaseExpanded(phase: TestTimelinePhase, index: number): boolean {
    const id = phase.phaseId || `phase-${index}`;
    return !this.collapsedPhaseIds().has(id);
  }

  togglePhase(phase: TestTimelinePhase, index: number): void {
    const id = phase.phaseId || `phase-${index}`;
    const next = new Set(this.collapsedPhaseIds());
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    this.collapsedPhaseIds.set(next);
  }

  visibleSubTests(
    subTests: readonly TestTimelineSubTestRow[] | undefined,
    rowKey: string,
  ): readonly TestTimelineSubTestRow[] {
    if (!subTests?.length) {
      return [];
    }
    return this.expandedSubTestRowKeys().has(rowKey)
      ? subTests
      : subTests.slice(0, SUBTEST_VISIBLE_CAP);
  }

  isSubTestGroupExpanded(rowKey: string): boolean {
    return this.expandedSubTestRowKeys().has(rowKey);
  }

  toggleSubTestGroup(rowKey: string): void {
    const next = new Set(this.expandedSubTestRowKeys());
    if (next.has(rowKey)) {
      next.delete(rowKey);
    } else {
      next.add(rowKey);
    }
    this.expandedSubTestRowKeys.set(next);
  }

  toggleShowAllLanes(): void {
    this.showAllLanes.update((open) => !open);
  }

  isFocused(key?: string): boolean {
    const active = this.focusedKey();
    return active !== null && Boolean(key) && active === key;
  }

  isPaired(pairKey?: string): boolean {
    const active = this.pairedKey();
    return active !== null && Boolean(pairKey) && active === pairKey;
  }

  laneEndMarkFocusKey(lane: JobTestLane): string {
    const bars = lane.bars ?? [];
    const lastFocusKey =
      bars.length > 0 ? bars[bars.length - 1].focusKey : undefined;
    return lastFocusKey || (lane.ranCell?.recorded ? 'running' : 'waiting');
  }

  enterSplitSegment(event: MouseEvent, seg: TimelineSplitSegment): void {
    this.focusedKey.set(seg.focusKey ?? null);
    const rows: TimelineTooltipRow[] = [
      {
        label: 'Duration',
        value: seg.durationText ?? '',
        muted: seg.shareText ? `${seg.shareText} of total` : undefined,
      },
      ...(seg.tooltipFacts ?? []).map((f) => ({
        label: f.label ?? '',
        value: f.value ?? '',
      })),
    ];
    this.activeTooltip.set({
      title: seg.label ?? '',
      rows,
      hints: seg.tooltipHint ? [seg.tooltipHint] : [],
    });
    this.updatePointerPosition(event);
  }

  leaveSplitSegment(): void {
    this.focusedKey.set(null);
    this.activeTooltip.set(null);
  }

  enterTestStepRow(event: MouseEvent, row: TestTimelineStepRow): void {
    this.pairedKey.set(row.pairKey || null);
    const bars = row.bars ?? [];
    const rows: TimelineTooltipRow[] = [
      {
        label: 'Duration',
        value: row.durationText ?? '',
        muted: row.shareText ? `${row.shareText} of total` : undefined,
      },
    ];
    if (bars.length === 1) {
      rows.push(...intervalTooltipRows(bars[0]));
    } else {
      bars.forEach((bar, idx) => {
        const offset = bar.startOffsetText ?? '';
        const dur = bar.durationText ?? '';
        rows.push({
          label: `Part ${idx + 1}`,
          value: formatClock(bar.startTime),
          muted: offset && dur ? `${offset}, ${dur}` : offset || dur,
        });
      });
    }
    this.activeTooltip.set({
      title: row.label ?? '',
      subtitle: row.detail || undefined,
      rows,
      hints: row.tooltipHint ? [row.tooltipHint] : [],
    });
    this.updatePointerPosition(event);
  }

  enterSubTestRow(event: MouseEvent, subTest: TestTimelineSubTestRow): void {
    this.pairedKey.set(null);
    const rows: TimelineTooltipRow[] = [
      {
        label: 'Duration',
        value: subTest.durationText ?? '',
      },
      ...(subTest.bar ? intervalTooltipRows(subTest.bar) : []),
    ];
    this.activeTooltip.set({
      title: subTest.name ?? '',
      subtitle: subTest.tooltipDetail || undefined,
      rows,
      hints: [],
    });
    this.updatePointerPosition(event);
  }

  enterJobBar(
    event: MouseEvent,
    bar: JobActivityBar | JobSetupBar,
    detail?: string,
  ): void {
    const interval = bar.interval;
    const rows: TimelineTooltipRow[] = [];
    if (interval) {
      rows.push({
        label: 'Duration',
        value: interval.durationText ?? '',
        muted: bar.shareText ? `${bar.shareText} of total` : undefined,
      });
      rows.push(...intervalTooltipRows(interval));
    }
    this.activeTooltip.set({
      title: bar.label ?? '',
      subtitle: detail || undefined,
      rows,
      hints: [],
    });
    this.updatePointerPosition(event);
  }

  enterJobLane(event: MouseEvent, lane: JobTestLane): void {
    const hints: string[] = [];
    if (lane.overheadHint) {
      hints.push(lane.overheadHint);
    }
    if (lane.missingRecordHint) {
      hints.push(lane.missingRecordHint);
    }
    this.activeTooltip.set({
      title: lane.hasRecord
        ? lane.fullName || lane.displayName || ''
        : 'No test record',
      subtitle: lane.testId || undefined,
      rows: (lane.tooltipRows ?? []).map((r) => ({
        label: r.label ?? '',
        value: r.value ?? '',
      })),
      hints,
    });
    this.updatePointerPosition(event);
  }

  leaveRowTooltip(): void {
    this.pairedKey.set(null);
    this.activeTooltip.set(null);
  }

  clearAllHover(): void {
    this.focusedKey.set(null);
    this.pairedKey.set(null);
    this.activeTooltip.set(null);
  }

  updatePointerPosition(event: MouseEvent): void {
    if (!this.activeTooltip()) {
      return;
    }
    const viewportWidth =
      typeof window !== 'undefined' ? window.innerWidth : 1280;
    const viewportHeight =
      typeof window !== 'undefined' ? window.innerHeight : 800;
    let left = event.clientX + TOOLTIP_OFFSET_PX;
    let top = event.clientY + TOOLTIP_OFFSET_PX;
    if (left + TOOLTIP_MAX_WIDTH_PX > viewportWidth - 8) {
      left = event.clientX - TOOLTIP_MAX_WIDTH_PX - TOOLTIP_OFFSET_PX;
    }
    if (top + TOOLTIP_ESTIMATED_HEIGHT_PX > viewportHeight - 8) {
      top = event.clientY - TOOLTIP_ESTIMATED_HEIGHT_PX - TOOLTIP_OFFSET_PX;
    }
    this.tooltipPosition.set({
      left: Math.max(8, left),
      top: Math.max(8, top),
    });
  }
}

function formatClock(isoTime?: string): string {
  if (!isoTime) {
    return '';
  }
  const date = new Date(isoTime);
  if (isNaN(date.getTime())) {
    return '';
  }
  return CLOCK_FORMATTER.format(date);
}

function intervalTooltipRows(
  interval: TimelineIntervalBar,
): TimelineTooltipRow[] {
  return [
    {
      label: 'From',
      value: formatClock(interval.startTime),
      muted: interval.startOffsetText || undefined,
    },
    {
      label: 'To',
      value: formatClock(interval.endTime),
      muted: interval.endOffsetText || undefined,
    },
  ];
}
