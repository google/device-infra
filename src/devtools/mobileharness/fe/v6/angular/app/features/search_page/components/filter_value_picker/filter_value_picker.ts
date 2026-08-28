import {ConnectionPositionPair, OverlayModule} from '@angular/cdk/overlay';
import {
  CdkVirtualScrollViewport,
  ScrollingModule,
} from '@angular/cdk/scrolling';
import {CommonModule} from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  linkedSignal,
  signal,
  viewChild,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';

import {TooltipIfTruncatedDirective} from '../../../../shared/directives/tooltip_if_truncated/tooltip_if_truncated';
import {AdvancedMatchMode, PickerValueItem} from '../../models';
import {SearchPageStore} from '../../services/search_page_store';
import {
  buildValuePickerApplyEvent,
  computeFilteredAndSortedValues,
  computePinnedValues,
  getPacificTimezoneName,
  parseDateRange,
} from '../../utils';
import {AdvancedMatchView} from './advanced_match_view/advanced_match_view';

/**
 * Unified facade value picker component with embedded CDK Connected Overlay.
 * Manages local draft state for filtering, sorting, selecting, and advanced match modes.
 */
@Component({
  selector: 'app-filter-value-picker',
  standalone: true,
  templateUrl: './filter_value_picker.ng.html',
  styleUrl: './filter_value_picker.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    OverlayModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    ScrollingModule,
    TooltipIfTruncatedDirective,
    AdvancedMatchView,
  ],
})
export class FilterValuePicker {
  /** Global search page store reference injected via Angular dependency injection. */
  readonly store = inject(SearchPageStore);

  /** Active value picker configuration signal from store. */
  readonly config = this.store.pickerConfig;
  /** Effective picker state (candidate values, selected items, loading status) from store. */
  readonly state = this.store.effectivePickerState;

  /** Reference to the search input element inside the picker popover. */
  readonly searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  /** Angular CDK Overlay positioning strategies (bottom-start preferred, top-start fallback). */
  readonly pickerOverlayPositions: ConnectionPositionPair[] = [
    {
      originX: 'start',
      originY: 'bottom',
      overlayX: 'start',
      overlayY: 'top',
      offsetY: 6,
    },
    {
      originX: 'start',
      originY: 'top',
      overlayX: 'start',
      overlayY: 'bottom',
      offsetY: -6,
    },
  ];

  // ===================== Local Draft Reactive State =====================
  /** Set of selected value strings linked to store effective state. */
  readonly selectedSet = linkedSignal(
    () => new Set(this.state().selectedValues),
  );
  /** Set of manually staged custom free-text inputs added by user. */
  readonly stagedCustomInputs = linkedSignal({
    source: () => this.state().selectedValues,
    computation: () => new Set<string>(),
  });
  /** Polarity flag (true = IS NOT / exclude, false = IS / include). */
  readonly isNegated = linkedSignal(() => !!this.state().negated);
  /** Flag indicating whether advanced match view is active. */
  readonly isAdvanced = linkedSignal(() => !!this.state().advanced?.active);

  /** Advanced match mode ('prefix', 'suffix', 'contains', 'regex', etc.). */
  readonly advMode = linkedSignal<AdvancedMatchMode>(
    () => this.state().advanced?.mode || 'prefix',
  );
  /** Advanced mode text query. */
  readonly advText = linkedSignal(() => this.state().advanced?.text || '');
  /** Advanced mode array of value items. */
  readonly advValues = linkedSignal(() => this.state().advanced?.values || []);

  /** Parsed date range state object for date-range picker type. */
  readonly rangeState = linkedSignal(() =>
    parseDateRange(this.state().selectedValues),
  );
  /** 'From' timestamp string for date-range picker type. */
  readonly rangeFrom = linkedSignal(() => this.rangeState().from);
  /** 'To' timestamp string for date-range picker type. */
  readonly rangeTo = linkedSignal(() => this.rangeState().to);

  /** Selected values as an array for efficient multi-field consumption. */
  readonly selectedValuesList = computed(() =>
    Array.from(this.state().selectedValues),
  );

