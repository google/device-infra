import {
  CdkVirtualScrollViewport,
  ScrollingModule,
} from '@angular/cdk/scrolling';
import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {EMPTY, Observable, timer} from 'rxjs';
import {catchError, concatMap, expand, finalize} from 'rxjs/operators';

/** Result interface for a chunk of streaming log output. */
export interface LogChunkResult {
  offset: number;
  content: string;
  isDone: boolean;
  reset?: boolean;
  hash?: string;
}

/** Function type strategy for fetching streaming log chunks. */
export type LogFetchStrategy = (
  offset: number,
  hash?: string,
) => Observable<LogChunkResult>;

const POLLING_INTERVAL_MS = 5000;

/** Generic reusable streaming log viewer component with virtual scrolling. */
@Component({
  selector: 'app-streaming-log-viewer',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatTooltipModule, ScrollingModule],
  templateUrl: './streaming_log_viewer.ng.html',
  styleUrl: './streaming_log_viewer.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamingLogViewerComponent implements OnInit {
  /** The unique entity ID (session, job, test, etc.). */
  readonly id = input.required<string>();
  /** Display title for the log card header. */
  readonly logTitle = input<string>('Console Output');
  /** Initial cloud log link if externally known. */
  readonly cloudLogLink = input<string>('');
  /** Text label for the external cloud logging link. */
  readonly cloudLogLinkText = input<string>(
    'Lab Server Logs on Cloud Logs Explorer',
  );
  /** The callback strategy used to fetch streaming log chunks. */
  readonly fetchStrategy = input.required<LogFetchStrategy>();
  /** Custom empty state message when no logs are returned. */
  readonly emptyMessage = input<string>('No logs available.');
  /** Initial scroll position behavior: 'top' (default) or 'bottom' (for live running logs). */
  readonly initialScrollPosition = input<'bottom' | 'top'>('top');
  /** Emitted when log streaming completes (i.e. isDone is returned as true). */
  readonly streamCompleted = output<void>();

  private readonly destroyRef = inject(DestroyRef);

  readonly logLines = signal<string[]>(['Loading logs...']);
  private trailingBuffer = '';

  readonly viewportHeight = computed(() => {
    const lines = this.logLines();
    const isSpecialMsg =
      lines.length <= 1 &&
      (lines[0] === 'Loading logs...' || lines[0] === this.emptyMessage());
    if (isSpecialMsg) {
      return 20;
    }
    let totalVisualRows = 0;
    for (const line of lines) {
      totalVisualRows += Math.max(1, Math.ceil(line.length / 80));
    }
    return Math.min(552, Math.max(100, totalVisualRows * 20));
  });

  readonly logViewport = viewChild<CdkVirtualScrollViewport>('logViewport');

  constructor() {
    effect(() => {
      this.logLines();
      this.scrollToPosition();
    });
  }

  ngOnInit() {
    this.startLiveLogStreaming();
  }

  readonly isFetching = signal<boolean>(false);

  readonly hasLoadedLogs = computed(() => {
    const lines = this.logLines();
    return (
      lines.length > 0 &&
      !(
        lines.length === 1 &&
        (lines[0] === 'Loading logs...' || lines[0] === this.emptyMessage())
      )
    );
  });

  private startLiveLogStreaming() {
    const strategy = this.fetchStrategy();
    if (!strategy) {
      this.isFetching.set(false);
      return;
    }

    this.isFetching.set(true);
    strategy(0)
      .pipe(
        expand((state: LogChunkResult): Observable<LogChunkResult> => {
          if (state.isDone) {
            this.isFetching.set(false);
            return EMPTY;
          }
          return timer(POLLING_INTERVAL_MS).pipe(
            concatMap(() => this.fetchStrategy()(state.offset, state.hash)),
          );
        }),
        catchError((err) => {
          this.isFetching.set(false);
          console.error('Error fetching dynamic streaming logs:', err);
          return EMPTY;
        }),
        finalize(() => {
          this.isFetching.set(false);
          const currentLines = this.logLines();
          if (
            currentLines.length === 0 ||
            (currentLines.length === 1 && currentLines[0] === 'Loading logs...')
          ) {
            this.logLines.set([this.emptyMessage()]);
          }
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (state: LogChunkResult) => {
          if (state.reset) {
            this.logLines.set([]);
            this.trailingBuffer = '';
          }
          if (state.content) {
            this.appendLogs(state.content);
          }
          const currentLines = this.logLines();
          const remainsLoading =
            currentLines.length === 1 && currentLines[0] === 'Loading logs...';
          if (state.isDone) {
            this.isFetching.set(false);
            if (remainsLoading) {
              this.logLines.set([this.emptyMessage()]);
            }
            this.streamCompleted.emit();
          }
        },
      });
  }

  private appendLogs(newLogs: string) {
    if (!newLogs) {
      return;
    }
    this.logLines.update((current) => {
      const lines =
        current.length === 1 &&
        (current[0] === 'Loading logs...' || current[0] === this.emptyMessage())
          ? []
          : [...current];

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

  private scrollToPosition() {
    const viewport = this.logViewport();
    if (viewport) {
      requestAnimationFrame(() => {
        try {
          viewport.checkViewportSize();
          if (this.initialScrollPosition() === 'top') {
            viewport.scrollTo({top: 0});
          } else {
            viewport.scrollTo({bottom: 0});
          }
        } catch {
          // Fallback if viewport measurement fails.
        }
      });
    }
  }
}
