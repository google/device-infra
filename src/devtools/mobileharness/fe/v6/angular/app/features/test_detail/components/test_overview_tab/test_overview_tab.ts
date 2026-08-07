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
import {
  createTimestampInfoMap,
  STANDARD_TIMESTAMP_KEYS,
} from '../../../../shared/composables/timestamp_info';

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

  /** Immutable list of timestamp metadata keys and their corresponding display labels. */
  readonly timestampKeys = STANDARD_TIMESTAMP_KEYS;
  private readonly executionDetails = computed(
    () => this.test().executionDetails,
  );
  readonly timestampInfoMap = createTimestampInfoMap(
    this.executionDetails,
    this.timestampKeys,
  );
}
