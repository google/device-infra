import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal,
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
  readonly test = input.required<TestOverviewData>();

  readonly propertiesSearchTerm = signal<string>('');
  readonly timestampKeys = [
    {key: 'createTime', label: 'Create Time'},
    {key: 'startTime', label: 'Start Time'},
    {key: 'endTime', label: 'End Time'},
  ] as const;

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

  readonly warningTitle = computed(() => {
    const warnings = this.test().warnings;
    return warnings && warnings.length > 0 ? warnings[0] : '';
  });

  readonly warningDetailsText = computed(() => {
    const warnings = this.test().warnings;
    return warnings && warnings.length > 1 ? warnings.slice(1).join('\n') : '';
  });

  readonly hasProperties = computed(() => {
    const props = this.test().properties;
    return props && Object.keys(props).length > 0;
  });

  readonly filteredProperties = computed(() => {
    const properties = this.test().properties;
    const searchTerm = this.propertiesSearchTerm().toLowerCase().trim();
    if (!properties) return [];
    const entries = Object.entries(properties).map(([key, value]) => ({
      key,
      value,
    }));
    if (!searchTerm) return entries;
    return entries.filter(
      (item) =>
        item.key.toLowerCase().includes(searchTerm) ||
        item.value.toLowerCase().includes(searchTerm),
    );
  });

  readonly timestampInfoMap = computed(() => {
    const test = this.test();
    const result: Record<string, ReturnType<typeof this.getTimestampInfo>> = {};
    for (const item of this.timestampKeys) {
      result[item.key] = this.getTimestampInfo(item.key, test);
    }
    return result;
  });

  getStatusPillLabel(test: TestOverviewData): string {
    if (!test || !test.status) return 'UNKNOWN';
    return test.status;
  }

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