  /** Property key name for namedPair picker type. */
  readonly propName = linkedSignal(() => {
    const vals = this.selectedValuesList();
    if (this.config()?.needsName && vals.length >= 2) return vals[0] || '';
    return this.config()?.title || '';
  });
  /** Property value string for namedPair picker type. */
  readonly propVal = linkedSignal(() => {
    const vals = this.selectedValuesList();
    if (this.config()?.needsName && vals.length >= 2) return vals[1] || '';
    return vals.join(', ');
  });
  /** Text value string for plain text picker type. */
  readonly textVal = linkedSignal(() => this.selectedValuesList().join(', '));

  /** Local search query entered in popover filter input. */
  readonly searchQuery = signal<string>('');
  /** Whether the candidate items are plain values without count columns. */
  readonly isPlain = computed(() => {
    if (this.config()?.valuesType === 'plain') return true;
    const list = this.state().values || [];
    return (
      list.length > 0 &&
      list.every((v) => v.filtered === undefined && v.total === undefined)
    );
  });

  /** Active column to sort candidate items by ('value' | 'filtered' | 'total'), adhering to search_fleet.proto contract. */
  readonly sortBy = linkedSignal<boolean, 'value' | 'filtered' | 'total'>({
    source: () => this.isPlain(),
    computation: (plain) => (plain ? 'value' : 'filtered'),
  });
  /** Sort direction flag (true = ascending, false = descending), adhering to search_fleet.proto contract. */
  readonly sortAsc = linkedSignal<boolean, boolean>({
    source: () => this.isPlain(),
    computation: (plain) => (plain ? true : false),
  });
  /** Polarity menu open state flag. */
  readonly showPolarityMenu = signal<boolean>(false);
  /** More/Overflow options menu open state flag. */
  readonly showOverflowMenu = signal<boolean>(false);

  // ===================== Computed Derived Signals =====================
  /** Whether the target filter key represents plural items. */
  readonly isPlural = computed(() => !!this.config()?.isPlural);
  /** Positive verb text ('is' or 'are'). */
  readonly posVerb = computed(() => (this.isPlural() ? 'are' : 'is'));
  /** Negative verb text ('is not' or 'are not'). */
  readonly negVerb = computed(() => (this.isPlural() ? 'are not' : 'is not'));

  /** Active Pacific timezone abbreviation ('PDT' or 'PST'). */
  readonly pdtTimezone = getPacificTimezoneName();

  /** Whether the value picker should display in a compact width (non-list types or list with only values). */
  readonly isCompact = computed(() => {
    if (this.isAdvanced()) return false;
    const type = this.config()?.type;
    if (type && type !== 'list') return true;
    return this.isPlain();
  });

  /** Whether polarity negate toggle button is visible. */
  readonly showNegateToggle = computed(
    () => !!this.config()?.showNegateToggle && !this.isAdvanced(),
  );
  /** Whether advanced match options menu button is visible. */
  readonly showAdvancedMenu = computed(
    () => !!this.config()?.showAdvancedMenu && !this.isAdvanced(),
  );
  /** Whether main filter search input box is visible. */
  readonly showSearchInput = computed(
    () => this.config()?.showSearchInput !== false,
  );
  /** Whether row actions ('only', 'copy') are enabled. */
  readonly showRowActions = computed(() => !!this.config()?.showRowActions);

  /** Height of an individual candidate item row in pixels. */
  readonly itemSize = 36;

  /** Reference to the CDK virtual scroll viewport. */
  readonly virtualViewport = viewChild<CdkVirtualScrollViewport>(
    CdkVirtualScrollViewport,
  );

  /** Filtered and sorted candidate value items for list view. */
  readonly displayList = computed(() =>
    computeFilteredAndSortedValues({
      items: this.state().values || [],
      query: this.searchQuery(),
      sortBy: this.sortBy(),
      sortAsc: this.sortAsc(),
      stagedCustomInputs: this.stagedCustomInputs(),
      isLoading: !!this.state().loading,
    }),
  );

  /** Dynamic height for virtual scroll viewport based on item count and pinned state. */
  readonly viewportHeight = computed(() => {
    const count = this.displayList().length;
    const maxViewportHeight = this.pinnedValues().length > 0 ? 220 : 260;
    return Math.min(maxViewportHeight, Math.max(36, count * this.itemSize));
  });

  /** Pinned selected items pinned to top when list exceeds threshold. */
  readonly pinnedValues = computed(() =>
    computePinnedValues(
      this.state().values || [],
      this.selectedSet(),
      this.searchQuery(),
      !!this.state().loading,
    ),
  );

