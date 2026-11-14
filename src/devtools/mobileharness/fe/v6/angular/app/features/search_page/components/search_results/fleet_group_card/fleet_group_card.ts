import {DecimalPipe} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
} from '@angular/core';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';

import {Column, FleetGroup} from '../../../../../core/models/search';
import {KeyDescriptionTooltipDirective} from '../../../../../shared/directives/key_description_tooltip/key_description_tooltip';
import {
  DEFAULT_GROUP_EXPAND_PAGE_SIZE,
  GROUP_ROW_HEIGHT_ESTIMATE,
  GroupPageState,
  PAGE_SIZE_OPTIONS,
} from '../../../models';
import {FleetSearchStore} from '../../../services/fleet_search_store';
import {formatPaginationRange, getDeviceTargetKey} from '../../../utils';
import {SearchCellComponent} from '../common/search_cell/search_cell';
import {SearchPaginationComponent} from '../common/search_pagination/search_pagination';
import {
  isTableWindowSpacer,
  TableWindowDirective,
} from '../common/table_window/table_window';

/** Standalone component representing a single expandable group card in grouped results view. */
@Component({
  selector: 'app-fleet-group-card',
  standalone: true,
  templateUrl: './fleet_group_card.ng.html',
  styleUrl: './fleet_group_card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    MatCheckboxModule,
    MatIconModule,
    MatProgressBarModule,
    KeyDescriptionTooltipDirective,
    SearchCellComponent,
    SearchPaginationComponent,
    TableWindowDirective,
  ],
})
export class FleetGroupCardComponent {
  readonly store = inject(FleetSearchStore);

  /** Fleet group header card data object. */
  readonly group = input.required<FleetGroup>();

  /** Default page size for expanded group inner table. */
  protected readonly defaultPageSize = DEFAULT_GROUP_EXPAND_PAGE_SIZE;

  /** Rows-per-page choices of the inner table footer (10 … 5000). */
  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;

  /** Initial row-height estimate for the windowed inner table; the directive measures the real height. */
  protected readonly rowHeightEstimate = GROUP_ROW_HEIGHT_ESTIMATE;

  /** Separates the windowing spacer rows from real row items in the template. */
  protected readonly isSpacer = isTableWindowSpacer;

  /** Whether this group card accordion is currently open/expanded. */
  readonly isOpen = computed<boolean>(() =>
    this.store.openGroupIds().has(this.group().groupId),
  );

  /** Async state for lazy-loaded group row data. */
  readonly groupState = computed<GroupPageState | undefined>(() =>
    this.store.expandedGroupPages().get(this.group().groupId),
  );

  /** Computed structured utilization view model for progress and legend bars. */
  readonly utilization = computed(() => {
    const util = this.group().utilization;
    const total = util?.total || 0;
    if (!util || total <= 0) return null;

    const toMetric = (v = 0) => {
      const width = Math.min(100, Math.max(0, (100 * v) / total));
      return {count: v, pct: Math.round(width), width};
    };

    const busy = toMetric(util.busy);
    const idle = toMetric(util.idle);
    const other = toMetric(util.other);

    return {
      busyCount: busy.count,
      busyPct: busy.pct,
      busyWidth: busy.width,
      idleCount: idle.count,
      idlePct: idle.pct,
      idleWidth: idle.width,
      otherCount: other.count,
      otherPct: other.pct,
      otherWidth: other.width,
    };
  });

  /** Group rows with pre-computed composite tracking keys and selection state. */
  readonly rowItems = computed(() => {
    const rows = this.groupState()?.data?.rows ?? [];
    const selected = this.store.selectedItems();
    return rows.map((row) => {
      const key = getDeviceTargetKey(row);
      return {row, key, selected: selected.has(key)};
    });
  });

  /** Effective columns for the inner table with pre-resolved header descriptions. */
  readonly innerColumns = computed<Column[]>(() => {
    const rawCols = this.groupState()?.data?.columns;
    const cols =
      rawCols && rawCols.length > 0 ? rawCols : this.store.displayColumns();
    const descriptions = this.store.columnDescriptions();
    return cols.map((col) => {
      const description = col.description || descriptions.get(col.key);
      return description ? {...col, description} : col;
    });
  });

  /** Tri-state checkbox selection state ({all, some}) for the current group page. */
  readonly selectionState = computed(() => {
    const items = this.rowItems();
    let selectedCount = 0;
    for (const item of items) {
      if (item.selected) selectedCount++;
    }
    return {
      all: items.length > 0 && selectedCount === items.length,
      some: selectedCount > 0 && selectedCount < items.length,
    };
  });

  /** Formatted range text for the inner group pagination footer (e.g. "1–25 of 100"). */
  readonly innerRangeText = computed<string>(() => {
    const data = this.groupState()?.data;
    return formatPaginationRange(data?.rangeStart, data?.rangeEnd, data?.total);
  });
}
