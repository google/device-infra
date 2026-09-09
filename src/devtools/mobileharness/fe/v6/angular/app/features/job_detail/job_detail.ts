import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {MatDialog} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {of} from 'rxjs';
import {catchError, map, take} from 'rxjs/operators';
import {JOB_ACTION_UI_CONFIG} from '../../core/constants/action_bar_config';
import {APP_DATA, getLegacyFeUrl} from '../../core/models/app_data';
import {
  GetJobRequest,
  JobOverviewData,
  JobResult,
  JobStatus,
} from '../../core/models/job_overview';
import {JOB_SERVICE} from '../../core/services/job/job_service';
import {ActionButton} from '../../shared/components/action_button/action_button';
import {ConfirmDialog} from '../../shared/components/confirm_dialog/confirm_dialog';
import {KillJobConfirmContent} from '../../shared/components/kill_job_confirm_content/kill_job_confirm_content';
import {LegacyConsoleBanner} from '../../shared/components/legacy_console_banner/legacy_console_banner';
import {useCopyToClipboard} from '../../shared/composables/copy';
import {usePageTitle} from '../../shared/composables/page_title';
import {useSilentResource} from '../../shared/composables/silent_resource';
import {TooltipIfTruncatedDirective} from '../../shared/directives/tooltip_if_truncated/tooltip_if_truncated';
import {SnackBarService} from '../../shared/services/snackbar_service';
import {JobFilesTab} from './components/job_files_tab/job_files_tab';
import {JobLogTab} from './components/job_log_tab/job_log_tab';
import {JobOverviewTab} from './components/job_overview_tab/job_overview_tab';
import {JobTimelineTab} from './components/job_timeline_tab/job_timeline_tab';

import {JobPageData} from './models/job_page_ui';

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
    TooltipIfTruncatedDirective,
    LegacyConsoleBanner,
  ],
})
export class JobDetail {
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly jobService = inject(JOB_SERVICE);
  private readonly snackBar = inject(SnackBarService);
  private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly dialog = inject(MatDialog);
  private readonly appData = inject(APP_DATA);
  readonly legacyFeUrl = getLegacyFeUrl(this.appData.applicationId ?? '');
  readonly copyToClipboard = useCopyToClipboard();

  readonly jobActionUiConfig = JOB_ACTION_UI_CONFIG;

  readonly jobId = toSignal(
    this.activatedRoute.paramMap.pipe(map((params) => params.get('id'))),
    {initialValue: null},
  );

  readonly activeTab = signal<'overview' | 'timeline' | 'log' | 'files'>(
    'overview',
  );
  readonly copiedJobId = signal<boolean>(false);
  readonly copiedSessionId = signal<boolean>(false);

  private readonly silentResourceResult = useSilentResource<
    JobPageData,
    string | null
  >({
    params: () => this.jobId(),
    stream: (jobId) => {
      if (!jobId) {
        return of<JobPageData>({
          jobOverviewData: null,
          error: 'No job ID provided in the route.',
        });
      }

      const request: GetJobRequest = {jobId};

      return this.jobService.getJob(request).pipe(
        map(
          (response) =>
            ({
              jobOverviewData: response.job,
              actions: response.actions,
            }) as JobPageData,
        ),
        catchError((err) => {
          console.error(`Error fetching job ${jobId}:`, err);
          return of<JobPageData>({
            jobOverviewData: null,
            error: `Failed to load job data for ID: ${jobId}. ${err.message || ''}`,
          });
        }),
      );
    },
    onInitialLoad: (data) => {
      if (data?.jobOverviewData) {
        const job = data.jobOverviewData;
        this.activeTab.set(
          job.status === JobStatus.JOB_STATUS_RUNNING ? 'log' : 'overview',
        );
      }
    },
  });

  readonly jobResource = this.silentResourceResult.resource;

  readonly jobPageData = computed(() => this.jobResource.value() || null);

  readonly pageTitle = computed(() => {
    const id = this.jobPageData()?.jobOverviewData?.id;
    return id ? `OmniLab Console - Job ${id.substring(0, 8)}...` : null;
  });

  constructor() {
    usePageTitle(this.pageTitle);
  }

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
    if (
      !job ||
      job.status !== JobStatus.JOB_STATUS_DONE ||
      !job.result ||
      job.result === JobResult.JOB_RESULT_UNSPECIFIED
    ) {
      return null;
    }
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

  onLogStreamCompleted() {
    const job = this.job();
    if (job && job.status === JobStatus.JOB_STATUS_RUNNING) {
      this.silentResourceResult.reloadSilent();
    }
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
        contentComponent: KillJobConfirmContent,
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
    this.jobService.killJob(job.id).subscribe({
      next: () => {
        this.snackBar.showSuccess('Succeed to send kill job request to Master.');
        this.silentResourceResult.reloadSilent();
      },
      error: (err: unknown) => {
        console.error('Failed to kill job:', err);
        const e = err as {message?: string};
        this.snackBar.showError(e?.message || 'Failed to terminate job.');
      },
    });
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
