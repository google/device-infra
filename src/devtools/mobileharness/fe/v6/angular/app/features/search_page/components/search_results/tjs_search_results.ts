import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSelectModule} from '@angular/material/select';
import {MatTableModule} from '@angular/material/table';
import {RouterLink} from '@angular/router';

import {Cell, Column, NavTarget} from '../../../../core/models/search_common';
import {OverflowChipListComponent} from '../../../../shared/components/overflow_chip_list/overflow_chip_list';
import {
  formatDuration,
  formatTime,
  getCell,
  getCellType,
  getRouterLink,
  getStatusClass,
  getTextValue,
} from '../../services/search_utils';
import {TjsSearchStore} from '../../services/tjs_search_store';

/** Component representing search results for TJS search entity types. */
@Component({
  selector: 'app-tjs-search-results',
  standalone: true,
  templateUrl: './tjs_search_results.ng.html',
  styleUrl: './search_results.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatMenuModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    OverflowChipListComponent,
    RouterLink,
  ],
})
export class TjsSearchResultsComponent {
  readonly store = inject(TjsSearchStore);

  // Context Delegates
  readonly columns = this.store.displayColumns;
  readonly rows = this.store.rows;
  readonly entity = this.store.entity;
  readonly pageIndex = this.store.pageIndex;
  readonly pageSize = this.store.pageSize;
  readonly density = this.store.density;
  readonly isLoading = this.store.isLoading;

  // Derived Signals (computed)
  readonly columnsToDisplay = computed<string[]>(() => {
    return this.columns().map((col: Column) => col.key);
  });

  prevPage() {
    this.store.prevPage();
  }

  nextPage() {
    this.store.nextPage();
  }

  // --- Template Helper Methods ---
  getRowPropertyValue(
    row: Record<string, Cell | string | string[]> | undefined,
    colKey: string,
  ): Cell | string | string[] | null {
    if (!row) return null;
    return row[colKey] !== undefined && row[colKey] !== null
      ? row[colKey]
      : null;
  }

  getCell(
    row: Record<string, Cell | string | string[]> | undefined,
    colKey: string,
    customColumns?: Column[],
  ): Cell | null {
    return getCell(row, colKey, customColumns || this.columns());
  }
  getTextValue = getTextValue;
  getCellValue(cell: (Cell & {values?: string[]}) | undefined): string {
    if (!cell) return '';
    if (Array.isArray(cell.values)) return cell.values.join(', ');
    if (cell.value !== undefined && cell.value !== null) {
      return String(cell.value);
    }
    const txt = getTextValue(cell);
    return txt || '';
  }
  getCellListValues(
    cell: (Cell & {values?: string[]}) | undefined,
  ): string[] | null {
    if (!cell) return null;
    if (Array.isArray(cell.values) && cell.values.length > 0) {
      const res = cell.values.map((v) => String(v)).filter(Boolean);
      return res.length > 0 ? res : null;
    }
    if (typeof cell.value === 'string' && cell.value.trim()) {
      const res = cell.value
        .split(',')
        .map((v: string) => v.trim())
        .filter(Boolean);
      return res.length > 0 ? res : null;
    }
    return null;
  }
  getCellType = getCellType;
  getStatusClass = getStatusClass;
  getRouterLink(target: NavTarget | string | undefined): string[] | null {
    return getRouterLink(
      {link: {target: target as unknown as NavTarget}},
      this.entity(),
    );
  }
  getRouterLinkFromTarget(cell: Cell): string[] | null {
    return getRouterLink(cell, this.entity());
  }
  formatTime = formatTime;
  formatDuration = formatDuration;

  getColumnDisplayName(colKey: string, customColumns?: Column[]): string {
    const cols =
      customColumns && customColumns.length > 0
        ? customColumns
        : this.columns();
    const col = cols.find((c: Column) => c.key === colKey);
    return col ? col.displayName : colKey;
  }

  getChipsValues(
    cell: (Cell & {values?: string[]}) | undefined,
  ): string[] | null {
    if (!cell) return null;
    if (Array.isArray(cell.chips?.values) && cell.chips.values.length > 0) {
      return cell.chips.values;
    }
    if (Array.isArray(cell.values) && cell.values.length > 0) {
      return cell.values;
    }
    if (typeof cell.value === 'string' && cell.value.trim()) {
      return [cell.value.trim()];
    }
    return null;
  }

  isArray(val: unknown): val is Array<string | Cell> {
    return Array.isArray(val);
  }

  getTjsNounPlural(): string {
    const e = this.entity();
    if (e === 'tests') return 'Tests';
    if (e === 'jobs') return 'Jobs';
    if (e === 'sessions') return 'Sessions';
    return e;
  }

  getTjsStatusClass(status: string): string {
    const u = (status || '').toUpperCase();
    if (
      [
        'PASS',
        'PASSED',
        'DONE',
        'FINISHED',
        'SUCCEEDED',
        'COMPLETED',
        'HEALTHY',
        'IDLE',
        'READY',
      ].includes(u)
    ) {
      return 'status-ok';
    }
    if (
      [
        'FAIL',
        'ERROR',
        'FAILED',
        'ABORT',
        'TIMEOUT',
        'CANCELLED',
        'EXPIRED',
        'BUSY',
        'OFFLINE',
      ].includes(u)
    ) {
      return 'status-error';
    }
    if (['RUNNING', 'ACTIVE'].includes(u)) return 'status-active';
    return 'status-neutral';
  }
}