  /** Whether "Add custom input" row should be displayed while loading. */
  readonly showAddRow = computed(() => {
    if (!this.state().loading) return false;
    const q = this.searchQuery().trim().toLowerCase();
    if (!q) return false;

    const inSelected = Array.from(this.selectedSet()).some(
      (s) => s.toLowerCase() === q,
    );
    if (inSelected) return false;

    const inValues = (this.state().values || []).some(
      (v) => v.value.toLowerCase() === q || v.displayLabel.toLowerCase() === q,
    );
    return !inValues;
  });

  /** Footer status summary text indicating selected/excluded count or advanced values count. */
  readonly footerStatusText = computed(() => {
    if (this.isAdvanced()) {
      const isMulti =
        this.advMode() === 'exactly' || this.advMode() === 'at_least';
      if (!isMulti) {
        return this.advText().trim() ? '1 value added' : 'No values added';
      }
      const count = this.advValues().length;
      return count === 0
        ? 'No values added'
        : `${count} value${count > 1 ? 's' : ''} added`;
    }

    const type = this.config()?.type;
    if (type !== 'list' && type !== undefined) {
      return '';
    }

    const count = this.selectedSet().size;
    const verb = this.isNegated() ? 'excluded' : 'selected';
    return count === 0
      ? `No options ${verb}`
      : `${count} option${count > 1 ? 's' : ''} ${verb}`;
  });

  constructor() {
    afterNextRender(() => {
      this.searchInput()?.nativeElement.focus();
    });
  }

  // ===================== User Interaction Handlers =====================
  /** Toggles sort direction or changes the active sorting column. */
  toggleSort(col: 'value' | 'filtered' | 'total') {
    if (this.sortBy() === col) {
      this.sortAsc.set(!this.sortAsc());
      return;
    }
    this.sortBy.set(col);
    this.sortAsc.set(col === 'value');
  }

  /** Toggles selection state of a candidate value item. */
  toggleValue(item: PickerValueItem) {
    if (item.disabled) return;
    const next = new Set(this.selectedSet());
    if (next.has(item.value)) {
      next.delete(item.value);
    } else {
      next.add(item.value);
    }
    this.selectedSet.set(next);
  }

  /** Deselects all items and selects only the specified value item. */
  selectOnlyValue(val: string) {
    this.selectedSet.set(new Set([val]));
  }

  /** Copies string value to system clipboard. */
  copyValue(val: string) {
    if (!val) return;
    navigator.clipboard.writeText(val);
  }

  /** Clears all selected value items in local draft state. */
  clearAll() {
    this.selectedSet.set(new Set());
  }

  /** Stages a custom free-text input value entered by user. */
  addCustomInput(raw: string) {
    const q = raw.trim();
    if (!q) return;

    const nextSel = new Set(this.selectedSet());
    nextSel.add(q);
    this.selectedSet.set(nextSel);

    const nextStaged = new Set(this.stagedCustomInputs());
    nextStaged.add(q);
    this.stagedCustomInputs.set(nextStaged);
    this.searchQuery.set('');
  }

  /** Handles Enter keypress inside popover search input box. */
  onSearchEnter() {
    const q = this.searchQuery().trim();
    if (q) {
      this.addCustomInput(q);
      return;
    }
    this.onApply();
  }

  /** Dispatches ValuePickerApplyEvent to store and closes popover overlay. */
  onApply() {
    const isAdv = this.isAdvanced();
    const event = buildValuePickerApplyEvent({
      type: this.config()?.type,
      isAdvanced: isAdv,
      negated: isAdv ? false : this.isNegated(),
      selectedSet: this.selectedSet(),
      searchQuery: this.searchQuery(),
      advMode: this.advMode(),
      advText: this.advText(),
      advValues: this.advValues(),
      rangeFrom: this.rangeFrom(),
      rangeTo: this.rangeTo(),
      propName: this.propName(),
      propVal: this.propVal(),
      textVal: this.textVal(),
    });
    this.store.applyValuePicker(event);
    this.store.closeValuePicker();
  }

  /** Switches back from Advanced matching view to Simple list matching view. */
  onBackToSimple() {
    this.isAdvanced.set(false);
    this.isNegated.set(false);
  }

  /** Closes popover overlay without applying changes. */
  onCancel() {
    this.store.closeValuePicker();
  }

  /** TrackBy function for virtual scroll list items. */
  trackByItemValue(index: number, item: PickerValueItem): string {
    return item.value;
  }
}
