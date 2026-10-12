import {DecimalPipe} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  output,
  viewChild,
  viewChildren,
} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTooltipModule} from '@angular/material/tooltip';

import {Filter} from '../../../../../core/models/search';
import {TooltipIfTruncatedDirective} from '../../../../../shared/directives/tooltip_if_truncated/tooltip_if_truncated';
import {
  FilterChip,
  FilterKeyMetadata,
  KeyPickerMode,
  MAX_GROUP_BY_KEYS,
  SearchBoxSuggestion,
} from '../../../models';
import {SearchPageStore} from '../../../services/search_page_store';
import {
  getChipKey,
  toFleetProto,
  toSearchEntityProto,
  trackChip,
} from '../../../utils';
import {KeyPickerComponent} from '../key_picker/key_picker';

/** Standalone popover component rendering active filter/group-by chips and typeahead search suggestions. */
@Component({
  selector: 'app-search-suggestions',
  standalone: true,
  templateUrl: './search_suggestions.ng.html',
  styleUrl: './search_suggestions.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    TooltipIfTruncatedDirective,
  ],
})
export class SearchSuggestions {
  readonly store = inject(SearchPageStore);
  private readonly dialog = inject(MatDialog, {optional: true});
  readonly getChipKey = getChipKey;

  /** Tracking function for active filter and group-by chips in Angular templates. */
  trackChip(chip: FilterChip): string {
    return trackChip(chip);
  }

  /** Template references to active filter chips rendered inside the popover. */
  readonly filterChipEls = viewChildren('filterChipEl', {read: ElementRef});

  /** Reference to the pending chip element rendered in the popover. */
  readonly pendingChipEl = viewChild<ElementRef<HTMLElement>>('pendingChipEl');

  /** Reference to the popover container element as a fallback anchor. */
  readonly popoverContainer =
    viewChild<ElementRef<HTMLElement>>('popoverContainer');

  /** References to rendered suggestion items for auto-scroll. */
  readonly suggestionItems = viewChildren('suggestionItem', {read: ElementRef});

  /** Currently highlighted keyboard navigation index. */
  readonly activeIndex = input<number>(-1);

  /** Automatically scrolls the active suggestion item into view on keyboard navigation. */
  private readonly scrollActiveSuggestionEffect = effect(() => {
    const idx = this.activeIndex();
    if (idx < 0) return;
    const items = this.suggestionItems();
    if (idx < items.length) {
      items[idx].nativeElement.scrollIntoView({block: 'nearest'});
    }
  });

  /** Group of chips to highlight with a ring when a summary chip is clicked ('filters' | 'groupby' | null). */
  readonly highlightedGroup = input<'filters' | 'groupby' | null>(null);

  /** Event emitted when a suggestion is selected. */
  readonly selectSuggestion = output<{item: SearchBoxSuggestion}>();

  /** Event emitted when an active filter chip inside the popover is clicked to edit. */
  readonly editChip = output<{
    chip: FilterChip;
    anchor: HTMLElement;
    event: MouseEvent;
  }>();

  /** Event emitted when the Add filter button is clicked in the popover. */
  readonly addFilter = output<{event: MouseEvent}>();

  /** Event emitted when the Add group-by button is clicked in the popover. */
  readonly addGroupBy = output<{event: MouseEvent}>();

  /** Event emitted when the user clicks Collapse or Clear all in the panel. */
  readonly collapsePanel = output<void>();

  /** Automatically opens ValuePicker anchored to the rendered popover chip when requested. */
  private readonly autoOpenValuePickerEffect = effect(
    () => {
      const req = this.store.pendingFilterOpenRequest();
      if (!req) return;

      const isPending = Boolean(this.store.pendingFilter());
      let chipEl: HTMLElement | null = null;

      if (isPending) {
        // When opening a new pending filter, anchor specifically to the pending chip element.
        // Reading pendingChipEl() registers a dependency so this effect re-runs the instant
        // Angular finishes rendering the pending chip into the DOM:
        chipEl = this.pendingChipEl()?.nativeElement ?? null;
        if (!chipEl) {
          // Pending chip has not yet rendered in the DOM; wait for viewChild resolution instead
          // of prematurely falling back to the outer container!
          return;
        }
      } else {
        // Active filter chip: re-run if filterChipEls update
        this.filterChipEls();
        chipEl = this.getChipElement(req.key, req.title);
        if (!chipEl) {
          chipEl = this.getPopoverContainerElement();
        }
      }

      if (chipEl) {
        this.store.openValuePicker(
          req.key,
          chipEl,
          req.title,
          req.metadata,
          req.stagedValues,
          /* keepSuggestionsOpen= */ true,
        );
        this.store.pendingFilterOpenRequest.set(null);
      }
    },
    {allowSignalWrites: true},
  );

