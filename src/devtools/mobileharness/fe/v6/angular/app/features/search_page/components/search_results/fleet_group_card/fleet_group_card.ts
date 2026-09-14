import {DecimalPipe} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatIconModule} from '@angular/material/icon';

import {Column, FleetGroup, Row} from '../../../../../core/models/search';
import {KeyDescriptionTooltipDirective} from '../../../../../shared/directives/key_description_tooltip/key_description_tooltip';
import {GroupPageState} from '../../../models';
import {
  formatPaginationRange,
  getDeviceTargetKey,
  getRowsSelectionState,
} from '../../../utils';
import {SearchCellComponent} from '../common/search_cell/search_cell';
import {SearchPaginationComponent} from '../common/search_pagination/search_pagination';

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
    KeyDescriptionTooltipDirective,
    SearchCellComponent,
    SearchPaginationComponent,
  ],
})
export class FleetGroupCardComponent {
  /** Fleet group header card data object. */
  readonly group = input.required<FleetGroup>();

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

  /** Whether this group card accordion is currently open/expanded. */
  readonly isOpen = input<boolean>(false);

  /** Async state for lazy-loaded group row data. */
  readonly groupState = input<GroupPageState | undefined>();

  /** Target search entity name ('devices' or 'hosts'). */
  readonly entity = input<string>('devices');

  /** Default table column definitions. */
  readonly columns = input<Column[]>([]);

  /** Descriptions of the column keys that have one, shown as header tooltips. */
  readonly columnDescriptions = input<ReadonlyMap<string, string>>(new Map());

  /** Currently selected row ID collection. */
  readonly selectedSet = input<
    ReadonlySet<string> | ReadonlyMap<string, unknown>
  >(new Set());

  /** Event emitted when user clicks group header button to toggle expansion. */
  readonly toggleGroup = output<string>();

  /** Event emitted when user clicks select-all or individual row checkbox inside group. */
  readonly toggleSelectRows = output<Row[]>();

  /** Event emitted when user requests inner page change (token passed). */
  readonly loadGroupPage = output<string | undefined>();

  /** Group rows with pre-computed composite tracking keys and selection state. */
  readonly rowItems = computed(() => {
    const rows = this.groupState()?.data?.rows ?? [];
    const selected = this.selectedSet();
    return rows.map((row) => {
      const key = getDeviceTargetKey(row);
      return {row, key, selected: selected.has(key)};
    });
  });

  /** Effective columns for the inner table (from state or default fallback). */
  readonly innerColumns = computed<Column[]>(() => {
    const cols = this.groupState()?.data?.columns;
    return cols && cols.length > 0 ? cols : this.columns();
  });

  /** Tri-state checkbox selection state ({all, some}) for the current group page. */
  readonly selectionState = computed(() =>
    getRowsSelectionState(
      this.groupState()?.data?.rows ?? [],
      this.selectedSet(),
    ),
  );

  /** Formatted range text for the inner group pagination footer (e.g. "1–25 of 100"). */
  readonly innerRangeText = computed<string>(() => {
    const data = this.groupState()?.data;
    return formatPaginationRange(data?.rangeStart, data?.rangeEnd, data?.total);
  });
}
