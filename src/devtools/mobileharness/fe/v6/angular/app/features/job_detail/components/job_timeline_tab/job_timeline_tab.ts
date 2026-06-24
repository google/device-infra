import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import {MatTooltipModule} from '@angular/material/tooltip';
import {
  JobTimelineEvent,
  TimeBreakdown,
} from '../../../../core/models/job_overview';

/** Component for rendering the job timeline tab content. */
@Component({
  selector: 'app-job-timeline-tab',
  standalone: true,
  imports: [CommonModule, MatTooltipModule],
  templateUrl: './job_timeline_tab.ng.html',
  styleUrl: './job_timeline_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JobTimelineTab {
  /** Optional timing breakdown metadata passed from the parent component, detailing stage start/end timestamps. */
  readonly timingBreakdown = input<TimeBreakdown | undefined>();

  /**
   * Computed array of normalized timeline events calculated relative to the job creation baseline.
   * Determines offsets, durations, and active in-progress flags for each execution stage.
   */
  readonly timelineEvents = computed(() => {
    const breakdown = this.timingBreakdown();
    if (!breakdown || !breakdown.stages || breakdown.stages.length === 0) {
      return [];
    }

    const baseline = new Date(breakdown.createTime).getTime();

    const events: JobTimelineEvent[] = breakdown.stages.map((stage) => {
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
   * @param events The array of calculated job timeline events.
   * @return The total duration in seconds.
   */
  getTimelineTotalDuration(events: JobTimelineEvent[]): number {
    return events
      ? events.reduce(
          (max, event) => Math.max(max, event.offset + event.duration),
          0,
        )
      : 0;
  }
}