  /** Locates the HTMLElement for an active filter chip with the given key using filterChipEls. */
  getChipElement(key: string, title?: string): HTMLElement | null {
    const k = key.toLowerCase();
    const t = title ? title.toLowerCase() : null;

    for (const item of this.filterChipEls()) {
      const el = item.nativeElement;
      if (!el || !el.getAttribute) continue;

      const chipKey = el.getAttribute('data-chip-key')?.toLowerCase() || '';
      const pillKey =
        el.getAttribute('data-chip-pill-key')?.toLowerCase() || '';

      if (
        chipKey === k ||
        pillKey === k ||
        (t && (chipKey === t || pillKey === t))
      ) {
        el.scrollIntoView?.({block: 'nearest'});
        return el;
      }
    }
    return null;
  }

  /** Returns the popover container element as a fallback anchor. */
  getPopoverContainerElement(): HTMLElement | null {
    return this.popoverContainer()?.nativeElement ?? null;
  }

  /** Checks whether the given chip matches the currently focused chip key and type in the store. */
  isChipFocused(chip: FilterChip, expectedType: 'filter' | 'groupby'): boolean {
    if (this.store.focusChipType() !== expectedType) {
      return false;
    }
    const focusKey = this.store.focusChipKey();
    if (!focusKey) {
      return false;
    }
    const normFocus = focusKey.toLowerCase();
    const chipKey = (chip.key || getChipKey(chip) || '').toLowerCase();
    const pillKey = (chip.pillKey || '').toLowerCase();
    return normFocus === chipKey || normFocus === pillKey;
  }

  /** Checks whether the pending chip matches the currently focused filter key in the store. */
  isPendingChipFocused(pKey: string): boolean {
    if (this.store.focusChipType() !== 'filter') {
      return false;
    }
    const focusKey = this.store.focusChipKey();
    if (!focusKey) {
      return false;
    }
    return focusKey.toLowerCase() === pKey.toLowerCase();
  }

  /** Active filter chips (excluding group-by). */
  readonly activeFilterChips = this.store.filterChips;

  /** Active group-by chips. */
  readonly activeGroupByChips = this.store.groupByChips;

  /** Maximum number of group-by keys allowed concurrently. */
  readonly maxGroupByKeys = MAX_GROUP_BY_KEYS;

  /** Whether adding more group-by keys is disabled (maximum reached). */
  readonly isAddGroupByDisabled = computed<boolean>(
    () => this.activeGroupByChips().length >= this.maxGroupByKeys,
  );

  /** Whether group-by criteria are supported for the active search entity (Fleet only, not TJS). */
  readonly showGroupBySection = computed<boolean>(() => !this.store.isTjs());

  /** Whether the Add filter button is supported for the active search entity (Fleet only, not TJS). */
  readonly showAddFilterButton = computed<boolean>(() => !this.store.isTjs());

  /** Whether there are any active filter or group-by chips. */
  readonly hasActiveChips = computed<boolean>(
    () => this.store.activeChips().length > 0,
  );

  /** Key of a filter currently being edited in ValuePicker or pending via Quick Filter, not yet applied as an active chip. */
  readonly pendingFilterKey = this.store.pendingFilterKey;

  /** Display title for the pending filter key being edited. */
  readonly pendingFilterTitle = this.store.pendingFilterTitle;

  /** Whether there are any applied chips or a pending filter key in progress. */
  readonly hasActiveCriteria = computed<boolean>(
    () => this.hasActiveChips() || !!this.pendingFilterKey(),
  );

