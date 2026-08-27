import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  output,
  signal,
} from '@angular/core';
import {MatCardModule} from '@angular/material/card';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSelectModule} from '@angular/material/select';
import {MatTableModule} from '@angular/material/table';
import {RouterLink} from '@angular/router';

import {
  Column,
  FleetGroup,
  FleetPromotedGroupByKey,
  Row,
} from '../../../../../core/models/search';
import {FleetSearchStore} from '../../../services/fleet_search_store';
import {getRowId, SearchCellPipe} from '../../../utils';

import {DensityDropdownComponent} from '../common/density_dropdown/density_dropdown';
import {FleetGroupCardComponent} from '../fleet_group_card/fleet_group_card';
import {SearchPaginationComponent} from '../common/search_pagination/search_pagination';
import {SearchCellComponent} from '../common/search_cell/search_cell';

/** Component representing search results for lab fleet (devices/hosts). */
@Component({
  selector: 'app-fleet-search-results',
  standalone: true,
  templateUrl: './fleet_search_results.ng.html',
  styleUrl: './fleet_search_results.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatCardModule,
    MatCheckboxModule,
    MatIconModule,
    MatMenuModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    RouterLink,
    DensityDropdownComponent,
    FleetGroupCardComponent,
    SearchPaginationComponent,
    SearchCellComponent,
    SearchCellPipe,
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
  readonly selected = this.store.selectedItems;
  readonly sortColumn = this.store.sortColumn;
  readonly sortAsc = this.store.sortAsc;
  readonly visibleColumns = this.store.visibleColumns;
  readonly availableColumns = this.store.availableColumns;

  // Select-All-Matching Banner State
  readonly selectAllMatching = signal(false);
  readonly excluded = signal<Set<string>>(new Set());

  readonly selectedCount = computed(() => {
    if (this.selectAllMatching()) {
      return Math.max(0, this.totalCount() - this.excluded().size);
    }
    return this.selected().size;
  });

  readonly excludedCount = computed(() => {
    return this.excluded().size;
  });

  /**
   * Whether to show the select-all-matching banner.
   *
   * This is shown when the user has selected all rows on the current page,
   * and there are more rows available in the search results.
   */
  readonly showSelectAllMatchingBanner = computed(() => {
    if (this.groupByKeys().length > 0) return false;
    if (this.selectAllMatching()) return true;

    const visibleIds = this.visibleRowIds();
    const allPageSelected =
      visibleIds.length > 0 &&
      visibleIds.every((id: string) => this.selected().has(id));

    return allPageSelected && this.totalCount() > visibleIds.length;
  });

  // Action Outputs
  readonly refresh = output<void>();
  readonly export = output<void>();
  readonly quarantine = output<void>();
  readonly configureWifi = output<void>();
  readonly configure = output<void>();


  prevPage() {
    this.store.prevPage();
  }

  nextPage() {
    this.store.nextPage();
  }

  // --- Group Accordion State (Delegated to Store) ---
  readonly openGroupIds = this.store.openGroupIds;
  readonly groupSort = this.store.groupSort;
  readonly groupSortOptions = this.store.groupSortOptions;

  readonly groupSortActiveLabel = computed<string>(() => {
    const activeVal = this.groupSort();
    const options = this.groupSortOptions();
    const hit = options.find((o) => o.value === activeVal);
    if (hit) return hit.label;

    if (activeVal === 'name:asc' || activeVal === 'name:desc') {
      const isAsc = activeVal === 'name:asc';
      const firstGb = this.store.groupByKeys()[0];
      const meta = firstGb ? this.store.keyMetadataMap().get(firstGb) : undefined;
      const name = meta?.keyDisplayName || firstGb || 'Name';
      return `${name} (${isAsc ? 'A–Z' : 'Z–A'})`;
    }

    return 'Sort';
  });

  readonly groupedRangeText = computed<string>(() => {
    const res = this.groupedResults();
    if (!res) return '';
    const start = res.rangeStart || 0;
    const end = res.rangeEnd || 0;
    const total = (res.totalGroups || 0).toLocaleString();
    return `${start}–${end} of ${total} groups`;
  });

  setGroupSort(value: string) {
    this.store.setGroupSort(value);
  }

  // --- Derived Signals (computed) ---
  readonly columnsToDisplay = computed<string[]>(() => {
    const cols = this.columns().map((col: Column) => col.key);
    if (cols.length === 0) return [];
    return ['checkbox', ...cols];
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

  readonly groups = computed<FleetGroup[]>(() => {
    return this.groupedResults()?.groups || [];
  });

  toggleGroup(groupId: string) {
    this.store.toggleGroup(groupId);
  }

  readonly getRowId = getRowId;

  toggleSelectGroupPage(rows: Row[] | undefined) {
    if (!rows || rows.length === 0) return;
    const current = new Set(this.selected());
    const allSelected = rows.every((r) => current.has(this.getRowId(r)));
    if (allSelected) {
      rows.forEach((r) => { current.delete(this.getRowId(r)); });
    } else {
      rows.forEach((r) => { current.add(this.getRowId(r)); });
    }
    this.selected.set(current);
  }

  // --- Template Helper Methods ---
  clearSelection() {
    this.selected.set(new Set());
    this.selectAllMatching.set(false);
    this.excluded.set(new Set());
  }

  /**
   * Computed list of unique row IDs currently visible on the page.
   * Disambiguates duplicate or missing IDs by appending index counter suffixes
   * to guarantee row uniqueness in selection Sets.
   */
  readonly visibleRowIds = computed<string[]>(() => {
    const rows = this.rows();
    const idCounts = new Map<string, number>();
    return rows.map((r, i) => {
      let id = this.getRowId(r);
      if (!id) id = `row_${i}`;
      const count = idCounts.get(id) || 0;
      idCounts.set(id, count + 1);
      // Append counter suffix for duplicate IDs on the same page
      return count > 0 ? `${id}_${count}` : id;
    });
  });

  onPageSizeChange(newSize: number) {
    this.store.pageSize.set(newSize);
    this.store.pageIndex.set(0);
    this.store.pageToken.set('');
    this.clearSelection();
  }

  // --- Selection Handlers ---
  isAllSelected(): boolean {
    if (this.selectAllMatching()) {
      return this.excluded().size === 0;
    }
    const visibleIds = this.visibleRowIds();
    return (
      visibleIds.length > 0 &&
      visibleIds.every((id: string) => this.selected().has(id))
    );
  }

  isSomeSelected(): boolean {
    if (this.selectAllMatching()) {
      return (
        this.excluded().size > 0 && this.excluded().size < this.totalCount()
      );
    }
    const visibleIds = this.visibleRowIds();
    const selectedCount = visibleIds.filter((id: string) =>
      this.selected().has(id),
    ).length;
    return selectedCount > 0 && selectedCount < visibleIds.length;
  }

  toggleSelectAll() {
    if (this.selectAllMatching()) {
      this.clearSelection();
      return;
    }
    const visibleIds = this.visibleRowIds();
    const allSelected = visibleIds.every((id: string) =>
      this.selected().has(id),
    );
    if (allSelected) {
      this.clearSelection();
    } else {
      const nextSet = new Set(this.selected());
      visibleIds.forEach((id: string) => { nextSet.add(id); });
      this.selected.set(nextSet);
    }
  }

  toggleSelectRow(id: string) {
    if (this.selectAllMatching()) {
      const currentExcluded = new Set(this.excluded());
      if (currentExcluded.has(id)) {
        currentExcluded.delete(id);
      } else {
        currentExcluded.add(id);
      }
      this.excluded.set(currentExcluded);
      if (currentExcluded.size === this.totalCount()) {
        this.clearSelection();
      }
    } else {
      const nextSet = new Set(this.selected());
      if (nextSet.has(id)) {
        nextSet.delete(id);
      } else {
        nextSet.add(id);
      }
      this.selected.set(nextSet);
    }
  }

  isRowSelected(id: string): boolean {
    if (this.selectAllMatching()) {
      return !this.excluded().has(id);
    }
    return this.selected().has(id);
  }

  selectActionAllMatching() {
    this.selectAllMatching.set(true);
    this.excluded.set(new Set());
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
}
