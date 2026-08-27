import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatIconModule} from '@angular/material/icon';

import {
  Column,
  FleetGroup,
  Row,
} from '../../../../../core/models/search';
import {
  SearchCellPipe,
  UtilPctPipe,
  UtilWidthPipe,
} from '../../../utils';
import {SearchCellComponent} from '../common/search_cell/search_cell';

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
    SearchCellPipe,
    UtilPctPipe,
    UtilWidthPipe,
  ],
})
export class FleetGroupCardComponent {
  /** Fleet group header card data object. */
  readonly group = input.required<FleetGroup>();

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

  getRowId(row: Row | undefined): string {
    if (!row) return '';
    const rawId = row.id || '';
    if (
      rawId &&
      rawId !== '000000000000' &&
      rawId !== '00000000-0000-0000-0000-000000000000'
    ) {
      return rawId;
    }
    const c0 = row.cells?.[0];
    if (c0) {
      const link = c0.link;
      const target = link?.target;
      const cellText =
        target?.device?.id ||
        target?.host?.hostName ||
        link?.text ||
        c0.text?.value;
      if (cellText) return String(cellText);
    }
    return rawId;
  }

  isGroupPageAllSelected(rows: Row[] | undefined): boolean {
    if (!rows || rows.length === 0) return false;
    const selected = this.selectedSet();
    return rows.every((r) => selected.has(this.getRowId(r)));
  }
}
