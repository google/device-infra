import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TestOverviewData} from '../../../../core/models/test_overview';
import {InfoCard} from '../../../../shared/components/info_card/info_card';
import {
  MasterDetailLayout,
  NavItem,
} from '../../../../shared/components/master_detail_layout/master_detail_layout';
import {createSearchFilter} from '../../../../shared/composables/search_filter';
import {dateUtils} from '../../../../shared/utils/date_utils';

/** Component for rendering the test overview tab content. */
@Component({
  selector: 'app-test-overview-tab',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatTooltipModule,
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
  ] as const;

  /**
   * Computed navigation item list for the side navigation menu.
   * Dynamically includes error details, warning details, execution details, and test properties.
   */
  readonly overviewNavList = computed(() => {
    const test = this.test();
    const list: NavItem[] = [];
    if (test.error) {
      list.push({id: 'overview-error', label: 'Error Details'});
    }
    if (test.warnings && test.warnings.length > 0) {
      list.push({id: 'overview-warning', label: 'Warning Details'});
    }
    list.push({id: 'overview-execution', label: 'Execution Details'});
    list.push({id: 'overview-properties', label: 'Test Properties'});
    return list;
  });

  /** Computed title of the primary warning message, representing the first warning recorded. */
  readonly warningTitle = computed(() => {
    const warnings = this.test().warnings;
    return warnings && warnings.length > 0 ? warnings[0] : '';
  });

  /** Computed details text encompassing all remaining secondary warning messages joined by newlines. */
  readonly warningDetailsText = computed(() => {
    const warnings = this.test().warnings;
    return warnings && warnings.length > 1 ? warnings.slice(1).join('\n') : '';
  });

  // properties-filtering logic handled by usePropertiesFilter composable

  /**
   * Computed map of parsed timestamp details for each lifecycle event (create, start, end).
   * Provides comprehensive timing breakdown, formatted display strings, and timezone information.
   */
  readonly timestampInfoMap = computed(() => {
    const test = this.test();
    const result: Record<string, ReturnType<typeof this.getTimestampInfo>> = {};
    for (const item of this.timestampKeys) {
      result[item.key] = this.getTimestampInfo(item.key, test);
    }
    return result;
  });

  /**
   * Retrieves the status label for display in the status badge pill.
   *
   * @param test The test overview data object.
   * @return The status string, defaulting to 'UNKNOWN' if unavailable.
   */
  getStatusPillLabel(test: TestOverviewData): string {
    if (!test || !test.status) return 'UNKNOWN';
    return test.status;
  }

  /**
   * Parses and formats detailed timestamp information for a given lifecycle key.
   * Calculates elapsed time relative to the creation time and provides local/UTC representation strings.
   *
   * @param key The specific lifecycle timestamp key to interrogate.
   * @param test The test overview data object containing raw timestamps.
   * @return An object containing raw, display, duration, local, UTC, and elapsed HTML strings.
   */
  getTimestampInfo(
    key: 'createTime' | 'startTime' | 'endTime',
    test: TestOverviewData,
  ) {
    let rawValue: string | undefined;
    if (key === 'createTime') {
      rawValue = test.createTime;
    } else if (key === 'startTime') {
      rawValue = test.startTime;
    } else if (key === 'endTime') {
      rawValue = test.endTime;
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

    const createVal = test.createTime;
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
}
