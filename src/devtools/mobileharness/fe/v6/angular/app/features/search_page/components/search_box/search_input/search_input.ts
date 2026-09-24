import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';

import {TooltipIfTruncatedDirective} from '../../../../../shared/directives/tooltip_if_truncated/tooltip_if_truncated';
import {FilterChip, SearchBoxSuggestion} from '../../../models';
import {SearchPageStore} from '../../../services/search_page_store';
import {getChipKey} from '../../../utils';
import {SearchSuggestions} from '../search_suggestions/search_suggestions';

/**
 * Standalone component containing Fleet scope switcher, active filter chips,
 * and primary search input with typeahead suggestions popover.
 */
@Component({
  selector: 'app-search-input',
  standalone: true,
  templateUrl: './search_input.ng.html',
  styleUrl: './search_input.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.docked]': 'isDocked()',
    '(document:mousedown)': 'onDocumentMouseDown($event)',
  },
  imports: [
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    TooltipIfTruncatedDirective,
    SearchSuggestions,
  ],
})
export class SearchInput {
  private readonly hostRef = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Shared search page state store injected via Angular Dependency Injection. */
  readonly store = inject(SearchPageStore);

  /** Helper to safely extract chip keys. */
  readonly getChipKey = getChipKey;

  /** Whether the search input is currently docked in the global header toolbar. */
  readonly isDocked = input<boolean>(false);

  /** Signal reference targeting the native HTML `<input>` element inside the search bar. */
  readonly searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  /** Signal reference targeting the search box outer container DOM element for CDK overlay positioning. */
  readonly searchBoxOrigin = viewChild<ElementRef<HTMLElement>>('searchBox');

  /** Signal reference targeting the composite Filters chip element for CDK overlay positioning. */
  readonly compositeFiltersEl =
    viewChild<ElementRef<HTMLElement>>('compositeFiltersEl');

  /** Active highlighted index during keyboard navigation over auto-complete suggestions (-1 when unselected). */
  readonly activeSuggestionIndex = signal<number>(-1);

  /** Group of chips to highlight in the suggestions popover when a summary chip is clicked. */
  readonly highlightedGroup = signal<'filters' | 'groupby' | null>(null);

  /** Active filter chips (excluding group-by). */
  readonly filterChips = computed<FilterChip[]>(
    () =>
      this.store.filterChips?.() ??
      this.store.activeChips().filter((c) => !c.isGroupBy),
  );

  /** Active group-by chips. */
  readonly groupByChips = computed<FilterChip[]>(
    () =>
      this.store.groupByChips?.() ??
      this.store.activeChips().filter((c) => c.isGroupBy),
  );

  /** Total count of active filter chips (excluding group-by). */
  readonly filterChipsCount = computed<number>(() => this.filterChips().length);

  /** Total count of active group-by chips. */
  readonly groupByChipsCount = computed<number>(
    () => this.groupByChips().length,
  );

  /** Whether active filter chips are collapsed into a composite `N filters` chip (only when > 1). */
  readonly showCompositeFilterChip = computed<boolean>(
    () => this.filterChipsCount() > 1,
  );

  /** Whether active group-by chips are collapsed into a composite `M group-by` chip (only when > 1). */
  readonly showCompositeGroupByChip = computed<boolean>(
    () => this.groupByChipsCount() > 1,
  );

  /** Whether either filter or group-by chips are currently collapsed into a composite chip. */
  readonly isCollapsed = computed<boolean>(
    () => this.showCompositeFilterChip() || this.showCompositeGroupByChip(),
  );

  /** Whether the M3 search suggestions panel is currently visible below the search box. */
  readonly isPanelOpen = computed<boolean>(
    () =>
      this.store.isSuggestionsPanelOpen?.() ??
      (this.store.showSuggestions() &&
        (this.store.activeChips().length > 0 ||
          !!this.store.pendingFilter?.() ||
          (this.store.showValuePicker?.() && !!this.store.pickerKey?.()) ||
          !!this.store.isSuggestionsLoading?.() ||
          this.store.suggestions().length > 0)),
  );

