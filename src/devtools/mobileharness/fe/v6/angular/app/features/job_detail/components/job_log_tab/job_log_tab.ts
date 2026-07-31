import {
  CdkVirtualScrollViewport,
  ScrollingModule,
} from '@angular/cdk/scrolling';
import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {EMPTY, Observable, timer} from 'rxjs';
import {catchError, concatMap, expand, finalize, map} from 'rxjs/operators';

import {JobStatus} from '../../../../core/models/job_overview';
import {JOB_SERVICE} from '../../../../core/services/job/job_service';

/** Internal state representing the outcome of a log chunk fetch operation. */
interface FetchState {
  /** The byte offset representing the end of the downloaded log chunk. */
  offset: number;
  /** The current execution status of the job being monitored. */
  status: JobStatus;
  /** The raw textual log content downloaded in this specific chunk. */
  logContent: string;
  contentHash?: string;
  logReset?: boolean;
}

const POLLING_INTERVAL_MS = 2000;

/** Component for rendering the job log tab content. */
@Component({
  selector: 'app-job-log-tab',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatTooltipModule, ScrollingModule],
  templateUrl: './job_log_tab.ng.html',
  styleUrl: './job_log_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JobLogTab implements OnInit {
  /** The unique job ID passed from the parent component. */
  readonly jobId = input.required<string>();

  private readonly jobService = inject(JOB_SERVICE);
  private readonly destroyRef = inject(DestroyRef);

  /** Signal holding the array of log lines currently rendered in the virtual scroll viewport. */
  readonly logLines = signal<string[]>(['Loading logs...']);
  /** Input or fetched external cloud log explorer URL for this job. */
  readonly cloudLogLinkInput = input<string>('', {alias: 'cloudLogLink'});
  private readonly cloudLogLinkFetched = signal<string>('');
  readonly cloudLogLink = computed(
    () => this.cloudLogLinkInput() || this.cloudLogLinkFetched(),
  );

  /** Buffer preserving incomplete line string fragments across network fetch boundaries. */
  private trailingBuffer = '';

  /** Computed absolute height of the virtual scroll viewport constrained to a maximum of 552px. */
  readonly viewportHeight = computed(() => {
    const lines = this.logLines();
    const isSpecialMsg =
      lines.length <= 1 &&
      (lines[0] === 'Loading logs...' ||
        lines[0] === 'No logs available for this job.');
    if (isSpecialMsg) {
      return 20;
    }
    let totalVisualRows = 0;
    for (const line of lines) {
      totalVisualRows += Math.max(1, Math.ceil(line.length / 80));
    }
    return Math.min(552, Math.max(100, totalVisualRows * 20));
  });

  /** Reference to the virtual scroll viewport directive. */
  readonly logViewport = viewChild<CdkVirtualScrollViewport>('logViewport');

  constructor() {
    effect(() => {
      this.logLines();
      this.scrollToBottom();
    });
  }

  ngOnInit() {
    this.startLiveLogStreaming();
  }

  /**
   * Initiates the live log streaming mechanism by fetching the initial log chunk
   * and setting up a recursive polling/expansion loop until the job completes.
   */
  private startLiveLogStreaming() {
    const id = this.jobId();

    if (!this.cloudLogLinkInput()) {
      this.jobService.getJob(id).subscribe({
        next: (resp) => {
          if (resp.job.executionDetails?.cloudLogLink) {
            this.cloudLogLinkFetched.set(
              resp.job.executionDetails.cloudLogLink,
            );
          }
        },
        error: () => {},
      });
    }

    const initialFetch$: Observable<FetchState> = this.fetchLogChunk(id, 0);

    initialFetch$
      .pipe(
        expand((state: FetchState): Observable<FetchState> => {
          if (
            state.status === JobStatus.JOB_STATUS_DONE ||
            state.status === JobStatus.JOB_STATUS_SUSPENDED
          ) {
            return EMPTY;
          }

          return timer(POLLING_INTERVAL_MS).pipe(
            concatMap(() =>
              this.fetchLogChunk(id, state.offset, state.contentHash),
            ),
          );
        }),
        catchError((err) => {
          console.error('Error fetching dynamic job logs:', err);
          return EMPTY;
        }),
        finalize(() => {
          if (
            this.logLines().length === 1 &&
            this.logLines()[0] === 'Loading logs...'
          ) {
            this.logLines.set(['No logs available for this job.']);
          }
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (state: FetchState) => {
          if (state.logReset) {
            this.logLines.set([]);
            this.trailingBuffer = '';
          }
          if (state.logContent) {
            this.appendLogs(state.logContent);
          }
          const isStreamingDone =
            state.status === JobStatus.JOB_STATUS_DONE ||
            state.status === JobStatus.JOB_STATUS_SUSPENDED;
          const currentLines = this.logLines();
          const remainsLoading =
            currentLines.length === 1 && currentLines[0] === 'Loading logs...';
          if (isStreamingDone && remainsLoading) {
            this.logLines.set(['No logs available for this job.']);
          }
        },
      });
  }

  /**
   * Fetches a single chunk of log data from the backend server.
   *
   * @param id The unique job ID.
   * @param offset The starting byte offset for the log fetch.
   * @param contentHash Optional log integrity token.
   * @return An observable emitting the resulting fetch state.
   */
  private fetchLogChunk(
    id: string,
    offset: number,
    contentHash?: string,
  ): Observable<FetchState> {
    return this.jobService
      .getJobLog({
        jobId: id,
        offset,
        contentHash,
      })
      .pipe(
        map((logResp) => {
          return {
            offset: logResp.nextOffset,
            status: logResp.jobStatus,
            logContent: logResp.logContent || '',
            contentHash: logResp.contentHash,
            logReset: logResp.logReset,
          };
        }),
      );
  }

  /**
   * Appends newly downloaded log content using trailing stream buffering
   * to ensure line boundaries are preserved across network chunk splits.
   *
   * @param newLogs The byte-slice log string to append.
   */
  private appendLogs(newLogs: string) {
    if (!newLogs) {
      return;
    }
    this.logLines.update((current) => {
      const lines =
        current.length === 1 &&
        (current[0] === 'Loading logs...' ||
          current[0] === 'No logs available for this job.')
          ? []
          : [...current];

      // Normalize \r\n and terminal progress bar \r (carriage return) to \n before splitting.
      const normalizedLogs = newLogs
        .replace(/\r\n/g, '\n')
        .replace(/\r/g, '\n');
      const rawLines = normalizedLogs.split('\n');
      const hasPrefixMatch = this.trailingBuffer && lines.length > 0;
      if (hasPrefixMatch) {
        lines[lines.length - 1] += rawLines[0];
        rawLines.shift();
      }

      const endsWithNewline = normalizedLogs.endsWith('\n');
      this.trailingBuffer = endsWithNewline ? '' : rawLines.pop() || '';

      const lastIndex = rawLines.length - 1;
      const lastIsEmpty = endsWithNewline && rawLines[lastIndex] === '';
      if (lastIsEmpty) {
        rawLines.pop();
      }

      const nextLines = [...lines, ...rawLines];
      const hasTrailingOutput = !endsWithNewline && this.trailingBuffer;
      if (hasTrailingOutput) {
        nextLines.push(this.trailingBuffer);
      }

      return nextLines;
    });
  }

  trackByIndex(index: number, _item: string): number {
    return index;
  }

  private scrollToBottom() {
    const viewport = this.logViewport();
    if (viewport) {
      setTimeout(() => {
        try {
          viewport.checkViewportSize();
          viewport.scrollTo({bottom: 0});
        } catch {
          // Fallback if viewport measurement fails.
        }
      }, 100);
    }
  }
}
