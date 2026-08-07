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
import {JobStatus} from '../../../../core/models/job_overview';
import {JOB_SERVICE} from '../../../../core/services/job/job_service';
import {
  LogFetchStrategy,
  StreamingLogViewerComponent,
} from '../../../../shared/components/streaming_log_viewer/streaming_log_viewer';

/** Component for rendering the job log tab content using generic log viewer. */
@Component({
  selector: 'app-job-log-tab',
  standalone: true,
  imports: [StreamingLogViewerComponent],
  templateUrl: './job_log_tab.ng.html',
  styleUrl: './job_log_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JobLogTab {
  /** The unique job ID passed from the parent component. */
  readonly jobId = input.required<string>();
  readonly cloudLogLink = input<string>('');
  readonly initialStatus = input<JobStatus>(JobStatus.JOB_STATUS_UNSPECIFIED);
  readonly streamCompleted = output<void>();

  readonly isJobRunning = computed(
    () => this.initialStatus() === JobStatus.JOB_STATUS_RUNNING,
  );

  private readonly jobService = inject(JOB_SERVICE);

  readonly logViewer = viewChild(StreamingLogViewerComponent);
  readonly logLines = computed(() => this.logViewer()?.logLines() || []);
  readonly logViewport = computed(() => this.logViewer()?.logViewport());

  readonly logStrategy: LogFetchStrategy = (offset: number, hash?: string) =>
    this.jobService
      .getJobLog({
        jobId: this.jobId(),
        offset,
        contentHash: hash,
      })
      .pipe(
        map((resp) => ({
          offset: resp.nextOffset,
          content: resp.logContent || '',
          isDone:
            resp.jobStatus === JobStatus.JOB_STATUS_DONE ||
            resp.jobStatus === JobStatus.JOB_STATUS_SUSPENDED,
          reset: resp.logReset,
          hash: resp.contentHash,
        })),
      );
}
