import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
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
const CHUNK_SIZE = 100000;

/** Component for rendering the test log tab content. */
@Component({
  selector: 'app-test-log-tab',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatTooltipModule],
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

  /** The initial test status of the test. */
  readonly initialStatus = input<TestStatus>(
    TestStatus.TEST_STATUS_UNSPECIFIED,
  );

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
  readonly logViewport = viewChild<ElementRef<HTMLDivElement>>('logViewport');

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

    const initialFetch$: Observable<FetchState> = this.fetchLogChunk(
      id,
      0,
      this.initialStatus(),
    );

    initialFetch$
      .pipe(
        expand((state: FetchState): Observable<FetchState> => {
          if (state.status === TestStatus.TEST_STATUS_DONE && !state.hasMore) {
            return EMPTY;
          }

          if (state.hasMore) {
            return this.fetchLogChunk(id, state.offset, state.status);
          } else {
            return timer(POLLING_INTERVAL_MS).pipe(
              concatMap(() =>
                this.testService.getTest({testId: id, jobId: this.jobId()}),
              ),
              concatMap((testDetail) => {
                return this.fetchLogChunk(id, state.offset, testDetail.status);
              }),
            );
          }
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
          if (state.logContent) {
            this.appendLogs(state.logContent);
          }
          const isStreamingDone =
            state.status === TestStatus.TEST_STATUS_DONE && !state.hasMore;
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
   * @param status The current test status.
   * @return An observable emitting the resulting fetch state.
   */
  private fetchLogChunk(
    id: string,
    offset: number,
    status: TestStatus,
  ): Observable<FetchState> {
    return this.testService
      .getTestLog({
        testId: id,
        jobId: this.jobId(),
        offset,
        length: CHUNK_SIZE,
      })
      .pipe(
        map((logResp) => {
          return {
            offset: logResp.nextOffset,
            hasMore: logResp.hasMore,
            status,
            logContent: logResp.logContent || '',
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

      const rawLines = newLogs.split('\n');
      const hasPrefixMatch = this.trailingBuffer && lines.length > 0;
      if (hasPrefixMatch) {
        lines[lines.length - 1] += rawLines[0];
        rawLines.shift();
      }

      const endsWithNewline = newLogs.endsWith('\n');
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

  private scrollToBottom() {
    const viewport = this.logViewport();
    if (viewport) {
      setTimeout(() => {
        try {
          const el = viewport.nativeElement;
          el.scrollTop = el.scrollHeight;
        } catch {
          // Fallback if viewport measurement fails.
        }
      }, 100);
    }
  }
}
