import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  output,
  signal,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatCardModule} from '@angular/material/card';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSelectModule} from '@angular/material/select';
import {MatTableModule} from '@angular/material/table';
import {RouterLink} from '@angular/router';

import {
  Cell,
  Column,
  FleetGroup,
  FleetPromotedGroupByKey,
  Indicator,
  NavTarget,
  Row,
} from '../../../../core/models/search';
import {OverflowChipListComponent} from '../../../../shared/components/overflow_chip_list/overflow_chip_list';
import {FleetSearchStore} from '../../services/fleet_search_store';
import {
  getCell,
  getCellType,
  getRouterLink,
  getStatusClass,
  getTextValue,
} from '../../services/search_utils';

type SearchResultRow = Row | Record<string, unknown>;

/** Component representing search results for lab fleet (devices/hosts). */
@Component({
  selector: 'app-fleet-search-results',
  standalone: true,
  templateUrl: './fleet_search_results.ng.html',
  styleUrl: './search_results.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatCheckboxModule,
    MatIconModule,
    MatMenuModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    OverflowChipListComponent,
    RouterLink,
  ],
})
export class FleetSearchResultsComponent {
  readonly store = inject(FleetSearchStore);

  // Context Delegates
  readonly columns = this.store.displayColumns;
  readonly rows = this.store.rows;
  readonly groupedResults = this.store.groupedResults;
  readonly expandedGroupPages = this.store.expandedGroupPages;
  readonly entity = this.store.entity;
  readonly fleet = this.store.fleet;
  readonly groupByKeys = this.store.groupByKeys;
  readonly totalCount = this.store.effectiveTotalCount;
  readonly rangeStart = this.store.effectiveRangeStart;
  readonly rangeEnd = this.store.effectiveRangeEnd;
  readonly pageIndex = this.store.pageIndex;
  readonly pageSize = this.store.pageSize;
  readonly density = this.store.density;
  readonly selected = this.store.selectedDevices;
  readonly sortColumn = this.store.sortColumn;
  readonly sortAsc = this.store.sortAsc;
  readonly visibleColumns = this.store.visibleColumns;
  readonly availableColumns = signal(this.store.availableColumns);

  // Action Outputs
  readonly refresh = output<void>();
  readonly export = output<void>();
  readonly quarantine = output<void>();
  readonly configureWifi = output<void>();

  toggleColumn(colKey: string) {
    this.store.toggleColumn(colKey);
  }

  prevPage() {
    this.store.prevPage();
  }

  nextPage() {
    this.store.nextPage();
  }

  // --- Local State for Group Accordion ---
  readonly openGroupIds = signal<Set<string>>(new Set());
  readonly groupSort = signal<string>('count:desc');

  readonly groupSortOptions = [
    {value: 'count:desc', labelTemplate: 'Most {entity}'},
    {value: 'count:asc', labelTemplate: 'Fewest {entity}'},
    {value: 'name:asc', labelTemplate: 'Name (A–Z)'},
    {value: 'name:desc', labelTemplate: 'Name (Z–A)'},
    {value: 'util:desc', labelTemplate: 'Highest utilization'},
    {value: 'util:asc', labelTemplate: 'Lowest utilization'},
  ];

  getGroupSortText(labelTemplate: string): string {
    const ent = this.entity() || 'items';
    return labelTemplate.replace('{entity}', ent);
  }

  getGroupSortActiveLabel(): string {
    const activeVal = this.groupSort();
    const hit = this.groupSortOptions.find((o) => o.value === activeVal);
    return hit ? this.getGroupSortText(hit.labelTemplate) : 'Sort';
  }

  setGroupSort(value: string) {
    this.groupSort.set(value);
  }

  // --- Derived Signals (computed) ---
  readonly columnsToDisplay = computed<string[]>(() => {
    const cols = this.columns().map((col: Column) => col.key);
    if (cols.length === 0) return [];
    return ['checkbox', ...cols];
  });

