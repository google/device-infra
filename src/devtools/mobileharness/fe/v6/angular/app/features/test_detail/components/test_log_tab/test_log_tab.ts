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

import {TestStatus} from '../../../../core/models/test_overview';
import {TEST_SERVICE} from '../../../../core/services/test/test_service';
import {FetchState} from '../../models';

const POLLING_INTERVAL_MS = 2000;

/** Component for rendering the test log tab content. */
@Component({
  selector: 'app-test-log-tab',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatTooltipModule, ScrollingModule],
  templateUrl: './test_log_tab.ng.html',
  styleUrl: './test_log_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TestLogTab implements OnInit {
  /** The unique test ID passed from the parent component. */
  readonly testId = input.required<string>();
  readonly jobId = input.required<string>();

  private readonly testService = inject(TEST_SERVICE);
  private readonly destroyRef = inject(DestroyRef);

  /** Signal holding the array of log lines currently rendered in the log viewport. */
  readonly logLines = signal<string[]>(['Loading logs...']);
  /** The external cloud log explorer URL for this test. */
  readonly cloudLogLink = input<string>('');

  /** Buffer preserving incomplete line string fragments across network fetch boundaries. */
  private trailingBuffer = '';

  /** Computed absolute height of the log viewport constrained to a maximum of 552px. */
  readonly viewportHeight = computed(() => {
    const lines = this.logLines();
    const isSpecialMsg =
      lines.length <= 1 &&
      (lines[0] === 'Loading logs...' ||
        lines[0] === 'No logs available for this test.');
    if (isSpecialMsg) {
      return 20;
    }
    let totalVisualRows = 0;
    for (const line of lines) {
      totalVisualRows += Math.max(1, Math.ceil(line.length / 80));
    }
    return Math.min(552, Math.max(100, totalVisualRows * 20));
  });

  /** Reference to the log viewer scroll container. */
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
   * and setting up a recursive polling/expansion loop until the test completes.
   */
  private startLiveLogStreaming() {
    const id = this.testId();

    // Fetch the initial chunk starting at offset 0.
    const initialFetch$: Observable<FetchState> = this.fetchLogChunk(id, 0);

    initialFetch$
      .pipe(
        // Use expand to recursively schedule subsequent page polls.
        expand((state: FetchState): Observable<FetchState> => {
          // If the test has terminated (DONE or SUSPENDED), stop polling since logs are final.
          if (
            state.status === TestStatus.TEST_STATUS_DONE ||
            state.status === TestStatus.TEST_STATUS_SUSPENDED
          ) {
            return EMPTY;
          }

          // Test is still running: wait for POLLING_INTERVAL_MS and query again
          // passing back the previous offset and its associated contentHash.
          return timer(POLLING_INTERVAL_MS).pipe(
            concatMap(() =>
              this.fetchLogChunk(id, state.offset, state.contentHash),
            ),
          );
        }),
        catchError((err) => {
          console.error('Error fetching dynamic test logs:', err);
          return EMPTY;
        }),
        finalize(() => {
          if (
            this.logLines().length === 1 &&
            this.logLines()[0] === 'Loading logs...'
          ) {
            this.logLines.set(['No logs available for this test.']);
          }
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (state: FetchState) => {
          // If the server signals logReset, it implies a contentHash mismatch has occurred
          // (e.g. log rotated/cleared). In this case we clear the cache and show the fresh log.
          if (state.logReset) {
            this.logLines.set([]);
            this.trailingBuffer = '';
          }
          if (state.logContent) {
            this.appendLogs(state.logContent);
          }
          const isStreamingDone =
            state.status === TestStatus.TEST_STATUS_DONE ||
            state.status === TestStatus.TEST_STATUS_SUSPENDED;
          const currentLines = this.logLines();
          const remainsLoading =
            currentLines.length === 1 && currentLines[0] === 'Loading logs...';
          if (isStreamingDone && remainsLoading) {
            this.logLines.set(['No logs available for this test.']);
          }
        },
      });
  }

  /**
   * Fetches a single chunk of log data from the backend server.
   *
   * @param id The unique test ID.
   * @param offset The starting byte offset for the log fetch.
   * @param contentHash Optional log integrity hash covering bytes [0, offset).
   * @return An observable emitting the resulting fetch state.
   */
  private fetchLogChunk(
    id: string,
    offset: number,
    contentHash?: string,
  ): Observable<FetchState> {
    return this.testService
      .getTestLog({
        testId: id,
        jobId: this.jobId(),
        offset,
        contentHash,
      })
      .pipe(
        map((logResp) => {
          // Map to FetchState, copying the authoritative test status and log-reset signals.
          return {
            offset: logResp.nextOffset,
            status: logResp.testStatus,
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
          current[0] === 'No logs available for this test.')
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
