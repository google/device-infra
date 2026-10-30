import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
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
  getTestSummaryDevicesList,
  TestOverviewData,
  TestSummary,
} from '../../../../core/models/test_overview';
import {AccordionItem} from '../../../../shared/components/accordion_item/accordion_item';
import {InfoCard} from '../../../../shared/components/info_card/info_card';
import {
  MasterDetailLayout,
  NavItem,
} from '../../../../shared/components/master_detail_layout/master_detail_layout';
import {NavLink} from '../../../../shared/components/nav_link/nav_link';
import {useCopiedIds} from '../../../../shared/composables/copy';
import {createSearchFilter} from '../../../../shared/composables/search_filter';
import {getTestSummaryResultBadge} from '../../../../shared/composables/status_badge';
import {
  createTimestampInfoMap,
  formatExecutionDuration,
  formatExecutionStartTime,
  STANDARD_TIMESTAMP_KEYS,
} from '../../../../shared/composables/timestamp_info';

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
    'result',
    'startTime',
    'duration',
    'host',
    'device',
  ];

  private readonly testIdCopy = useCopiedIds('Test ID copied to clipboard!');
  readonly copiedTestIds = this.testIdCopy.copiedIds;
  readonly copyTestId = this.testIdCopy.copyId;
  readonly getTestDevicesList = getTestSummaryDevicesList;

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
    return formatExecutionDuration(subTest.startTime, subTest.endTime);
  }

  getTestResultBadge(subTest: TestSummary) {
    return getTestSummaryResultBadge(subTest);
  }

  getTestStartTime(subTest: TestSummary): string {
    return formatExecutionStartTime(subTest.startTime);
  }
}
