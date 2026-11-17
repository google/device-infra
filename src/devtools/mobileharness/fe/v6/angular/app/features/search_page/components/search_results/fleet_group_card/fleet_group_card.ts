/**
 * @fileoverview Expandable accordion card for a single Fleet group header, rendering utilization bars and a lazy-loaded inner device/host table.
 */

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
  GROUP_ROW_HEIGHT_ESTIMATE,
  GroupPageState,
  PAGE_SIZE_OPTIONS,
} from '../../../models';
import {FleetSearchStore} from '../../../services/fleet_search_store';
import {
  buildUtilizationViewModel,
  formatPaginationRange,
  getRowsSelectionState,
  resolveGroupInnerColumns,
  toSelectableRows,
} from '../../../utils';
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
  private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

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

  /** Whether this group card is the currently focused expanded group. */
  readonly isFocused = computed<boolean>(
    () => this.isOpen() && this.store.focusedGroupId() === this.group().groupId,
  );

  /** Async state for lazy-loaded group row data. */
  readonly groupState = computed<GroupPageState | undefined>(() =>
    this.store.expandedGroupPages().get(this.group().groupId),
  );

  /** Computed structured utilization view model for progress and legend bars. */
  readonly utilization = computed(() =>
    buildUtilizationViewModel(this.group().utilization),
  );

  /** Group rows with pre-computed composite tracking keys and selection state. */
  readonly rowItems = computed(() =>
    toSelectableRows(
      this.groupState()?.data?.rows ?? [],
      this.store.selectedItems(),
    ),
  );

  /** Effective columns for the inner table with pre-resolved header descriptions. */
  readonly innerColumns = computed<Column[]>(() =>
    resolveGroupInnerColumns(
      this.groupState()?.data?.columns,
      this.store.displayColumns(),
      this.store.columnDescriptions(),
    ),
  );

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

  /** Smoothly scrolls the parent `.rt-accordion` container to align this card at the top after expansion. */
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
