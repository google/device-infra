import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {TimeBreakdown} from '../../../../core/models/job_overview';
import {TimelineChart} from '../../../../shared/components/timeline_chart/timeline_chart';

/** Component for rendering the job timeline tab content. */
@Component({
  selector: 'app-job-timeline-tab',
  standalone: true,
  imports: [TimelineChart],
  templateUrl: './job_timeline_tab.ng.html',
  styleUrl: './job_timeline_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JobTimelineTab {
  /** Optional timing breakdown metadata passed from the parent component, detailing stage start/end timestamps. */
  readonly timingBreakdown = input<TimeBreakdown | undefined>();
}
