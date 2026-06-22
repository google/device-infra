import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, computed, input, Signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatTableModule} from '@angular/material/table';
import {MatTooltipModule} from '@angular/material/tooltip';
import {RouterModule} from '@angular/router';

import {JobOverviewData} from '../../../../core/models/job_overview';
import {useCopyToClipboard} from '@deviceinfra/app/shared/composables/copy';
import {FilterEntry, createSearchFilter} from '@deviceinfra/app/shared/composables/search_filter';
import {InfoCard} from '../../../../shared/components/info_card/info_card';
import {
  MasterDetailLayout,
  NavItem,
} from '../../../../shared/components/master_detail_layout/master_detail_layout';
import {NavLink} from '../../../../shared/components/nav_link/nav_link';
import {dateUtils} from '../../../../shared/utils/date_utils';

/** Component for rendering the job overview tab content. */
@Component({
  selector: 'app-job-overview-tab',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatTableModule,
    MatTooltipModule,
    RouterModule,
    InfoCard,
    MasterDetailLayout,
    NavLink,
  ],
  templateUrl: './job_overview_tab.ng.html',
  styleUrl: './job_overview_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JobOverviewTab {
  readonly job = input.required<JobOverviewData>();

  readonly displayedColumns = ['id', 'name', 'status', 'duration', 'device'];

  readonly copyToClipboard = useCopyToClipboard();

  private readonly propertiesFilter = createSearchFilter<string>(
    computed(() => this.job().properties),
  );
  readonly propertiesSearchTerm = this.propertiesFilter.searchTerm;
  readonly hasProperties = this.propertiesFilter.hasData;
  readonly filteredProperties: Signal<Array<FilterEntry<string>>> = this.propertiesFilter.filteredData;
  readonly timestampKeys = [
    {key: 'createTime', label: 'Create Time'},
    {key: 'startTime', label: 'Start Time'},
    {key: 'endTime', label: 'End Time'},
  ] as const;

  readonly overviewNavList = computed(() => {
    const job = this.job();
    const list: NavItem[] = [];
    if (job.error) {
      list.push({id: 'overview-error', label: 'Error Details'});
    }
    if (job.warnings) {
      list.push({id: 'overview-warning', label: 'Warning Details'});
    }
    if (job.tests && job.tests.length > 0) {
      list.push({id: 'job-tests', label: 'Child Tests'});
    }
    list.push({id: 'overview-execution', label: 'Execution Details'});
    list.push({id: 'overview-config', label: 'Job Configuration'});
    list.push({id: 'overview-properties', label: 'Job Properties'});
    return list;
  });

  // properties-filtering logic handled by usePropertiesFilter composable

  readonly timestampInfoMap = computed(() => {
    const job = this.job();
    const result: Record<string, ReturnType<typeof this.getTimestampInfo>> = {};
    for (const item of this.timestampKeys) {
      result[item.key] = this.getTimestampInfo(item.key, job);
    }
    return result;
  });

  readonly isMultiDevice = computed(() => {
    const config = this.job().config;
    return !!(config.devices && config.devices.length > 0);
  });

  readonly jobSettingsMap = computed(() => {
    const job = this.job();
    const core = job.config.core || {};
    const retry = job.config.retry || {};
    const settings: Record<string, string> = {};
    if (core['Job Priority']) {
      settings['Job Priority'] = String(core['Job Priority']);
    }
    if (core['Total Test Count'] !== undefined) {
      settings['Total Test Count'] = String(core['Total Test Count']);
    }
    for (const [key, val] of Object.entries(retry)) {
      if (
        typeof val === 'string' ||
        typeof val === 'number' ||
        typeof val === 'boolean'
      ) {
        settings[key] = String(val);
      }
    }
    return settings;
  });

  readonly hasJobSettings = computed(() => {
    return Object.keys(this.jobSettingsMap()).length > 0;
  });

  readonly hasJobParams = computed(() => {
    const params = this.job().config.params;
    return params && Object.keys(params).length > 0;
  });

  readonly hasDimensions = computed(() => {
    const dims = this.job().config.dimensions;
    return dims && Object.keys(dims).length > 0;
  });

  readonly deviceRequirementsBasic = computed(() => {
    const core = this.job().config.core || {};
    const basic: Record<string, string> = {};
    if (core['Device Type']) basic['Device Type'] = String(core['Device Type']);
    if (core['Driver']) basic['Driver'] = String(core['Driver']);
    if (core['Decorator(s)']) basic['Decorator(s)'] = String(core['Decorator(s)']);
    return basic;
  });

  isTestRunning(testStatus: string): boolean {
    return testStatus === 'In Progress' || testStatus === 'Running';
  }

  getTestStatusDisplayText(testStatus: string): string {
    return this.isTestRunning(testStatus) ? 'Running' : testStatus;
  }

  getTestStatusPillClass(testStatus: string): string {
    if (this.isTestRunning(testStatus)) {
      return 'bg-blue-50 text-blue-600';
    } else if (testStatus === 'Passed') {
      return 'bg-green-50 text-green-600';
    } else if (testStatus === 'Failed') {
      return 'bg-red-50 text-red-600';
    }
    return 'bg-gray-50 text-gray-500';
  }

  getTestStatusIcon(testStatus: string): string {
    if (this.isTestRunning(testStatus)) return 'sync';
    if (testStatus === 'Passed') return 'check_circle';
    if (testStatus === 'Failed') return 'error';
    return 'help_outline';
  }

  getTimestampInfo(
    key: 'createTime' | 'startTime' | 'endTime',
    job: JobOverviewData,
  ) {
    let rawValue: string | undefined;
    if (key === 'createTime') {
      rawValue = job.createTime;
    } else if (key === 'startTime') {
      rawValue = job.startTime;
    } else if (key === 'endTime') {
      rawValue = job.endTime;
    }
    if (!rawValue) {
      return {
        rawValue: '',
        displayValue: 'N/A',
        durationText: '',
        localStr: '',
        utcStr: '',
        elapsedHtml: '',
      };
    }

    const date = dateUtils.parsePdtTimestamp(rawValue);
    const isValid = date && !isNaN(date.getTime());

    if (!isValid) {
      return {
        rawValue,
        displayValue: rawValue,
        durationText: '',
        localStr: '',
        utcStr: '',
        elapsedHtml: '',
      };
    }

    const createVal = job.createTime;
    const createDate = createVal
      ? dateUtils.parsePdtTimestamp(createVal)
      : null;
    let durationText = '';
    let elapsedHtml = '';

    if (key === 'createTime') {
      durationText = '(base)';
    } else {
      const elapsed = dateUtils.getElapsedTimeText(
        date,
        createDate,
        'Create Time',
      );
      durationText = elapsed.durationText;
      elapsedHtml = elapsed.elapsedHtml;
    }

    const displayValue = dateUtils.formatPdt(date);

    const localStr = date.toLocaleString(undefined, {
      weekday: 'short',
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      timeZoneName: 'short',
    });
    const utcStr = date.toUTCString();

    return {
      rawValue,
      displayValue,
      durationText,
      localStr,
      utcStr,
      elapsedHtml,
    };
  }

  getDeviceSearchLink(deviceType: string, dimensions: Record<string, string> = {}): string {
    const queryParams = new URLSearchParams();
    if (deviceType) queryParams.set('type', deviceType);
    const job = this.job();
    if (job.user) queryParams.set('owner', job.user);
    for (const [k, v] of Object.entries(dimensions)) {
      queryParams.set(k, v);
    }
    return `../device_search/device_search.html?${queryParams.toString()}`;
  }
}
