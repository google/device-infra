import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
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

import {
  TestOverviewData,
  TestStatus,
  TestSummary,
} from '../../../../core/models/test_overview';
import {AccordionItem} from '../../../../shared/components/accordion_item/accordion_item';
import {InfoCard} from '../../../../shared/components/info_card/info_card';
import {
  MasterDetailLayout,
  NavItem,
} from '../../../../shared/components/master_detail_layout/master_detail_layout';
import {NavLink} from '../../../../shared/components/nav_link/nav_link';
import {useCopyToClipboard} from '../../../../shared/composables/copy';
import {createSearchFilter} from '../../../../shared/composables/search_filter';
import {
  getTestResultBadge,
  getTestStatusBadge,
} from '../../../../shared/composables/status_badge';
import {
  createTimestampInfoMap,
  STANDARD_TIMESTAMP_KEYS,
} from '../../../../shared/composables/timestamp_info';
import {dateUtils} from '../../../../shared/utils/date_utils';

const NAV_ITEM_ERROR: NavItem = {id: 'overview-error', label: 'Error Details'};
const NAV_ITEM_WARNING: NavItem = {
  id: 'overview-warning',
  label: 'Warning Details',
};
const NAV_ITEM_SUB_TESTS: NavItem = {
  id: 'test-sub-tests',
  label: 'Sub-tests',
};
const NAV_ITEM_EXECUTION: NavItem = {
  id: 'overview-execution',
  label: 'Execution Details',
};
const NAV_ITEM_PROPERTIES: NavItem = {
  id: 'overview-properties',
  label: 'Test Properties',
};

/** Component for rendering the test overview tab content. */
@Component({
  selector: 'app-test-overview-tab',
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
    AccordionItem,
    InfoCard,
    MasterDetailLayout,
    NavLink,
  ],
  templateUrl: './test_overview_tab.ng.html',
  styleUrl: './test_overview_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TestOverviewTab {
  /** The target test overview data passed from the parent component. */
  readonly test = input.required<TestOverviewData>();

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

  readonly rootTestId = computed(
    () => this.test().subTestsInfo?.rootTestId || this.test().id,
  );

  readonly subTestsList = computed(
    () => this.test().subTestsInfo?.subTests?.test ?? [],
  );

  private readonly propertiesFilter = createSearchFilter(
    computed(() => this.test().properties),
  );
  readonly propertiesSearchTerm = this.propertiesFilter.searchTerm;
  readonly hasProperties = this.propertiesFilter.hasData;
  readonly filteredProperties = this.propertiesFilter.filteredData;

  readonly overviewNavList = computed((): NavItem[] => {
    const troubleshooting = this.test().troubleshooting;
    const hasErrors = (troubleshooting?.resultCause?.error?.length ?? 0) > 0;
    const hasWarnings = (troubleshooting?.warnings?.warning?.length ?? 0) > 0;
    const hasSubTests = this.subTestsList().length > 0;

    return [
      ...(hasErrors ? [NAV_ITEM_ERROR] : []),
      ...(hasWarnings ? [NAV_ITEM_WARNING] : []),
      ...(hasSubTests ? [NAV_ITEM_SUB_TESTS] : []),
      NAV_ITEM_EXECUTION,
      NAV_ITEM_PROPERTIES,
    ];
  });

  /** Immutable list of timestamp metadata keys and their corresponding display labels. */
  readonly timestampKeys = STANDARD_TIMESTAMP_KEYS;
  private readonly executionDetails = computed(
    () => this.test().executionDetails,
  );
  readonly timestampInfoMap = createTimestampInfoMap(
    this.executionDetails,
    this.timestampKeys,
  );

  getTestDuration(subTest: TestSummary): string {
    if (!subTest.startTime || !subTest.endTime) return 'N/A';
    const start = dateUtils.parseUtcTimestamp(subTest.startTime);
    const end = dateUtils.parseUtcTimestamp(subTest.endTime);
    let diffSec = -1;
    if (!start || !end || isNaN(start.getTime()) || isNaN(end.getTime())) {
      const startIso = new Date(subTest.startTime);
      const endIso = new Date(subTest.endTime);
      if (isNaN(startIso.getTime()) || isNaN(endIso.getTime())) return 'N/A';
      diffSec = Math.round((endIso.getTime() - startIso.getTime()) / 1000);
    } else {
      diffSec = Math.round((end.getTime() - start.getTime()) / 1000);
    }

    if (diffSec < 0) return 'N/A';
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

  getTestDevicesList(subTest: TestSummary): string[] {
    if (subTest.devices?.device && subTest.devices.device.length > 0) {
      return subTest.devices.device.map((d) => d.id).filter(Boolean);
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

  getTestStatusBadge(subTest: TestSummary) {
    return getTestStatusBadge(subTest.status);
  }

  getTestResultBadge(subTest: TestSummary) {
    if (subTest.status !== TestStatus.TEST_STATUS_DONE || !subTest.result) {
      return null;
    }
    return getTestResultBadge(subTest.result);
  }

  getTestStartTime(subTest: TestSummary): string {
    if (!subTest.startTime) return 'N/A';
    const date = dateUtils.parseUtcTimestamp(subTest.startTime);
    if (!date || isNaN(date.getTime())) return subTest.startTime || 'N/A';
    return dateUtils.formatDetailedLocal(date);
  }
}
