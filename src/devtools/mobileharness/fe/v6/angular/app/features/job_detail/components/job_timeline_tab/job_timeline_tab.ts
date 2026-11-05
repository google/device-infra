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
import {TimeBreakdown} from '../../../../core/models/job_overview';
import {JOB_SERVICE} from '../../../../core/services/job/job_service';
import {TimelineChart} from '../../../../shared/components/timeline_chart/timeline_chart';

/** Component for rendering the job timeline tab content. */
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

  private readonly jobService = inject(JOB_SERVICE);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly timingBreakdown = signal<TimeBreakdown | undefined>(undefined);

  private loadedJobId = '';

  private readonly fetchTrigger = computed(() => ({
    jobId: this.jobId(),
    status: this.status(),
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
          const isSameJob =
            jobId === this.loadedJobId && this.timingBreakdown() !== undefined;
          if (!isSameJob) {
            this.timingBreakdown.set(undefined);
            this.loading.set(true);
          }
          return this.jobService.getJobTimeline({jobId}).pipe(
            map((resp) => {
              this.loadedJobId = jobId;
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
        this.timingBreakdown.set(timeline);
        this.loading.set(false);
      });
  }
}