  /** Summary tooltip for the composite `N filters` chip. */
  readonly filterSummaryTooltip = computed<string>(() => {
    const lines = this.filterChips().map(
      (c) => `${c.pillKey} (${c.pillCondition})`,
    );
    return lines.length > 0
      ? `${lines.join('\n')}\n\nClick to view or edit.`
      : 'Click to view or edit.';
  });

  /** Summary tooltip for the composite `M group-by` chip. */
  readonly groupBySummaryTooltip = computed<string>(() => {
    const keys = this.groupByChips().map((c) => c.pillKey);
    return keys.length > 0
      ? `Group by: ${keys.join(', ')}\n\nClick to view or edit.`
      : 'Click to view or edit.';
  });

  /** Whether the FilterValuePicker is currently open for one of the collapsed filter chips. */
  readonly isCompositeFilterPickerActive = computed<boolean>(() =>
    this.filterChips().some((chip) => this.store.isChipPickerActive(chip)),
  );

  /** Effective input placeholder (cleared when active chips are present in single-line docked mode). */
  readonly effectivePlaceholder = computed<string>(() => {
    if (this.isDocked() && this.store.activeChips().length > 0) {
      return '';
    }
    return this.store.searchPlaceholder();
  });

  /** Opens the suggestions popover and highlights the corresponding chip row when a summary chip is clicked. */
  onCompositeChipClick(
    group: 'filters' | 'groupby' = 'filters',
    event?: MouseEvent,
  ) {
    event?.stopPropagation();
    this.store.focusChipKey?.set(null);
    this.highlightedGroup.set(group);
    this.focusInput();
  }

  /** Removes a single filter or group-by chip from the search bar and collapses suggestions if auto-collapse is enabled. */
  onRemoveChip(chip: FilterChip, event: MouseEvent) {
    event.stopPropagation();
    this.highlightedGroup.set(null);
    this.store.removeFilterChip(chip);
    if (this.store.autoCollapseAfterApply?.()) {
      this.onCollapsePanel();
    }
  }

  /** Removes all active filter chips when the composite filter chip's remove button is clicked. */
  onRemoveCompositeFilters(event: MouseEvent) {
    event.stopPropagation();
    this.highlightedGroup.set(null);
    this.store.clearFilterChips();
    if (this.store.autoCollapseAfterApply?.()) {
      this.onCollapsePanel();
    }
  }

  /** Removes all active group-by chips when the composite group-by chip's remove button is clicked. */
  onRemoveCompositeGroupBy(event: MouseEvent) {
    event.stopPropagation();
    this.highlightedGroup.set(null);
    this.store.clearGroupByChips();
    if (this.store.autoCollapseAfterApply?.()) {
      this.onCollapsePanel();
    }
  }

  /** Handles Add filter button click from the suggestions popover. */
  onAddFilter(event?: {event: MouseEvent}) {
    this.highlightedGroup.set('filters');
    this.focusInput();
  }

  /** Handles Add group-by button click from the suggestions popover. */
  onAddGroupBy(event?: {event: MouseEvent}) {
    this.highlightedGroup.set('groupby');
    this.focusInput();
  }

  /** Closes the suggestions panel and blurs the search input when Collapse is clicked. */
  onCollapsePanel() {
    this.highlightedGroup.set(null);
    this.searchInput()?.nativeElement.blur();
    this.store.showSuggestions.set(false);
  }

