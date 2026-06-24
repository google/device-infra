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
  ViewChild,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {EMPTY, Observable, timer} from 'rxjs';
import {catchError, concatMap, expand, map} from 'rxjs/operators';

import {TestStatus} from '../../../../core/models/test_overview';
import {TEST_SERVICE} from '../../../../core/services/test/test_service';

/**
 * Internal state representing the outcome of a log chunk fetch operation.
 */
interface FetchState {
  /** The byte offset representing the end of the downloaded log chunk. */
  offset: number;
  /** Boolean indicating whether additional log chunks are immediately available on the server. */
  hasMore: boolean;
  /** The current execution status of the test being monitored. */
  status: TestStatus;
  /** The raw textual log content downloaded in this specific chunk. */
  logContent: string;
}

const POLLING_INTERVAL_MS = 2000;
const CHUNK_SIZE = 100000;

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

  private readonly testService = inject(TEST_SERVICE);
  private readonly destroyRef = inject(DestroyRef);

  /** Signal holding the array of log lines currently rendered in the virtual scroll viewport. */
  readonly logLines = signal<string[]>(['Loading logs...']);
  /** Signal holding the external cloud log explorer URL for this test. */
  readonly cloudLogLink = signal<string>('');

  /** Computed absolute height of the virtual scroll viewport constrained to a maximum of 552px. */
  readonly viewportHeight = computed(() => {
    const lineCount = this.logLines().length;
    return Math.min(552, lineCount * 20);
  });

  /** Reference to the virtual scroll viewport directive. */
  @ViewChild(CdkVirtualScrollViewport)
  private readonly viewport!: CdkVirtualScrollViewport;

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

    const initialFetch$: Observable<FetchState> = this.testService
      .getTest(id)
      .pipe(
        concatMap((testDetail) => {
          return this.fetchLogChunk(
            id,
            0,
            testDetail.status,
            /* isInitial= */ true,
          );
        }),
      );

    initialFetch$
      .pipe(
        expand((state: FetchState): Observable<FetchState> => {
          if (state.status === 'DONE' && !state.hasMore) {
            return EMPTY;
          }

          if (state.hasMore) {
            return this.fetchLogChunk(id, state.offset, state.status);
          } else {
            return timer(POLLING_INTERVAL_MS).pipe(
              concatMap(() => this.testService.getTest(id)),
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
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (state: FetchState) => {
          if (state.logContent) {
            this.appendLogs(state.logContent);
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
   * @param isInitial Flag indicating whether this is the very first log fetch request.
   * @return An observable emitting the resulting fetch state.
   */
  private fetchLogChunk(
    id: string,
    offset: number,
    status: TestStatus,
    isInitial = false,
  ): Observable<FetchState> {
    return this.testService.getTestLog(id, offset, CHUNK_SIZE).pipe(
      map((logResp) => {
        if (isInitial && logResp.cloudLogLink) {
          this.cloudLogLink.set(logResp.cloudLogLink);
        }
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
   * Appends newly downloaded log content to the existing log lines signal array.
   *
   * @param newLogs The raw newline-delimited log string to append.
   */
  private appendLogs(newLogs: string) {
    this.logLines.update((current) => {
      const lines =
        current.length === 1 && current[0] === 'Loading logs...' ? [] : current;
      const newLines = newLogs.split('\n');
      return [...lines, ...newLines];
    });
  }

  /**
   * Automatically scrolls the virtual scroll viewport to the absolute bottom
   * if the user is currently positioned near the bottom of the log stream.
   */
  private scrollToBottom() {
    if (this.viewport) {
      setTimeout(() => {
        this.viewport.checkViewportSize();
        const totalItems = this.logLines().length;
        if (totalItems > 0) {
          const offset = this.viewport.measureScrollOffset('bottom');
          if (offset < 150) {
            this.viewport.scrollToIndex(totalItems - 1);
          }
        }
      }, 0);
    }
  }

  /**
   * Tracking function for virtual scroll loop performance optimization.
   *
   * @param index The current array index.
   * @return The numeric index tracking reference.
   */
  trackByFn(index: number) {
    return index;
  }
}
