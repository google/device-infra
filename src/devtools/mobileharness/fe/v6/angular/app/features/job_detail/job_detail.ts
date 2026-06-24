import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  signal,
} from '@angular/core';
import {toObservable, toSignal} from '@angular/core/rxjs-interop';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {Title} from '@angular/platform-browser';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {LoadingService} from '@deviceinfra/app/shared/services/loading_service';
import {of} from 'rxjs';
import {catchError, map, switchMap, tap} from 'rxjs/operators';
import {JobOverviewData} from '../../core/models/job_overview';
import {JOB_SERVICE} from '../../core/services/job/job_service';
import {MOCK_JOB_SCENARIOS} from '../../core/services/mock_data';
import {NavLink} from '../../shared/components/nav_link/nav_link';
import {SnackBarService} from '../../shared/services/snackbar_service';
import {useCopyToClipboard} from '@deviceinfra/app/shared/composables/copy';
import {JobLogTab} from './components/job_log_tab/job_log_tab';
import {JobOverviewTab} from './components/job_overview_tab/job_overview_tab';
import {JobTimelineTab} from './components/job_timeline_tab/job_timeline_tab';

interface JobPageData {
  jobOverviewData: JobOverviewData | null;
  error?: string;
}

const CURRENT_VIEWER_LDAP = 'dafeni';

/**
 * Component for displaying the detailed information of a single job.
 * It fetches job data based on the ID from the route parameters and
 * presents different tabs for overview, timeline, and logs.
 */
@Component({
  selector: 'app-job-detail',
  standalone: true,
  templateUrl: './job_detail.ng.html',
  styleUrl: './job_detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatIconModule,
    MatTooltipModule,
    RouterModule,
    JobOverviewTab,
    JobTimelineTab,
    JobLogTab,
    NavLink,
  ],
})
export class JobDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly jobService = inject(JOB_SERVICE);
  private readonly titleService = inject(Title);
  private readonly loadingService = inject(LoadingService);
  private readonly snackBar = inject(SnackBarService);
  readonly copyToClipboard = useCopyToClipboard();
  private readonly destroyRef = inject(DestroyRef);

  readonly jobId = toSignal(
    this.route.paramMap.pipe(map((params) => params.get('id'))),
    {initialValue: null},
  );

  readonly activeTab = signal<'overview' | 'timeline' | 'log'>('overview');
  readonly showKillModal = signal<boolean>(false);
  private readonly reloadTrigger = signal<number>(0);

  constructor() {
    effect(() => {
      const id = this.jobId();
      if (id) {
        const shortId = id.substring(0, 8);
        this.titleService.setTitle(`OmniLab Console - Job ${shortId}...`);
      } else {
        this.titleService.setTitle('OmniLab Console');
      }
    });

    this.destroyRef.onDestroy(() => {
      this.titleService.setTitle('OmniLab Console');
    });
  }

  readonly jobPageData = toSignal(
    toObservable(this.reloadTrigger).pipe(
      switchMap(() => this.route.paramMap),
      map((params) => params.get('id')),
      tap(() => {
        this.loadingService.show();
      }),
      switchMap((id: string | null) => {
        if (!id) {
          this.loadingService.hide();
          return of<JobPageData>({
            jobOverviewData: null,
            error: 'No job ID provided in the route.',
          });
        }

        return this.jobService.getJob(id).pipe(
          map(
            (jobOverviewData) =>
              ({
                jobOverviewData,
              }) as JobPageData,
          ),
          catchError((err) => {
            console.error(`Error fetching job ${id}:`, err);
            return of<JobPageData>({
              jobOverviewData: null,
              error: `Failed to load job data for ID: ${id}. ${err.message || ''}`,
            });
          }),
          tap((data) => {
            this.loadingService.hide();
            if (data && data.jobOverviewData) {
              const job = data.jobOverviewData;
              if (job.status === 'RUNNING') {
                this.activeTab.set('log');
              } else {
                this.activeTab.set('overview');
              }
            }
          }),
        );
      }),
    ),
  );

  setActiveTab(tab: 'overview' | 'timeline' | 'log') {
    this.activeTab.set(tab);
  }

  scrollToJobConfig(event: Event) {
    event.preventDefault();
    this.activeTab.set('overview');
    setTimeout(() => {
      const element = document.getElementById('overview-config');
      if (element) {
        element.scrollIntoView({behavior: 'smooth', block: 'start'});
      }
    }, 100);
  }

  getStatusPillLabel(job: JobOverviewData): string {
    if (!job || !job.status) return 'UNKNOWN';
    return job.status.toUpperCase();
  }

  getResultPillLabel(job: JobOverviewData): string | null {
    if (!job || !job.result) return null;
    return job.result.toUpperCase();
  }

  isKillable(job: JobOverviewData): boolean {
    if (!job) return false;
    return (
      job.status === 'RUNNING' ||
      job.status === 'NEW' ||
      job.status === 'ASSIGNED'
    );
  }

  isMultiDevice(job: JobOverviewData): boolean {
    return !!(
      job &&
      job.config &&
      job.config.devices &&
      job.config.devices.length > 0
    );
  }

  isOwner(job: JobOverviewData): boolean {
    if (!job) return false;
    return job.user === CURRENT_VIEWER_LDAP;
  }

  getKillTooltip(job: JobOverviewData): string {
    if (this.isOwner(job)) {
      return 'Click to terminate this running job immediately.';
    }
    return `Permission denied. Only the owner (${job.user}) or members of mdb/mobileharness can kill this job.`;
  }

  confirmKillJob(job: JobOverviewData) {
    // Find job in mockData and update it
    const matchedScenario = MOCK_JOB_SCENARIOS.find(
      (s) => s.overview.id === job.id,
    );
    if (matchedScenario) {
      const mockJob = matchedScenario.overview;
      mockJob.status = 'DONE';
      mockJob.result = 'ABORT';

      mockJob.endTime = new Date().toISOString();
      mockJob.properties['aborted_by'] = CURRENT_VIEWER_LDAP;

      // Abort running child tests
      if (mockJob.tests && mockJob.tests.length > 0) {
        mockJob.tests = mockJob.tests.map((test) => {
          if (test.status === 'In Progress') {
            return {
              ...test,
              status: 'Failed', // or aborted representation
              duration: 'N/A',
            };
          }
          return test;
        });
      }

      this.snackBar.showSuccess('Job aborted successfully.');
      this.showKillModal.set(false);
      // Re-trigger signals subscription to reload mock data
      this.reloadTrigger.update((n) => n + 1);
    } else {
      this.snackBar.showError('Could not find job scenario to kill.');
      this.showKillModal.set(false);
    }
  }
}