  /** Opens the FilterValuePicker for an active filter chip clicked inside the suggestions popover, keeping the panel open. */
  onEditChipFromSuggestions(event: {
    chip: FilterChip;
    anchor?: HTMLElement;
    event: MouseEvent;
  }) {
    const {chip, anchor} = event;
    this.highlightedGroup.set(null);
    const key = getChipKey(chip);
    const targetAnchor =
      anchor ||
      this.compositeFiltersEl()?.nativeElement ||
      this.searchBoxOrigin()?.nativeElement;
    if (targetAnchor) {
      const title = chip.metadata?.keyDisplayName || chip.pillKey;
      this.store.openValuePicker(
        key,
        targetAnchor,
        title,
        chip.metadata,
        undefined,
        /* keepSuggestionsOpen= */ true,
      );
    }
  }

  /**
   * Handles user selection of an auto-complete suggestion item.
   * If the suggestion requests an open-picker action, opens the FilterValuePicker overlay anchored to the search box;
   * otherwise, applies the suggestion filter directly to the store.
   *
   * @param event Object containing the selected `SearchBoxSuggestion` item.
   */
  onSelectSuggestion(event: {item: SearchBoxSuggestion}) {
    const {item} = event;
    this.highlightedGroup.set(null);
    this.activeSuggestionIndex.set(-1);

    this.store.selectSuggestion(
      item,
      this.searchBoxOrigin()?.nativeElement || null,
    );

    if (!this.store.showSuggestions()) {
      this.searchInput()?.nativeElement.blur();
    } else if (!this.store.showValuePicker()) {
      this.searchInput()?.nativeElement.focus();
    }
  }

  /**
   * Handles user text input events in the search box.
   * Updates the search query signal, resets active suggestion highlight, and opens suggestions popover.
   *
   * @param val Current text value in the search input box.
   */
  onSearchInput(val: string) {
    this.highlightedGroup.set(null);
    this.store.closeValuePicker();
    this.store.searchQuery.set(val);
    this.store.showSuggestions.set(true);
    this.activeSuggestionIndex.set(-1);
  }

  /**
   * Clears only the user's typed text in the search input while preserving active filter and group-by chips.
   *
   * @param event Mouse event from clicking the clear button.
   */
  onClearInput(event?: MouseEvent) {
    event?.stopPropagation();
    this.store.clearSearchQuery();
    this.activeSuggestionIndex.set(-1);
    this.searchInput()?.nativeElement.focus();
  }

  /** Focuses the native search input element and displays the suggestions popover. */
  focusInput() {
    this.store.closeValuePicker();
    this.searchInput()?.nativeElement.focus();
    this.store.showSuggestions.set(true);
  }

  /** Handles focus event on the input element by opening the suggestions popover. */
  onInputFocus() {
    if (!this.store.pendingFilter?.()) {
      this.store.closeValuePicker();
    }
    this.store.showSuggestions.set(true);
  }

  /**
   * Prevents click propagation from closing overlays and displays suggestions popover on input click.
   *
   * @param event Mouse event from input click.
   */
  onInputClick(event?: MouseEvent) {
    event?.stopPropagation();
    this.store.closeValuePicker();
    this.store.showSuggestions.set(true);
  }

  /**
   * Handles click events anywhere on the outer search box area.
   * Delegates focus to the text input unless a chip or clear button was explicitly clicked.
   *
   * @param event Mouse event from search box container click.
   */
  onSearchBoxClick(event?: MouseEvent) {
    const target = event?.target as HTMLElement | null;
    if (
      target === this.searchInput()?.nativeElement ||
      target?.closest('.search-chip') ||
      target?.closest('.search-clear-btn')
    ) {
      return;
    }
    this.focusInput();
  }

  /** Handles blur event on the input element by hiding suggestions popover unless ValuePicker is open or auto-collapse is disabled. */
  onInputBlur(event?: FocusEvent) {
    const related = event?.relatedTarget as HTMLElement | null;
    if (
      (related &&
        (this.hostRef.nativeElement.contains(related) ||
          related.closest('.value-picker') ||
          related.closest('.preset-link'))) ||
      this.store.showValuePicker?.() ||
      this.store.pendingFilter?.() ||
      this.store.autoCollapseAfterApply?.() === false
    ) {
      return;
    }
    this.store.showSuggestions.set(false);
  }

