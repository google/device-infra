import {DecimalPipe} from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  Injector,
  input,
} from '@angular/core';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';

import {Column, FleetGroup} from '../../../../../core/models/search';
import {KeyDescriptionTooltipDirective} from '../../../../../shared/directives/key_description_tooltip/key_description_tooltip';
import {
  DEFAULT_GROUP_EXPAND_PAGE_SIZE,
  GroupPageState,
} from '../../../models';
import {FleetSearchStore} from '../../../services/fleet_search_store';
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
    MatProgressBarModule,
    KeyDescriptionTooltipDirective,
    SearchCellComponent,
    SearchPaginationComponent,
  ],
})
export class FleetGroupCardComponent {
  readonly store = inject(FleetSearchStore);
  private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  /** Fleet group header card data object. */
  readonly group = input.required<FleetGroup>();

  /** Default page size for expanded group inner table. */
  protected readonly defaultPageSize = DEFAULT_GROUP_EXPAND_PAGE_SIZE;

  /** Whether this group card accordion is currently open/expanded. */
  readonly isOpen = computed<boolean>(() =>
    this.store.openGroupIds().has(this.group().groupId),
  );

  /** Whether this group card is the currently focused expanded group. */
  readonly isFocused = computed<boolean>(
    () =>
      this.isOpen() &&
      this.store.focusedGroupId() === this.group().groupId,
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
  readonly selectionState = computed(() =>
    getRowsSelectionState(
      this.groupState()?.data?.rows ?? [],
      this.store.selectedItems(),
    ),
  );

  /** Formatted range text for the inner group pagination footer (e.g. "1–25 of 100"). */
  readonly innerRangeText = computed<string>(() => {
    const data = this.groupState()?.data;
    return formatPaginationRange(data?.rangeStart, data?.rangeEnd, data?.total);
  });

  /** Toggles this group card and scrolls it into view when expanding. */
  onToggleGroup(): void {
    const willOpen = !this.isOpen();
    this.store.toggleGroup(this.group().groupId);
    if (willOpen) {
      this.scrollCardIntoView();
    }
  }

  private scrollCardIntoView(): void {
    afterNextRender(
      () => {
        const hostEl = this.elementRef.nativeElement;
        const accordion = hostEl.closest('.rt-accordion');
        if (!accordion) return;
        const offset =
          hostEl.getBoundingClientRect().top -
          accordion.getBoundingClientRect().top;
        accordion.scrollTo({
          top: Math.max(0, accordion.scrollTop + offset),
          behavior: 'smooth',
        });
      },
      {injector: this.injector},
    );
  }
}
