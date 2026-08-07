import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import {map} from 'rxjs/operators';
import {TestStatus} from '../../../../core/models/test_overview';
import {TEST_SERVICE} from '../../../../core/services/test/test_service';
import {
  LogFetchStrategy,
  StreamingLogViewerComponent,
} from '../../../../shared/components/streaming_log_viewer/streaming_log_viewer';

/** Component for rendering the test log tab content using generic log viewer. */
@Component({
  selector: 'app-test-log-tab',
  standalone: true,
  imports: [StreamingLogViewerComponent],
  templateUrl: './test_log_tab.ng.html',
  styleUrl: './test_log_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TestLogTab {
  /** The unique test ID passed from the parent component. */
  readonly testId = input.required<string>();
  readonly jobId = input.required<string>();
  readonly cloudLogLink = input<string>('');
  readonly initialStatus = input<TestStatus>(
    TestStatus.TEST_STATUS_UNSPECIFIED,
  );
  readonly streamCompleted = output<void>();
  readonly isTestRunning = computed(
    () => this.initialStatus() === TestStatus.TEST_STATUS_RUNNING,
  );

  private readonly testService = inject(TEST_SERVICE);

  readonly logViewer = viewChild(StreamingLogViewerComponent);
  readonly logLines = computed(() => this.logViewer()?.logLines() || []);
  readonly logViewport = computed(() => this.logViewer()?.logViewport());

  readonly logStrategy: LogFetchStrategy = (offset: number, hash?: string) =>
    this.testService
      .getTestLog({
        testId: this.testId(),
        jobId: this.jobId(),
        offset,
        contentHash: hash,
      })
      .pipe(
        map((resp) => ({
          offset: resp.nextOffset,
          content: resp.logContent || '',
          isDone:
            resp.testStatus === TestStatus.TEST_STATUS_DONE ||
            resp.testStatus === TestStatus.TEST_STATUS_SUSPENDED,
          reset: resp.logReset,
          hash: resp.contentHash,
        })),
      );
}
