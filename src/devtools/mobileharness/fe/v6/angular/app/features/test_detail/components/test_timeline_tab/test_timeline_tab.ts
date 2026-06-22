import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import {MatTooltipModule} from '@angular/material/tooltip';
import {
  TestTimelineEvent,
  TimeBreakdown,
} from '../../../../core/models/test_overview';

/** Component for rendering the test timeline tab content. */
@Component({
  selector: 'app-test-timeline-tab',
  standalone: true,
  imports: [CommonModule, MatTooltipModule],
  templateUrl: './test_timeline_tab.ng.html',
  styleUrl: './test_timeline_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TestTimelineTab {
  /** Optional timing breakdown metadata passed from the parent component, detailing stage start/end timestamps. */
  readonly timingBreakdown = input<TimeBreakdown | undefined>();

  /**
   * Computed array of normalized timeline events calculated relative to the test creation baseline.
   * Determines offsets, durations, and active in-progress flags for each execution stage.
   */
  readonly timelineEvents = computed(() => {
    const breakdown = this.timingBreakdown();
    if (!breakdown || !breakdown.stages || breakdown.stages.length === 0) {
      return [];
    }

    const baseline = new Date(breakdown.createTime).getTime();

    const events: TestTimelineEvent[] = breakdown.stages.map((stage) => {
      const startMs = stage.startTime
        ? new Date(stage.startTime).getTime()
        : baseline;
      const endMs = stage.endTime
        ? new Date(stage.endTime).getTime()
        : Date.now();

      const offset = Math.max(0, (startMs - baseline) / 1000);
      const duration = Math.max(0, (endMs - startMs) / 1000);
      const inProgress = !stage.endTime;

      return {
        name: stage.name + (stage.tag ? ` (${stage.tag})` : ''),
        offset,
        duration,
        inProgress,
      };
    });

    return events;
  });

  /**
   * Calculates the maximum total duration across all timeline events to establish
   * the 100% scale boundary for Gantt chart rendering.
   *
   * @param events The array of calculated test timeline events.
   * @return The total duration in seconds.
   */
  getTimelineTotalDuration(events: TestTimelineEvent[]): number {
    return events
      ? events.reduce(
          (max, event) => Math.max(max, event.offset + event.duration),
          0,
        )
      : 0;
  }
}
