import {CommonModule} from '@angular/common';
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
import {SearchCellComponent} from '../common/search_cell/search_cell';
import {SearchPaginationComponent} from '../common/search_pagination/search_pagination';

/** Data state structure for an expanded group card page. */
export interface GroupPageState {
  readonly loading?: boolean;
  readonly error?: string;
  readonly data?: {
    readonly rows?: Row[];
    readonly columns?: Column[];
    readonly rangeStart?: number;
    readonly rangeEnd?: number;
    readonly total?: number;
    readonly prevPageToken?: string;
    readonly nextPageToken?: string;
  };
}

/** Standalone component representing a single expandable group card in grouped results view. */
@Component({
  selector: 'app-fleet-group-card',
  standalone: true,
  templateUrl: './fleet_group_card.ng.html',
  styleUrl: './fleet_group_card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatCheckboxModule,
    MatIconModule,
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

    const calcPct = (v?: number) =>
      Math.min(100, Math.max(0, Math.round((100 * (v || 0)) / total)));
    const calcWidth = (v?: number) =>
      Math.min(100, Math.max(0, (100 * (v || 0)) / total));

    return {
      busyCount: util.busy || 0,
      busyPct: calcPct(util.busy),
      busyWidth: calcWidth(util.busy),

      idleCount: util.idle || 0,
      idlePct: calcPct(util.idle),
      idleWidth: calcWidth(util.idle),

      otherCount: util.other || 0,
      otherPct: calcPct(util.other),
      otherWidth: calcWidth(util.other),
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

  /** Currently selected row ID set. */
  readonly selectedSet = input<Set<string>>(new Set());

  /** Event emitted when user clicks group header button to toggle expansion. */
  readonly toggleGroup = output<string>();

  /** Event emitted when user clicks select-all checkbox for a group's page. */
  readonly toggleSelectGroupPage = output<Row[]>();

  /** Event emitted when user clicks checkbox for an individual row inside group. */
  readonly toggleSelectRow = output<string>();

  /** Event emitted when user requests inner page change (token passed). */
  readonly loadGroupPage = output<string | undefined>();

  /** Group rows for the current expanded group page. */
  readonly groupRows = computed<Row[]>(
    () => this.groupState()?.data?.rows || [],
  );

  /** Whether all rows on the current group page are selected. */
  readonly isGroupPageAllSelected = computed<boolean>(() => {
    const rows = this.groupRows();
    if (rows.length === 0) return false;
    const selected = this.selectedSet();
    return rows.every((r) => selected.has(r.id));
  });

  /** Whether some (but not all) rows on the current group page are selected. */
  readonly isGroupPageSomeSelected = computed<boolean>(() => {
    const rows = this.groupRows();
    if (rows.length === 0) return false;
    const selected = this.selectedSet();
    const count = rows.filter((r) => selected.has(r.id)).length;
    return count > 0 && count < rows.length;
  });

  /** Formatted range text for the inner group pagination footer (e.g. "1–25 of 100"). */
  readonly innerRangeText = computed<string>(() => {
    const data = this.groupState()?.data;
    if (!data) return '';
    const start = (data.rangeStart || 0).toLocaleString();
    const end = (data.rangeEnd || 0).toLocaleString();
    const total = (data.total || 0).toLocaleString();
    return `${start}–${end} of ${total}`;
  });
}
