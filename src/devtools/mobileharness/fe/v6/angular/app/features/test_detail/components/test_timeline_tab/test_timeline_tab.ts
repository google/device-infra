import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {TimeBreakdown} from '../../../../core/models/test_overview';
import {TimelineChart} from '../../../../shared/components/timeline_chart/timeline_chart';

/** Component for rendering the test timeline tab content. */
@Component({
  selector: 'app-test-timeline-tab',
  standalone: true,
  imports: [TimelineChart],
  templateUrl: './test_timeline_tab.ng.html',
  styleUrl: './test_timeline_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TestTimelineTab {
  /** Optional timing breakdown metadata passed from the parent component, detailing stage start/end timestamps. */
  readonly timingBreakdown = input<TimeBreakdown | undefined>();
}
