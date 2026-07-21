import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import {toObservable, toSignal} from '@angular/core/rxjs-interop';
import {MatDialog} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {Title} from '@angular/platform-browser';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {of} from 'rxjs';
import {catchError, map, switchMap, take, tap} from 'rxjs/operators';
import {JOB_ACTION_UI_CONFIG} from '../../core/constants/action_bar_config';
import {
  JobActions,
  JobOverviewData,
  JobStatus,
} from '../../core/models/job_overview';
import {JOB_SERVICE} from '../../core/services/job/job_service';
import {ActionButton} from '../../shared/components/action_button/action_button';
import {ConfirmDialog} from '../../shared/components/confirm_dialog/confirm_dialog';
import {useCopyToClipboard} from '../../shared/composables/copy';
import {LoadingService} from '../../shared/services/loading_service';
import {SnackBarService} from '../../shared/services/snackbar_service';
import {JobFilesTab} from './components/job_files_tab/job_files_tab';
import {JobLogTab} from './components/job_log_tab/job_log_tab';
import {JobOverviewTab} from './components/job_overview_tab/job_overview_tab';
import {JobTimelineTab} from './components/job_timeline_tab/job_timeline_tab';

interface JobPageData {
  jobOverviewData: JobOverviewData | null;
  actions?: JobActions;
  error?: string;
}

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
    JobFilesTab,
    ActionButton,
  ],
})
export class JobDetail {
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly jobService = inject(JOB_SERVICE);
  private readonly snackBar = inject(SnackBarService);
  private readonly loadingService = inject(LoadingService);
  private readonly titleService = inject(Title);
  private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly dialog = inject(MatDialog);
  readonly copyToClipboard = useCopyToClipboard();

  readonly jobActionUiConfig = JOB_ACTION_UI_CONFIG;

  readonly activeTab = signal<'overview' | 'timeline' | 'log' | 'files'>(
    'overview',
  );
  readonly reloadTrigger = signal<number>(0);
  readonly copiedJobId = signal<boolean>(false);
  readonly copiedSessionId = signal<boolean>(false);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    effect(() => {
      const data = this.jobPageData();
      if (!data?.jobOverviewData) {
        this.titleService.setTitle('OmniLab Console');
        return;
      }
      const id = data.jobOverviewData.id;
      const shortId = id.substring(0, 8);
      this.titleService.setTitle(`OmniLab Console - Job ${shortId}...`);
    });

    this.destroyRef.onDestroy(() => {
      this.titleService.setTitle('OmniLab Console');
    });
  }

  readonly jobPageData = toSignal(
    toObservable(this.reloadTrigger).pipe(
      switchMap(() => this.activatedRoute.paramMap),
      map((params) => params.get('id')),
      switchMap((id) => {
        this.loadingService.show();
        if (!id) {
          this.loadingService.hide();
          return of<JobPageData>({
            jobOverviewData: null,
            error: 'No job ID provided in the route.',
          });
        }

        return this.jobService.getJob(id).pipe(
          map(
            (response) =>
              ({
                jobOverviewData: response.job,
                actions: response.actions,
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
            if (!data?.jobOverviewData) return;
            this.activeTab.set(
              data.jobOverviewData.status === JobStatus.JOB_STATUS_RUNNING
                ? 'log'
                : 'overview',
            );
          }),
        );
      }),
    ),
  );

  readonly job = computed(() => this.jobPageData()?.jobOverviewData || null);
  readonly actions = computed(() => this.jobPageData()?.actions || null);
  readonly errorMessage = computed(() => this.jobPageData()?.error || null);

  readonly statusPillLabel = computed(() => {
    const job = this.job();
    if (!job || !job.status) return 'UNKNOWN';
    return job.status.replace('JOB_STATUS_', '');
  });

  readonly resultPillLabel = computed(() => {
    const job = this.job();
    if (!job || !job.result) return null;
    return job.result.replace('JOB_RESULT_', '');
  });

  readonly isMultiDevice = computed(() => {
    const job = this.job();
    return !!(
      job &&
      job.config &&
      job.config.devices?.device &&
      job.config.devices.device.length > 1
    );
  });

  readonly killActionState = computed(() => this.actions()?.kill || null);

  setActiveTab(tab: 'overview' | 'timeline' | 'log' | 'files') {
    this.activeTab.set(tab);
  }

  scrollToJobConfig(event: Event) {
    event.preventDefault();
    this.activeTab.set('overview');
    setTimeout(() => {
      const element =
        this.elementRef.nativeElement.querySelector('#overview-config');
      if (element) {
        element.scrollIntoView({behavior: 'smooth', block: 'start'});
      }
    }, 100);
  }

  readonly onKillJobClicked = (job: JobOverviewData) => {
    const dialogRef = this.dialog.open(ConfirmDialog, {
      panelClass: 'confirm-dialog-panel',
      data: {
        title: 'Kill Job?',
        content: `Are you sure you want to terminate job ${job.id} This action will abort all running child tests immediately and cannot be undone.`,
        type: 'error',
        primaryButtonLabel: 'Kill Job',
        secondaryButtonLabel: 'Cancel',
      },
      disableClose: true,
    });

    dialogRef
      .afterClosed()
      .pipe(take(1))
      .subscribe((result) => {
        if (result === 'primary') {
          this.executeKillJob(job);
        }
      });
  };

  private executeKillJob(job: JobOverviewData) {
    // TODO: Use real job service to kill the job.
    this.snackBar.showSuccess('Job aborted successfully.');
    // Re-trigger signals subscription to reload mock data
    this.reloadTrigger.update((n) => n + 1);
  }

  copyJobId(id: string) {
    this.copyToClipboard(id, 'Job ID copied to clipboard!');
    this.copiedJobId.set(true);
    setTimeout(() => {
      this.copiedJobId.set(false);
    }, 2000);
  }

  copySessionId(id: string) {
    this.copyToClipboard(id, 'Session ID copied to clipboard!');
    this.copiedSessionId.set(true);
    setTimeout(() => {
      this.copiedSessionId.set(false);
    }, 2000);
  }
}
