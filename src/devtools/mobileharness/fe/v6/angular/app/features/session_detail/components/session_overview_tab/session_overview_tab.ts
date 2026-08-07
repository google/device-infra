import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Signal,
  computed,
  input,
  signal,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatTableModule} from '@angular/material/table';
import {MatTooltipModule} from '@angular/material/tooltip';
import {RouterModule} from '@angular/router';

import {JobResult} from '../../../../core/models/common_models';
import {
  SessionDetail,
  SessionJob,
} from '../../../../core/models/session_overview';
import {AccordionItem} from '../../../../shared/components/accordion_item/accordion_item';
import {InfoCard} from '../../../../shared/components/info_card/info_card';
import {
  MasterDetailLayout,
  NavItem,
} from '../../../../shared/components/master_detail_layout/master_detail_layout';
import {useCopyToClipboard} from '../../../../shared/composables/copy';
import {
  FilterEntry,
  createSearchFilter,
} from '../../../../shared/composables/search_filter';
import {
  getJobResultBadge,
  getJobStatusBadgeOnly,
} from '../../../../shared/composables/status_badge';
import {
  STANDARD_TIMESTAMP_KEYS,
  createTimestampInfoMap,
} from '../../../../shared/composables/timestamp_info';
import {dateUtils} from '../../../../shared/utils/date_utils';

/** Component for rendering the session overview tab content. */
@Component({
  selector: 'app-session-overview-tab',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatTableModule,
    MatTooltipModule,
    RouterModule,
    InfoCard,
    MasterDetailLayout,
    AccordionItem,
  ],
  templateUrl: './session_overview_tab.ng.html',
  styleUrl: './session_overview_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionOverviewTab {
  readonly session = input.required<SessionDetail>();

  readonly displayedColumns = [
    'id',
    'name',
    'status',
    'result',
    'startTime',
    'endTime',
  ];

  readonly copyToClipboard = useCopyToClipboard();
  readonly copiedJobIds = signal<Record<string, boolean>>({});

  private readonly propertiesFilter = createSearchFilter<string>(
    computed(() => this.session().properties),
  );
  readonly propertiesSearchTerm = this.propertiesFilter.searchTerm;
  readonly hasProperties = this.propertiesFilter.hasData;
  readonly filteredProperties: Signal<Array<FilterEntry<string>>> =
    this.propertiesFilter.filteredData;

  readonly timestampKeys = STANDARD_TIMESTAMP_KEYS;
  private readonly executionDetails = computed(
    () => this.session().executionDetails,
  );
  readonly timestampInfoMap = createTimestampInfoMap(
    this.executionDetails,
    this.timestampKeys,
  );

  readonly overviewNavList = computed(() => {
    const session = this.session();
    const list: NavItem[] = [];
    if (
      session.troubleshooting?.resultCause?.error != null &&
      session.troubleshooting.resultCause.error.length > 0
    ) {
      list.push({id: 'overview-error', label: 'Error Details'});
    }
    if (
      session.troubleshooting?.warnings?.warning != null &&
      session.troubleshooting.warnings.warning.length > 0
    ) {
      list.push({id: 'overview-warning', label: 'Warning Details'});
    }
    const jobs = session.jobs;
    if (jobs && jobs.length > 0) {
      list.push({id: 'session-jobs', label: 'Child Jobs'});
    }
    list.push({id: 'overview-execution', label: 'Execution Details'});
    list.push({id: 'overview-properties', label: 'Session Properties'});
    return list;
  });

  getJobStatusBadge(job: SessionJob) {
    return getJobStatusBadgeOnly(job.status);
  }

  getJobResultBadge(job: SessionJob) {
    if (!job.result || job.result === JobResult.JOB_RESULT_UNSPECIFIED) {
      return null;
    }
    return getJobResultBadge(job.result);
  }

  getJobStartTime(job: SessionJob): string {
    if (!job.startTime) return 'N/A';
    const date = dateUtils.parseUtcTimestamp(job.startTime);
    if (!date || isNaN(date.getTime())) {
      const jsDate = new Date(job.startTime);
      if (isNaN(jsDate.getTime())) return job.startTime || 'N/A';
      return dateUtils.formatDetailedLocal(jsDate);
    }
    return dateUtils.formatDetailedLocal(date);
  }

  getJobEndTime(job: SessionJob): string {
    if (!job.endTime) return 'N/A';
    const date = dateUtils.parseUtcTimestamp(job.endTime);
    if (!date || isNaN(date.getTime())) {
      const jsDate = new Date(job.endTime);
      if (isNaN(jsDate.getTime())) return job.endTime || 'N/A';
      return dateUtils.formatDetailedLocal(jsDate);
    }
    return dateUtils.formatDetailedLocal(date);
  }

  copyJobId(id: string) {
    this.copyToClipboard(id, 'Job ID copied to clipboard!');
    this.copiedJobIds.update((prev) => ({...prev, [id]: true}));
    setTimeout(() => {
      this.copiedJobIds.update((prev) => ({...prev, [id]: false}));
    }, 2000);
  }

  getSessionDuration(): string {
    const details = this.session().executionDetails;
    if (!details || !details.startTime) return '-';
    const start = new Date(details.startTime).getTime();
    const end = details.endTime
      ? new Date(details.endTime).getTime()
      : Date.now();
    const diffMs = end - start;
    if (diffMs < 0) return '0s';
    const diffSecs = Math.floor(diffMs / 1000);
    const secs = diffSecs % 60;
    const mins = Math.floor(diffSecs / 60) % 60;
    const hours = Math.floor(diffSecs / 3600);
    const parts = [];
    if (hours > 0) parts.push(`${hours}h`);
    if (mins > 0 || hours > 0) parts.push(`${mins}m`);
    parts.push(`${secs}s`);
    return parts.join(' ');
  }
}
