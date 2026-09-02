import {
  ConnectionPositionPair,
  OverlayModule,
} from '@angular/cdk/overlay';
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

import {
  AdvancedMatchMode,
  PickerValueItem,
} from '../../models/value_picker_models';
import {SearchPageStore} from '../../services/search_page_store';
import {AdvancedMatchView} from './advanced_match_view/advanced_match_view';
import {
  buildValuePickerApplyEvent,
  computeFilteredAndSortedValues,
  computePinnedValues,
  parseDateRange,
} from '../../utils';

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
  readonly selectedSet = linkedSignal(() => new Set(this.state().selectedValues));
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

  /** Property key name for namedPair picker type. */
  readonly propName = linkedSignal(() => {
    const vals = Array.from(this.state().selectedValues);
    if (this.config()?.needsName && vals.length >= 2) return vals[0] || '';
    return this.config()?.title || '';
  });
  /** Property value string for namedPair picker type. */
  readonly propVal = linkedSignal(() => {
    const vals = Array.from(this.state().selectedValues);
    if (this.config()?.needsName && vals.length >= 2) return vals[1] || '';
    return vals.join(', ');
  });
  /** Text value string for plain text picker type. */
  readonly textVal = linkedSignal(() =>
    Array.from(this.state().selectedValues).join(', '),
  );

  /** Local search query entered in popover filter input. */
  readonly searchQuery = signal<string>('');
  /** Active column to sort candidate items by ('value' | 'filtered' | 'total'). */
  readonly sortBy = linkedSignal<'value' | 'filtered' | 'total'>(() =>
    this.isPlain() ? 'value' : 'filtered',
  );
  /** Sort direction flag (true = ascending, false = descending). */
  readonly sortAsc = linkedSignal<boolean>(() =>
    this.isPlain() ? true : false,
  );
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

  /** Whether the candidate items are plain values without count columns. */
  readonly isPlain = computed(() => {
    if (this.config()?.valuesType === 'plain') return true;
    if (this.state()?.valuesType === 'plain') return true;
    if (this.state()?.valuesType === 'counted') return false;
    const list = this.state().values || [];
    return (
      list.length > 0 &&
      list.every((v) => v.filtered === undefined && v.total === undefined)
    );
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
      const isMulti = this.advMode() === 'exactly' || this.advMode() === 'at_least';
      if (!isMulti) {
        return this.advText().trim() ? '1 value added' : 'No values added';
      }
      const count = this.advValues().length;
      return count === 0 ? 'No values added' : `${count} value${count > 1 ? 's' : ''} added`;
    }

    const type = this.config()?.type;
    if (type !== 'list' && type !== undefined) {
      return '';
    }

    const count = this.selectedSet().size;
    const verb = this.isNegated() ? 'excluded' : 'selected';
    return count === 0 ? `No options ${verb}` : `${count} option${count > 1 ? 's' : ''} ${verb}`;
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
    const event = buildValuePickerApplyEvent({
      type: this.config()?.type,
      isAdvanced: this.isAdvanced(),
      negated: this.isNegated(),
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

  /** Closes popover overlay without applying changes. */
  onCancel() {
    this.store.closeValuePicker();
  }
}