  readonly showScopeSwitcher = computed(() => {
    const e = this.entity();
    return e === 'devices' || e === 'hosts';
  });

  readonly totalPages = computed(() => {
    return Math.ceil(this.totalCount() / this.pageSize()) || 1;
  });

  readonly groupKeysText = computed(() => {
    const keys = this.groupedResults()?.groupByKeys || [];
    return keys
      .map((k: FleetPromotedGroupByKey) => k.displayName || k.key)
      .join(' × ');
  });

  readonly sortedGroups = computed<FleetGroup[]>(() => {
    const rawGroups = this.groupedResults()?.groups;
    if (!rawGroups || rawGroups.length === 0) return [];
    const groups = [...rawGroups];
    const sortVal = this.groupSort();

    return groups.sort((a, b) => {
      switch (sortVal) {
        case 'count:desc':
          return (b.itemCount || 0) - (a.itemCount || 0);
        case 'count:asc':
          return (a.itemCount || 0) - (b.itemCount || 0);
        case 'name:asc': {
          const nameA = this.getGroupTitle(a.values);
          const nameB = this.getGroupTitle(b.values);
          return nameA.localeCompare(nameB);
        }
        case 'name:desc': {
          const nameA = this.getGroupTitle(a.values);
          const nameB = this.getGroupTitle(b.values);
          return nameB.localeCompare(nameA);
        }
        case 'util:desc': {
          const utilA =
            (a.utilization?.busy || 0) / Math.max(1, a.utilization?.total || 1);
          const utilB =
            (b.utilization?.busy || 0) / Math.max(1, b.utilization?.total || 1);
          return utilB - utilA;
        }
        case 'util:asc': {
          const utilA =
            (a.utilization?.busy || 0) / Math.max(1, a.utilization?.total || 1);
          const utilB =
            (b.utilization?.busy || 0) / Math.max(1, b.utilization?.total || 1);
          return utilA - utilB;
        }
        default:
          return 0;
      }
    });
  });

  toggleGroup(groupId: string) {
    const current = new Set(this.openGroupIds());
    if (current.has(groupId)) {
      current.delete(groupId);
    } else {
      current.add(groupId);
      this.store.onLoadGroupRows(groupId);
    }
    this.openGroupIds.set(current);
  }

  getGroupTitle(values?: string[]): string {
    if (!values || values.length === 0) return '(no value)';
    return values
      .map((v) =>
        v !== null && v !== undefined && v !== '' ? v : '(no value)',
      )
      .join(' · ');
  }

  getUtilPct(val: number | undefined, total: number | undefined): number {
    if (!val || !total || total <= 0) return 0;
    return Math.round((100 * val) / total);
  }

  getUtilWidth(val: number | undefined, total: number | undefined): number {
    if (!val || !total || total <= 0) return 0;
    return (100 * val) / total;
  }

  getRowId(row: SearchResultRow | undefined): string {
    if (!row) return '';
    const r = row as unknown as Record<string, unknown>;
    return String(r['id'] || r['_id'] || '');
  }

  isGroupPageAllSelected(rows: SearchResultRow[] | undefined): boolean {
    if (!rows || rows.length === 0) return false;
    return rows.every((r) => this.selected().has(this.getRowId(r)));
  }

  toggleSelectGroupPage(rows: SearchResultRow[] | undefined) {
    if (!rows || rows.length === 0) return;
    const current = new Set(this.selected());
    const allSelected = rows.every((r) => current.has(this.getRowId(r)));
    if (allSelected) {
      rows.forEach((r) => current.delete(this.getRowId(r)));
    } else {
      rows.forEach((r) => current.add(this.getRowId(r)));
    }
    this.selected.set(current);
  }

  // --- Template Helper Methods ---
  isColumnVisible(key: string): boolean {
    return this.visibleColumns().has(key);
  }

  clearSelection() {
    this.selected.set(new Set());
  }

