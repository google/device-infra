import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import {takeUntilDestroyed, toObservable} from '@angular/core/rxjs-interop';
import {of} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';
import {
  GetTestTimelineRequest,
  TimeBreakdown,
} from '../../../../core/models/test_overview';
import {TEST_SERVICE} from '../../../../core/services/test/test_service';
import {TimelineChart} from '../../../../shared/components/timeline_chart/timeline_chart';
import {useLiveRefresh} from '../../../../shared/composables/live_refresh';

/**
 * Component for rendering the test timeline tab content. While the test runs,
 * it fetches the timeline again every refresh interval for as long as the tab
 * is `active`, and emits `finished` once the timeline says the test is over.
 */
@Component({
  selector: 'app-test-timeline-tab',
  standalone: true,
  imports: [TimelineChart],
  templateUrl: './test_timeline_tab.ng.html',
  styleUrl: './test_timeline_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TestTimelineTab {
  readonly testId = input<string>('');
  readonly jobId = input<string>('');
  readonly status = input<string | undefined>(undefined);
  /** Whether the tab is showing; a hidden tab does not refresh. */
  readonly active = input<boolean>(true);
  /** Emitted when a live timeline comes back final, once the test is over. */
  readonly finished = output<void>();

  private readonly testService = inject(TEST_SERVICE);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly timingBreakdown = signal<TimeBreakdown | undefined>(undefined);

  private readonly liveRefresh = useLiveRefresh(
    computed(() => Boolean(this.timingBreakdown()?.live)),
    this.active,
    'test',
  );
  private readonly refreshes = this.liveRefresh.refreshes;

  /** Tells the reader that a live timeline keeps itself current. */
  readonly liveNote = this.liveRefresh.liveNote;

  private loadedEntityKey = '';

  private readonly fetchTrigger = computed<{
    request: GetTestTimelineRequest;
    status: string | undefined;
    refreshes: number;
  } | null>(() => {
    const testId = this.testId();
    if (!testId) return null;
    return {
      request: {
        testId,
        jobId: this.jobId(),
      },
      status: this.status(),
      refreshes: this.refreshes(),
    };
  });

  constructor() {
    toObservable(this.fetchTrigger)
      .pipe(
        switchMap((trigger) => {
          this.errorMessage.set(null);
          if (!trigger) {
            this.loadedEntityKey = '';
            this.timingBreakdown.set(undefined);
            this.loading.set(false);
            return of(undefined);
          }
          const params = trigger.request;
          const entityKey = `${params.testId}|${params.jobId || ''}`;
          const current = this.timingBreakdown();
          const isSameEntity =
            entityKey === this.loadedEntityKey && current !== undefined;
          if (isSameEntity && !current.live && Boolean(current.testTimeline)) {
            return of(current);
          }
          if (!isSameEntity) {
            this.timingBreakdown.set(undefined);
            this.loading.set(true);
          }
          return this.testService.getTestTimeline(params).pipe(
            map((resp) => {
              this.loadedEntityKey = entityKey;
              this.liveRefresh.markRefreshed();
              return resp.timeline;
            }),
            catchError((err) => {
              console.error(
                `Error fetching timeline for test ${params.testId}:`,
                err,
              );
              if (isSameEntity) {
                return of(this.timingBreakdown());
              }
              this.errorMessage.set('Failed to load timeline.');
              return of(undefined);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((timeline) => {
        const wasLive = Boolean(this.timingBreakdown()?.live);
        this.timingBreakdown.set(timeline);
        this.loading.set(false);
        if (wasLive && timeline && !timeline.live) {
          this.finished.emit();
        }
      });
  }
}
