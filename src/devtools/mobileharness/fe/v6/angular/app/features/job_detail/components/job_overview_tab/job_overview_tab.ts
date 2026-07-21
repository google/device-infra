import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  Signal,
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

import {useCopyToClipboard} from '@deviceinfra/app/shared/composables/copy';
import {
  createSearchFilter,
  FilterEntry,
} from '@deviceinfra/app/shared/composables/search_filter';
import {
  JobOverviewData,
  TestResult,
  TestStatus,
} from '../../../../core/models/job_overview';
import {TestSummary} from '../../../../core/models/test_overview';
import {AccordionItem} from '../../../../shared/components/accordion_item/accordion_item';
import {InfoCard} from '../../../../shared/components/info_card/info_card';
import {
  MasterDetailLayout,
  NavItem,
} from '../../../../shared/components/master_detail_layout/master_detail_layout';
import {dateUtils} from '../../../../shared/utils/date_utils';

/** Component for rendering the job overview tab content. */
@Component({
  selector: 'app-job-overview-tab',
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
  templateUrl: './job_overview_tab.ng.html',
  styleUrl: './job_overview_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JobOverviewTab {
  readonly job = input.required<JobOverviewData>();

  readonly displayedColumns = ['id', 'name', 'status', 'duration', 'device'];

  readonly copyToClipboard = useCopyToClipboard();
  readonly copiedTestIds = signal<Record<string, boolean>>({});

  private readonly propertiesFilter = createSearchFilter<string>(
    computed(() => this.job().properties),
  );
  readonly propertiesSearchTerm = this.propertiesFilter.searchTerm;
  readonly hasProperties = this.propertiesFilter.hasData;
  readonly filteredProperties: Signal<Array<FilterEntry<string>>> =
    this.propertiesFilter.filteredData;
  readonly timestampKeys = [
    {key: 'createTime', label: 'Create Time'},
    {key: 'startTime', label: 'Start Time'},
    {key: 'endTime', label: 'End Time'},
    {key: 'updateTime', label: 'Last Update Time'},
  ] as const;

  readonly overviewNavList = computed(() => {
    const job = this.job();
    const list: NavItem[] = [];
    const errors = job.troubleshooting?.resultCause?.error;
    const warnings = job.troubleshooting?.warnings?.warning;
    if (errors && errors.length > 0) {
      list.push({id: 'overview-error', label: 'Error Details'});
    }
    if (warnings && warnings.length > 0) {
      list.push({id: 'overview-warning', label: 'Warning Details'});
    }
    const tests = job.tests?.test;
    if (tests && tests.length > 0) {
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
    const devices = this.job().config.devices?.device || [];
    return devices.length > 1;
  });

  readonly jobSettingsMap = computed(() => {
    const job = this.job();
    const settings = job.config.settings || {};
    const result: Record<string, string> = {};

    if (settings.priority) {
      result['Job Priority'] = settings.priority;
    }
    if (settings.totalTestCount !== undefined) {
      result['Total Test Count'] = String(settings.totalTestCount);
    }
    if (settings.jobTimeoutSec !== undefined) {
      result['Job Timeout(s)'] = String(settings.jobTimeoutSec);
    }
    if (settings.testTimeoutSec !== undefined) {
      result['Test Timeout(s)'] = String(settings.testTimeoutSec);
    }
    if (settings.startTimeoutSec !== undefined) {
      result['Start Timeout(s)'] = String(settings.startTimeoutSec);
    }
    if (settings.testAttempts !== undefined) {
      result['Test Attempts'] = String(settings.testAttempts);
    }
    if (settings.forceRetry !== undefined) {
      result['Force Retry'] = String(settings.forceRetry);
    }
    if (settings.retryLevel) {
      result['Retry Level'] = settings.retryLevel;
    }
    return result;
  });

  readonly hasJobSettings = computed(() => {
    return Object.keys(this.jobSettingsMap()).length > 0;
  });

  readonly hasJobParams = computed(() => {
    const params = this.job().config.params;
    return params && Object.keys(params).length > 0;
  });

  readonly hasDimensions = computed(() => {
    const devices = this.job().config.devices?.device || [];
    const dims = devices[0]?.dimensions;
    return dims && Object.keys(dims).length > 0;
  });

  readonly deviceRequirementsBasic = computed(() => {
    const devices = this.job().config.devices?.device || [];
    const firstDev = devices[0];
    const basic: Record<string, string> = {};
    if (firstDev) {
      if (firstDev.deviceType) {
        basic['Device Type'] = firstDev.deviceType;
      }
      if (firstDev.driver) {
        basic['Driver'] = firstDev.driver;
      }
      if (firstDev.decorators && firstDev.decorators.length > 0) {
        basic['Decorator(s)'] = firstDev.decorators.join(', ');
      }
    }
    return basic;
  });

  getTestDuration(test: TestSummary): string {
    if (!test.startTime || !test.endTime) return 'N/A';
    const start = dateUtils.parsePdtTimestamp(test.startTime);
    const end = dateUtils.parsePdtTimestamp(test.endTime);
    if (!start || !end || isNaN(start.getTime()) || isNaN(end.getTime())) {
      const startIso = new Date(test.startTime);
      const endIso = new Date(test.endTime);
      if (isNaN(startIso.getTime()) || isNaN(endIso.getTime())) return 'N/A';
      const diffSec = Math.round(
        (endIso.getTime() - startIso.getTime()) / 1000,
      );
      return diffSec >= 0 ? `${diffSec}s` : 'N/A';
    }
    const diffSec = Math.round((end.getTime() - start.getTime()) / 1000);
    return diffSec >= 0 ? `${diffSec}s` : 'N/A';
  }

  getTestDevicesList(test: TestSummary): string[] {
    if (test.devices?.device && test.devices.device.length > 0) {
      return test.devices.device.map((d) => d.id).filter(Boolean);
    }
    return [];
  }

  copyTestId(id: string) {
    this.copyToClipboard(id, 'Test ID copied to clipboard!');
    this.copiedTestIds.update((map: Record<string, boolean>) => ({
      ...map,
      [id]: true,
    }));
    setTimeout(() => {
      this.copiedTestIds.update((map: Record<string, boolean>) => ({
        ...map,
        [id]: false,
      }));
    }, 2000);
  }

  getTestStatusBadge(test: TestSummary) {
    const status = test.status;
    const result = test.result;

    if (status === TestStatus.TEST_STATUS_RUNNING) {
      return {
        icon: 'sync',
        bgClass: 'bg-blue-50',
        colorClass: 'text-blue-600',
        text: 'Running',
        spin: true,
      };
    }
    if (status === TestStatus.TEST_STATUS_SUSPENDED) {
      return {
        icon: 'pause_circle_filled',
        bgClass: 'bg-yellow-55',
        colorClass: 'text-yellow-700',
        text: 'Suspended',
        spin: false,
      };
    }
    if (
      status === TestStatus.TEST_STATUS_NEW ||
      status === TestStatus.TEST_STATUS_ASSIGNED
    ) {
      return {
        icon: 'schedule',
        bgClass: 'bg-gray-50',
        colorClass: 'text-gray-500',
        text:
          status === TestStatus.TEST_STATUS_ASSIGNED ? 'Assigned' : 'Queued',
        spin: false,
      };
    }
    if (status === TestStatus.TEST_STATUS_DONE) {
      switch (result) {
        case TestResult.TEST_RESULT_PASS:
          return {
            icon: 'check_circle',
            bgClass: 'bg-green-50',
            colorClass: 'text-green-600',
            text: 'Passed',
            spin: false,
          };
        case TestResult.TEST_RESULT_FAIL:
          return {
            icon: 'error',
            bgClass: 'bg-red-50',
            colorClass: 'text-red-600',
            text: 'Failed',
            spin: false,
          };
        case TestResult.TEST_RESULT_ERROR:
          return {
            icon: 'error',
            bgClass: 'bg-red-50',
            colorClass: 'text-red-600',
            text: 'Error',
            spin: false,
          };
        case TestResult.TEST_RESULT_TIMEOUT:
          return {
            icon: 'access_time',
            bgClass: 'bg-red-50',
            colorClass: 'text-red-600',
            text: 'Timeout',
            spin: false,
          };
        case TestResult.TEST_RESULT_ABORT:
          return {
            icon: 'do_not_disturb_on',
            bgClass: 'bg-gray-50',
            colorClass: 'text-gray-600',
            text: 'Aborted',
            spin: false,
          };
        case TestResult.TEST_RESULT_SKIP:
          return {
            icon: 'block',
            bgClass: 'bg-gray-50',
            colorClass: 'text-gray-500',
            text: 'Skipped',
            spin: false,
          };
        default:
          return {
            icon: 'help_outline',
            bgClass: 'bg-gray-50',
            colorClass: 'text-gray-500',
            text: 'Unknown',
            spin: false,
          };
      }
    }
    return {
      icon: 'help_outline',
      bgClass: 'bg-gray-50',
      colorClass: 'text-gray-500',
      text: 'Unknown',
      spin: false,
    };
  }

  getTimestampInfo(
    key: 'createTime' | 'startTime' | 'endTime' | 'updateTime',
    job: JobOverviewData,
  ) {
    let rawValue: string | undefined;
    const details = job.executionDetails;
    if (details) {
      if (key === 'createTime') {
        rawValue = details.createTime;
      } else if (key === 'startTime') {
        rawValue = details.startTime;
      } else if (key === 'endTime') {
        rawValue = details.endTime;
      } else if (key === 'updateTime') {
        rawValue = details.updateTime;
      }
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

    const createVal = details?.createTime;
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

  getDeviceSearchLink(
    deviceType: string,
    dimensions: Record<string, string> = {},
  ): string {
    const queryParams = new URLSearchParams();
    if (deviceType) {
      queryParams.set('type', deviceType);
    }
    const job = this.job();
    if (job.executionDetails?.user) {
      queryParams.set('owner', job.executionDetails.user);
    }
    for (const [k, v] of Object.entries(dimensions)) {
      queryParams.set(k, v);
    }
    return `../device_search/device_search.html?${queryParams.toString()}`;
  }
}