  /** Closes suggestions popover when clicking outside the search box, value picker, and preset links. */
  onDocumentMouseDown(event: MouseEvent) {
    if (!this.store.showSuggestions()) return;
    const target = event.target as HTMLElement | null;
    if (!target) return;
    if (
      this.hostRef.nativeElement.contains(target) ||
      target.closest('.value-picker') ||
      target.closest('.preset-link')
    ) {
      return;
    }
    this.highlightedGroup.set(null);
    this.store.showSuggestions.set(false);
  }

  /**
   * Dispatches keyboard navigation events for search input controls.
   * Handles ArrowUp/ArrowDown for suggestion highlighting, Enter for selection/execution, and Escape for closing overlays.
   *
   * @param event Keyboard event triggered from the search input.
   */
  onKeyDown(event: KeyboardEvent) {
    const key = event.key;
    if (!['ArrowDown', 'ArrowUp', 'Enter', 'Escape'].includes(key)) return;

    const suggestions = this.store.isSuggestionsLoading?.()
      ? []
      : this.store.suggestions();
    const isShowing = this.store.showSuggestions() && suggestions.length > 0;

    switch (key) {
      case 'ArrowDown':
        if (!isShowing) return;
        event.preventDefault();
        this.activeSuggestionIndex.update((i) => (i + 1) % suggestions.length);
        break;

      case 'ArrowUp':
        if (!isShowing) return;
        event.preventDefault();
        this.activeSuggestionIndex.update((i) =>
          i <= 0 ? suggestions.length - 1 : i - 1,
        );
        break;

      case 'Enter':
        this.handleEnterKey(event, isShowing, suggestions);
        break;

      case 'Escape':
        this.handleEscapeKey();
        break;

      default:
        break;
    }
  }

  /**
   * Private helper executing selection or search query when the Enter key is pressed.
   *
   * @param event Keyboard event.
   * @param isShowing Whether suggestions popover is active.
   * @param suggestions Array of available search suggestions.
   */
  private handleEnterKey(
    event: KeyboardEvent,
    isShowing: boolean,
    suggestions: SearchBoxSuggestion[],
  ) {
    if (suggestions.length > 0) {
      event.preventDefault();
      const idx = this.activeSuggestionIndex();
      const targetIdx =
        isShowing && idx >= 0 && idx < suggestions.length ? idx : 0;
      this.onSelectSuggestion({item: suggestions[targetIdx]});
      return;
    }

    if (this.store.searchQuery().trim()) {
      this.store.showSuggestions.set(false);
      this.store.executeSearch();
    }
  }

  /** Private helper closing suggestions popover or value picker overlay when Escape key is pressed. */
  private handleEscapeKey() {
    if (this.store.showSuggestions()) {
      this.store.showSuggestions.set(false);
      this.activeSuggestionIndex.set(-1);
      return;
    }

    if (this.store.showValuePicker()) {
      this.store.closeValuePicker();
    }
  }

  /**
   * Handles clicking an active filter or group-by chip in the search bar:
   * Consistent with composite chips and quick filter/group-by presets, opens the suggestions
   * popover first, locates/highlights the corresponding chip in the popover, and (for filters)
   * opens the FilterValuePicker anchored to that popover chip.
   *
   * @param chip The FilterChip to locate/edit.
   * @param anchor Fallback DOM element anchor.
   * @param event Optional MouseEvent to prevent event bubbling.
   */
  openPickerForChip(chip: FilterChip, anchor: HTMLElement, event?: MouseEvent) {
    event?.stopPropagation();
    this.highlightedGroup.set(null);
    const key = getChipKey(chip);
    if (chip.isGroupBy) {
      this.store.openQuickGroupBy(key, chip.pillKey);
      return;
    }
    const title = chip.metadata?.keyDisplayName || chip.pillKey;
    this.store.openQuickFilter(key, title, chip.metadata);
  }
}
