import {DatePipe, DecimalPipe} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  input,
  linkedSignal,
  viewChild,
} from '@angular/core';
import {TimeBreakdown} from '../../../core/models/timeline';
import {StageRow, TimelineEvent} from './timeline_chart_models';

const TICK_RATIOS = [0, 0.25, 0.5, 0.75, 1] as const;

/**
 * Allocates track indices to timeline events in a single O(N*M) pass using findIndex
 * to avoid visual overlapping without nested imperative loops.
 */
function allocateTracks(events: TimelineEvent[]): number {
  events.sort((a, b) => a.offset - b.offset);
  const tracksEndTimes: number[] = [];

  for (const event of events) {
    const idx = tracksEndTimes.findIndex(
      (endTime) => event.offset >= endTime - 0.01,
    );

    if (idx !== -1) {
      event.trackIndex = idx;
      tracksEndTimes[idx] = event.offset + event.duration;
    } else {
      event.trackIndex = tracksEndTimes.length;
      tracksEndTimes.push(event.offset + event.duration);
    }
  }

  return tracksEndTimes.length;
}

/** Component for rendering a generic timeline / Gantt chart. */
@Component({
  selector: 'app-timeline-chart',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  templateUrl: './timeline_chart.ng.html',
  styleUrl: './timeline_chart.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TimelineChart {
  /** Optional timing breakdown metadata passed from the parent component, detailing stage start/end timestamps. */
  readonly timingBreakdown = input<TimeBreakdown | undefined>();

  /** The color theme for the timeline bars. Default is 'blue'. */
  readonly theme = input<'green' | 'blue'>('blue');

  /** Reference to the Gantt chart tracks container for tooltip positioning calculations. */
  readonly rowsContainer = viewChild<ElementRef<HTMLElement>>('rowsContainer');

  /** Current hovered event details for displaying the single shared tooltip. */
  readonly hoveredEvent = linkedSignal<{
    event: TimelineEvent;
    rowName: string;
    top: number;
    left: number;
    placement: 'left' | 'right';
  } | null>(() => {
    this.timingBreakdown();
    return null;
  });

  private readonly parsedStages = computed(() => {
    const breakdown = this.timingBreakdown();
    if (!breakdown?.stages?.length) {
      return {
        groupedStages: new Map<string, TimelineEvent[]>(),
        maxEndOffset: 0,
        baseline: 0,
      };
    }

    const baseline = new Date(breakdown.createTime).getTime();
    const groupedStages = new Map<string, TimelineEvent[]>();
    let maxEndOffset = 0;

    for (const stage of breakdown.stages) {
      const startMs = stage.startTime
        ? new Date(stage.startTime).getTime()
        : baseline;
      const endMs = stage.endTime
        ? new Date(stage.endTime).getTime()
        : Date.now();
      const offset = (startMs - baseline) / 1000;
      const duration = (endMs - startMs) / 1000;
      const endOffset = (endMs - baseline) / 1000;

      if (endOffset > maxEndOffset) {
        maxEndOffset = endOffset;
      }

      const event: TimelineEvent = {
        label: stage.tag || '',
        duration,
        offset,
        inProgress: !stage.endTime,
        trackIndex: 0,
        startTime: stage.startTime || breakdown.createTime,
        endTime: stage.endTime,
      };

      let list = groupedStages.get(stage.name);
      if (!list) {
        list = [];
        groupedStages.set(stage.name, list);
      }
      list.push(event);
    }

    return {groupedStages, maxEndOffset, baseline};
  });

  /**
   * Calculates the maximum total duration across all timeline events to establish
   * the 100% scale boundary for Gantt chart rendering.
   */
  readonly totalDuration = computed(() => this.parsedStages().maxEndOffset);

  /**
   * Computed array of stage rows where events with the same stage name are grouped together
   * and resolved for collisions dynamically (placing non-overlapping events on the same line).
   */
  readonly stageRows = computed<StageRow[]>(() => {
    const {groupedStages} = this.parsedStages();
    if (groupedStages.size === 0) {
      return [];
    }

    return Array.from(groupedStages.entries(), ([stageName, events]) => {
      // Clone events to keep computed function pure and side-effect free
      const clonedEvents = events.map((event) => ({...event}));
      const rowCount = allocateTracks(clonedEvents);
      return {
        stageName,
        rowCount,
        tracks: Array.from({length: rowCount}, (_, i) => i),
        events: clonedEvents,
      };
    });
  });

  /**
   * Computed 5 absolute time points distributed at 0%, 25%, 50%, 75%, 100%
   * of the total timeline duration.
   */
  readonly timelineTicks = computed<Date[]>(() => {
    const {baseline, maxEndOffset} = this.parsedStages();
    if (!baseline || maxEndOffset === 0) {
      return [];
    }

    const durationMs = maxEndOffset * 1000;
    return TICK_RATIOS.map((ratio) => new Date(baseline + durationMs * ratio));
  });

  showTooltip(event: MouseEvent, ev: TimelineEvent, rowName: string) {
    const barElement = event.currentTarget as HTMLElement;
    const containerElement = this.rowsContainer()?.nativeElement;
    if (!containerElement) return;

    const barRect = barElement.getBoundingClientRect();
    const containerRect = containerElement.getBoundingClientRect();

    const top = barRect.top - containerRect.top + barRect.height / 2;

    // Check if there is enough space on the right side of the bar
    const spaceOnRight = containerRect.right - barRect.right;
    const placement = spaceOnRight > 280 ? 'right' : 'left';

    const left =
      placement === 'right'
        ? barRect.right - containerRect.left + 8
        : barRect.left - containerRect.left - 8;

    this.hoveredEvent.set({
      event: ev,
      rowName,
      top,
      left,
      placement,
    });
  }

  hideTooltip() {
    this.hoveredEvent.set(null);
  }
}