  getRowPropertyValue(
    row: SearchResultRow | undefined,
    colKey: string,
  ): Cell | string | string[] | null {
    if (!row) return null;
    const r = row as unknown as Record<string, Cell | string | string[]>;
    return r[colKey] !== undefined && r[colKey] !== null ? r[colKey] : null;
  }

  getCell(
    row: SearchResultRow | undefined,
    colKey: string,
    customColumns?: Column[],
  ): Cell | null {
    const r = row as unknown as Record<string, Cell | string | string[]>;
    return getCell(r, colKey, customColumns || this.columns());
  }
  getTextValue = getTextValue;
  getCellType = getCellType;
  getStatusClass = (status: string, indicator?: unknown) =>
    getStatusClass({
      status: {text: status},
      indicator: indicator as unknown as Indicator,
    });
  getRouterLink = (target: NavTarget | string | undefined) =>
    getRouterLink(
      {link: {target: target as unknown as NavTarget}},
      this.entity(),
    );

  // --- Selection Handlers ---
  isAllSelected(): boolean {
    const visibleIds = this.rows().map((r) =>
      this.getRowId(r as SearchResultRow),
    );
    return (
      visibleIds.length > 0 &&
      visibleIds.every((id: string) => this.selected().has(id))
    );
  }

  isSomeSelected(): boolean {
    const visibleIds = this.rows().map((r) =>
      this.getRowId(r as SearchResultRow),
    );
    const selectedCount = visibleIds.filter((id: string) =>
      this.selected().has(id),
    ).length;
    return selectedCount > 0 && selectedCount < visibleIds.length;
  }

  toggleSelectAll() {
    const current = this.selected();
    const visibleIds = this.rows().map((r) =>
      this.getRowId(r as SearchResultRow),
    );
    const allSelected = visibleIds.every((id: string) => current.has(id));
    const nextSet = new Set(current);

    if (allSelected) {
      visibleIds.forEach((id: string) => nextSet.delete(id));
    } else {
      visibleIds.forEach((id: string) => nextSet.add(id));
    }
    this.selected.set(nextSet);
  }

  toggleSelectRow(id: string) {
    const nextSet = new Set(this.selected());
    if (nextSet.has(id)) {
      nextSet.delete(id);
    } else {
      nextSet.add(id);
    }
    this.selected.set(nextSet);
  }

  isSortColumn(colKey: string): boolean {
    const sortCol = this.sortColumn();
    if (!sortCol) return false;
    if (sortCol === colKey) return true;
    const isIdOrUuidSort = ['id', 'uuid', 'field::uuid'].includes(sortCol);
    const isIdOrUuidCol = ['id', 'uuid', 'field::uuid'].includes(colKey);
    if (isIdOrUuidSort && isIdOrUuidCol) return true;
    return false;
  }

  onHeaderClick(colKey: string) {
    let asc = true;
    if (this.isSortColumn(colKey)) {
      asc = !this.sortAsc();
    }
    this.sortColumn.set(colKey);
    this.sortAsc.set(asc);
  }

  getColumnDisplayName(colKey: string, customColumns?: Column[]): string {
    const cols =
      customColumns && customColumns.length > 0
        ? customColumns
        : this.columns();
    const col = cols.find((c: Column) => c.key === colKey);
    if (col) return col.displayName;
    const avail = this.availableColumns();
    const foundAvail = avail.find(
      (a: {key: string; label: string}) => a.key === colKey,
    );
    if (foundAvail) {
      return foundAvail.label;
    }
    if (colKey.toLowerCase().includes('type')) {
      return 'Device Types';
    }
    if (colKey.toLowerCase().includes('owner')) {
      return 'Owners';
    }
    return colKey;
  }

  getChipsValues(cell: Cell | undefined): string[] {
    if (!cell || !cell.chips) {
      return [];
    }
    return cell.chips.values || [];
  }

  isArray(val: unknown): val is Array<string | Cell> {
    return Array.isArray(val);
  }
}
