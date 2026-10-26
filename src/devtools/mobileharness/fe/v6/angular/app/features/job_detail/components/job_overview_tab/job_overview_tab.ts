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
import {MatSortModule, Sort, SortDirection} from '@angular/material/sort';
import {MatTableModule} from '@angular/material/table';
import {MatTooltipModule} from '@angular/material/tooltip';
import {RouterModule} from '@angular/router';

import {
  JobOverviewData,
  TestStatus,
} from '../../../../core/models/job_overview';
import {TestSummary} from '../../../../core/models/test_overview';
import {AccordionItem} from '../../../../shared/components/accordion_item/accordion_item';
import {InfoCard} from '../../../../shared/components/info_card/info_card';
import {
  MasterDetailLayout,
  NavItem,
} from '../../../../shared/components/master_detail_layout/master_detail_layout';
import {NavLink} from '../../../../shared/components/nav_link/nav_link';
import {useCopyToClipboard} from '../../../../shared/composables/copy';
import {
  createSearchFilter,
  FilterEntry,
} from '../../../../shared/composables/search_filter';
import {
  getTestResultBadge,
  getTestStatusBadge,
} from '../../../../shared/composables/status_badge';
import {
  createTimestampInfoMap,
  STANDARD_TIMESTAMP_KEYS,
} from '../../../../shared/composables/timestamp_info';
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
    MatSortModule,
    MatTableModule,
    MatTooltipModule,
    RouterModule,
    InfoCard,
    MasterDetailLayout,
    AccordionItem,
    NavLink,
  ],
  templateUrl: './job_overview_tab.ng.html',
  styleUrl: './job_overview_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JobOverviewTab {
  readonly job = input.required<JobOverviewData>();

  readonly displayedColumns = [
    'id',
    'name',
    'status',
    'result',
    'startTime',
    'duration',
    'host',
    'device',
  ];

  readonly copyToClipboard = useCopyToClipboard();
  readonly copiedTestIds = signal<Record<string, boolean>>({});
  readonly sortColumn = signal<string>('');
  readonly sortDirection = signal<SortDirection>('');

  readonly sortedTests = computed<TestSummary[]>(() => {
    const tests = this.job().tests?.test ?? [];
    const column = this.sortColumn();
    const direction = this.sortDirection();
    if (!column || !direction) {
      return tests;
    }
    return [...tests].sort((a, b) =>
      this.compareTests(a, b, column, direction),
    );
  });

  private readonly propertiesFilter = createSearchFilter<string>(
    computed(() => this.job().properties),
  );
  readonly propertiesSearchTerm = this.propertiesFilter.searchTerm;
  readonly hasProperties = this.propertiesFilter.hasData;
  readonly filteredProperties: Signal<Array<FilterEntry<string>>> =
    this.propertiesFilter.filteredData;
  readonly timestampKeys = STANDARD_TIMESTAMP_KEYS;
  private readonly executionDetails = computed(
    () => this.job().executionDetails,
  );
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

  readonly timestampInfoMap = createTimestampInfoMap(
    this.executionDetails,
    this.timestampKeys,
  );

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

  private getTestDurationSec(test: TestSummary): number | null {
    const startMs = this.parseTimestampMs(test.startTime);
    const endMs = this.parseTimestampMs(test.endTime);
    if (startMs === null || endMs === null || endMs < startMs) return null;
    return Math.round((endMs - startMs) / 1000);
  }

  getTestDuration(test: TestSummary): string {
    const diffSec = this.getTestDurationSec(test);
    if (diffSec === null) return 'N/A';
    if (diffSec < 60) return `${diffSec}s`;
    if (diffSec < 3600) {
      const m = Math.floor(diffSec / 60);
      const s = diffSec % 60;
      return `${m}m ${s}s`;
    }
    const h = Math.floor(diffSec / 3600);
    const m = Math.floor((diffSec % 3600) / 60);
    const s = diffSec % 60;
    return `${h}h ${m}m ${s}s`;
  }

  private parseTimestampMs(timestamp?: string): number | null {
    if (!timestamp) return null;
    const ms = dateUtils.parseUtcTimestamp(timestamp).getTime();
    return isNaN(ms) ? null : ms;
  }

  onSortChange(sort: Sort): void {
    this.sortColumn.set(sort.active);
    this.sortDirection.set(sort.direction);
  }

  private compareTests(
    a: TestSummary,
    b: TestSummary,
    column: string,
    direction: 'asc' | 'desc',
  ): number {
    const dir = direction === 'asc' ? 1 : -1;
    let cmp = 0;

    switch (column) {
      case 'id':
        cmp = this.compareStrings(a.id, b.id, dir);
        break;
      case 'name':
        cmp = this.compareStrings(a.name, b.name, dir);
        break;
      case 'status':
        cmp = this.compareStrings(
          this.getTestStatusBadge(a).text,
          this.getTestStatusBadge(b).text,
          dir,
        );
        break;
      case 'result':
        cmp = this.compareNullableStrings(
          this.getTestResultBadge(a)?.text ?? null,
          this.getTestResultBadge(b)?.text ?? null,
          dir,
        );
        break;
      case 'startTime':
        cmp = this.compareNullableNumbers(
          this.parseTimestampMs(a.startTime),
          this.parseTimestampMs(b.startTime),
          dir,
        );
        break;
      case 'duration':
        cmp = this.compareNullableNumbers(
          this.getTestDurationSec(a),
          this.getTestDurationSec(b),
          dir,
        );
        break;
      case 'host':
        cmp = this.compareNullableStrings(
          a.host?.name || null,
          b.host?.name || null,
          dir,
        );
        break;
      case 'device':
        cmp = this.compareNullableStrings(
          this.getTestDevicesList(a).join(', ') || null,
          this.getTestDevicesList(b).join(', ') || null,
          dir,
        );
        break;
      default:
        break;
    }

    return cmp !== 0 ? cmp : this.compareStrings(a.id, b.id, 1);
  }

  private compareStrings(a: string, b: string, dir: number): number {
    return (
      a.localeCompare(b, undefined, {
        numeric: true,
        sensitivity: 'base',
      }) * dir
    );
  }

  private compareNullableStrings(
    a: string | null,
    b: string | null,
    dir: number,
  ): number {
    if (a === null && b === null) return 0;
    if (a === null) return 1;
    if (b === null) return -1;
    return this.compareStrings(a, b, dir);
  }

  private compareNullableNumbers(
    a: number | null,
    b: number | null,
    dir: number,
  ): number {
    if (a === null && b === null) return 0;
    if (a === null) return 1;
    if (b === null) return -1;
    return (a - b) * dir;
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
    return getTestStatusBadge(test.status);
  }

  getTestResultBadge(test: TestSummary) {
    if (test.status !== TestStatus.TEST_STATUS_DONE || !test.result) {
      return null;
    }
    return getTestResultBadge(test.result);
  }

  getTestStartTime(test: TestSummary): string {
    if (!test.startTime) return 'N/A';
    const date = dateUtils.parseUtcTimestamp(test.startTime);
    if (!date || isNaN(date.getTime())) return test.startTime || 'N/A';
    return dateUtils.formatDetailedLocal(date);
  }
}