  /** Live result count label displayed in the panel header (e.g., "918 devices"). Semantic text for TJS. */
  readonly resultCountLabel = computed<string>(() => {
    if (this.store.isTjs()) {
      return 'Active filters';
    }
    const count = this.store.effectiveTotalCount();
    if (count !== null && count !== undefined) {
      const entity = this.store.entity() || 'devices';
      const noun = count === 1 ? entity.replace(/s$/, '') : entity;
      return `${count.toLocaleString()} ${noun}`;
    }
    return 'Active criteria';
  });

  /** Whether autocomplete suggestions are currently loading from the BFF. */
  readonly isSuggestionsLoading = this.store.isSuggestionsLoading;

  /** Whether the suggestions popover should be displayed. */
  readonly shouldShowPopover = this.store.isSuggestionsPanelOpen;

  /** Whether to show the no-matches empty item when query yielded no suggestions. */
  readonly hasNoMatches = computed<boolean>(
    () =>
      !this.isSuggestionsLoading() &&
      this.store.suggestions().length === 0 &&
      Boolean(this.store.searchQuery().trim()),
  );

  /** Headline text for no-matches display. */
  readonly noMatchesQueryText = computed<string>(() =>
    this.store.searchQuery().trim(),
  );

  /** Supporting text for no-matches display based on entity domain. */
  readonly noMatchesSupportText = computed<string>(() => {
    if (this.store.isTjs()) {
      return 'Try an ID, an LDAP, a status, or a field name.';
    }
    return 'Type one condition at a time.';
  });

  /** Whether the 'Auto-collapse after applying' checkbox is checked (defaults to true if not in storage). */
  readonly isAutoCollapseChecked = this.store.autoCollapseAfterApply;

  onItemClick(item: SearchBoxSuggestion) {
    this.selectSuggestion.emit({item});
  }

  onChipClick(chip: FilterChip, anchor: HTMLElement, event: MouseEvent) {
    event.stopPropagation();
    if (chip.isGroupBy) return;
    this.editChip.emit({chip, anchor, event});
  }

  onRemoveChip(chip: FilterChip, event: MouseEvent) {
    event.stopPropagation();
    this.store.removeFilterChip(chip);
    if (this.store.autoCollapseAfterApply()) {
      this.collapsePanel.emit();
    }
  }

  onPendingChipClick(
    pf: {key: string; displayName: string; metadata?: FilterKeyMetadata},
    anchor: HTMLElement,
    event: MouseEvent,
  ) {
    event.stopPropagation();
    this.store.openValuePicker(
      pf.key,
      anchor,
      pf.displayName,
      pf.metadata,
      undefined,
      /* keepSuggestionsOpen= */ true,
    );
  }

  /** Opens the KeyPicker dialog for selecting a filter or group-by key. */
  openKeyPicker(mode: KeyPickerMode) {
    this.dialog?.open(KeyPickerComponent, {
      panelClass: 'key-picker-dialog-panel',
      data: {
        mode,
        entity: toSearchEntityProto(this.store.entity()),
        fleet: toFleetProto(this.store.fleet()),
        activeFilters: (this.store.effectiveFilters() as Filter[]) || [],
        activeGroupBy: this.store.groupByKeys() || [],
      },
    });
  }

  onAddFilterClick(event: MouseEvent) {
    event.stopPropagation();
    this.openKeyPicker('filter');
    this.addFilter.emit({event});
  }

  onAddGroupByClick(event: MouseEvent) {
    event.stopPropagation();
    if (this.isAddGroupByDisabled()) {
      return;
    }
    this.openKeyPicker('groupby');
    this.addGroupBy.emit({event});
  }

  onClearAll(event: MouseEvent) {
    event.stopPropagation();
    this.store.resetSearchState();
    this.collapsePanel.emit();
  }

  onAutoCollapseChange(event: Event) {
    event.stopPropagation();
    const target = event.target as HTMLInputElement | null;
    if (target) {
      this.store.setAutoCollapseAfterApply(target.checked);
    }
  }

  onCollapse(event: MouseEvent) {
    event.stopPropagation();
    this.collapsePanel.emit();
  }
}
