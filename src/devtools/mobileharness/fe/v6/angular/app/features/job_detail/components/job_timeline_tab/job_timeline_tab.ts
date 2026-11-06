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
import {TimeBreakdown} from '../../../../core/models/job_overview';
import {JOB_SERVICE} from '../../../../core/services/job/job_service';
import {TimelineChart} from '../../../../shared/components/timeline_chart/timeline_chart';
import {useLiveRefresh} from '../../../../shared/composables/live_refresh';

/**
 * Component for rendering the job timeline tab content. While the job runs, it
 * fetches the timeline again every refresh interval for as long as the tab is
 * `active`, and emits `finished` once the timeline says the job is over.
 */
@Component({
  selector: 'app-job-timeline-tab',
  standalone: true,
  imports: [TimelineChart],
  templateUrl: './job_timeline_tab.ng.html',
  styleUrl: './job_timeline_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JobTimelineTab {
  readonly jobId = input<string>('');
  readonly status = input<string | undefined>(undefined);
  /** Whether the tab is showing; a hidden tab does not refresh. */
  readonly active = input<boolean>(true);
  /** Emitted when a live timeline comes back final, once the job is over. */
  readonly finished = output<void>();

  private readonly jobService = inject(JOB_SERVICE);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly timingBreakdown = signal<TimeBreakdown | undefined>(undefined);

  private readonly liveRefresh = useLiveRefresh(
    computed(() => Boolean(this.timingBreakdown()?.live)),
    this.active,
    'job',
  );
  private readonly refreshes = this.liveRefresh.refreshes;

  /** Tells the reader that a live timeline keeps itself current. */
  readonly liveNote = this.liveRefresh.liveNote;

  private loadedJobId = '';

  private readonly fetchTrigger = computed(() => ({
    jobId: this.jobId(),
    status: this.status(),
    refreshes: this.refreshes(),
  }));

  constructor() {
    toObservable(this.fetchTrigger)
      .pipe(
        switchMap(({jobId}) => {
          this.errorMessage.set(null);
          if (!jobId) {
            this.loadedJobId = '';
            this.timingBreakdown.set(undefined);
            this.loading.set(false);
            return of(undefined);
          }
          const current = this.timingBreakdown();
          const isSameJob = jobId === this.loadedJobId && current !== undefined;
          if (isSameJob && !current.live && Boolean(current.jobTimeline)) {
            return of(current);
          }
          if (!isSameJob) {
            this.timingBreakdown.set(undefined);
            this.loading.set(true);
          }
          return this.jobService.getJobTimeline({jobId}).pipe(
            map((resp) => {
              this.loadedJobId = jobId;
              this.liveRefresh.markRefreshed();
              return resp.timeline;
            }),
            catchError((err) => {
              console.error(`Error fetching timeline for job ${jobId}:`, err);
              if (isSameJob) {
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
