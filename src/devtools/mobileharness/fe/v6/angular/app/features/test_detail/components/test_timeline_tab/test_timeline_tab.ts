import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
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
  readonly testId = input<string>('');
  readonly subTestId = input<string | undefined>(undefined);
  readonly jobId = input<string>('');
  readonly status = input<string | undefined>(undefined);
  readonly result = input<string | undefined>(undefined);

  private readonly testService = inject(TEST_SERVICE);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly timingBreakdown = signal<TimeBreakdown | undefined>(undefined);

  private loadedEntityKey = '';

  private readonly fetchTrigger = computed<{
    request: GetTestTimelineRequest;
    status: string | undefined;
    result: string | undefined;
  } | null>(() => {
    const testId = this.testId();
    if (!testId) return null;
    const subTestId = this.subTestId();
    return {
      request: {
        testId,
        ...(subTestId ? {subTestId} : {}),
        jobId: this.jobId(),
      },
      status: this.status(),
      result: this.result(),
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
          const entityKey = `${params.testId}|${params.subTestId || ''}|${params.jobId || ''}`;
          const isSameEntity =
            entityKey === this.loadedEntityKey &&
            this.timingBreakdown() !== undefined;
          if (!isSameEntity) {
            this.timingBreakdown.set(undefined);
            this.loading.set(true);
          }
          return this.testService.getTestTimeline(params).pipe(
            map((resp) => {
              this.loadedEntityKey = entityKey;
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
        this.timingBreakdown.set(timeline);
        this.loading.set(false);
      });
  }
}
