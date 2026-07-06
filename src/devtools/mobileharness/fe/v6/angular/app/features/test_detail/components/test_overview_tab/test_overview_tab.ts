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
import {MatTooltipModule} from '@angular/material/tooltip';

import {TestOverviewData} from '../../../../core/models/test_overview';
import {AccordionItem} from '../../../../shared/components/accordion_item/accordion_item';
import {InfoCard} from '../../../../shared/components/info_card/info_card';
import {
  MasterDetailLayout,
  NavItem,
} from '../../../../shared/components/master_detail_layout/master_detail_layout';
import {createSearchFilter} from '../../../../shared/composables/search_filter';
import {dateUtils} from '../../../../shared/utils/date_utils';

const NAV_ITEM_ERROR: NavItem = {id: 'overview-error', label: 'Error Details'};
const NAV_ITEM_WARNING: NavItem = {
  id: 'overview-warning',
  label: 'Warning Details',
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
    MatTooltipModule,
    AccordionItem,
    InfoCard,
    MasterDetailLayout,
  ],
  templateUrl: './test_overview_tab.ng.html',
  styleUrl: './test_overview_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TestOverviewTab {
  /** The target test overview data passed from the parent component. */
  readonly test = input.required<TestOverviewData>();

  private readonly propertiesFilter = createSearchFilter(
    computed(() => this.test().properties),
  );
  readonly propertiesSearchTerm = this.propertiesFilter.searchTerm;
  readonly hasProperties = this.propertiesFilter.hasData;
  readonly filteredProperties = this.propertiesFilter.filteredData;

  /** Immutable list of timestamp metadata keys and their corresponding display labels. */
  readonly timestampKeys = [
    {key: 'createTime', label: 'Create Time'},
    {key: 'startTime', label: 'Start Time'},
    {key: 'endTime', label: 'End Time'},
    {key: 'lastUpdateTime', label: 'Last Update Time'},
  ] as const;

  /**
   * Computed navigation item list for the side navigation menu.
   * Dynamically includes error details, warning details, execution details, and test properties.
   */
  readonly overviewNavList = computed((): NavItem[] => {
    const troubleshooting = this.test().troubleshooting;
    const hasErrors = (troubleshooting?.resultCause?.error?.length ?? 0) > 0;
    const hasWarnings = (troubleshooting?.warnings?.warning?.length ?? 0) > 0;

    return [
      ...(hasErrors ? [NAV_ITEM_ERROR] : []),
      ...(hasWarnings ? [NAV_ITEM_WARNING] : []),
      NAV_ITEM_EXECUTION,
      NAV_ITEM_PROPERTIES,
    ];
  });

  /**
   * Computed map of parsed timestamp details for each lifecycle event (create, start, end).
   * Provides comprehensive timing breakdown, formatted display strings, and timezone information.
   */
  readonly timestampInfoMap = computed(() => {
    const test = this.test();
    const createVal = test.executionDetails?.createTime;
    const baseCreateDate = createVal
      ? dateUtils.parsePdtTimestamp(createVal)
      : null;
    const result: Record<string, ReturnType<typeof this.getTimestampInfo>> = {};
    for (const item of this.timestampKeys) {
      result[item.key] = this.getTimestampInfo(item.key, test, baseCreateDate);
    }
    return result;
  });

  /**
   * Parses and formats detailed timestamp information for a given lifecycle key.
   * Calculates elapsed time relative to the creation time and provides local/UTC representation strings.
   *
   * @param key The specific lifecycle timestamp key to interrogate.
   * @param test The test overview data object containing raw timestamps.
   * @param baseCreateDate Optional pre-parsed creation date for performance optimization.
   * @return An object containing raw, display, duration, local, UTC, and elapsed HTML strings.
   */
  getTimestampInfo(
    key: 'createTime' | 'startTime' | 'endTime' | 'lastUpdateTime',
    test: TestOverviewData,
    baseCreateDate: Date | null = null,
  ) {
    const details = test.executionDetails;
    let rawValue: string | undefined = undefined;
    if (details) {
      if (key === 'createTime') {
        rawValue = details.createTime;
      } else if (key === 'startTime') {
        rawValue = details.startTime;
      } else if (key === 'endTime') {
        rawValue = details.endTime;
      } else if (key === 'lastUpdateTime') {
        rawValue = details.lastUpdateTime;
      }
    }
    const date = rawValue ? dateUtils.parsePdtTimestamp(rawValue) : null;
    const isValid = date && !isNaN(date.getTime());

    if (!rawValue || !isValid) {
      return {
        rawValue: rawValue ?? '',
        displayValue: rawValue ?? 'N/A',
        durationText: '',
        localStr: '',
        utcStr: '',
        elapsedHtml: '',
      };
    }

    const createDate =
      baseCreateDate ??
      (test.executionDetails?.createTime
        ? dateUtils.parsePdtTimestamp(test.executionDetails.createTime)
        : null);

    const elapsed =
      key === 'createTime'
        ? {durationText: '(base)', elapsedHtml: ''}
        : dateUtils.getElapsedTimeText(date, createDate, 'Create Time');

    return {
      rawValue,
      displayValue: dateUtils.formatPdt(date),
      durationText: elapsed.durationText,
      localStr: dateUtils.formatDetailedLocal(date),
      utcStr: date.toUTCString(),
      elapsedHtml: elapsed.elapsedHtml,
    };
  }
}
