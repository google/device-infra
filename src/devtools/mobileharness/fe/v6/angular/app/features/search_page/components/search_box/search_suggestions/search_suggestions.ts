import {DecimalPipe} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
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

import {Filter, Fleet, SearchEntity} from '../../../../../core/models/search';
import {TooltipIfTruncatedDirective} from '../../../../../shared/directives/tooltip_if_truncated/tooltip_if_truncated';
import {FilterChip, SearchBoxSuggestion} from '../../../models';
import {SearchPageStore} from '../../../services/search_page_store';
import {getChipKey} from '../../../utils';
import {KeyPickerComponent, KeyPickerMode} from '../key_picker/key_picker';

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

  /** Template references to filter and pending chips rendered inside the popover. */
  readonly popoverChipEls =
    viewChildren('popoverChipEl', {read: ElementRef});

  /** Reference to the popover container element as a fallback anchor. */
  readonly popoverContainer =
    viewChild('popoverContainer', {read: ElementRef});

  /** Currently highlighted keyboard navigation index. */
  readonly activeIndex = input<number>(-1);

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

  constructor() {
    this.store.locatePopoverChip?.set((key: string, title?: string) =>
      this.getChipElement(key, title),
    );
    this.store.getPopoverContainer?.set(() =>
      this.getPopoverContainerElement(),
    );
  }

  /** Locates the HTMLElement for a popover chip with the given key using viewChildren. */
  getChipElement(key: string, title?: string): HTMLElement | null {
    const k = key.toLowerCase();
    const t = title ? title.toLowerCase() : null;
    for (const item of this.popoverChipEls()) {
      const el =
        (item as {nativeElement?: HTMLElement})?.nativeElement ??
        (item as unknown as HTMLElement);
      if (!el || !el.getAttribute) continue;
      const chipKey = el.getAttribute('data-chip-key')?.toLowerCase();
      const pillKey = el.getAttribute('data-chip-pill-key')?.toLowerCase();
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
    const item = this.popoverContainer();
    return (
      (item as {nativeElement?: HTMLElement})?.nativeElement ??
      (item as unknown as HTMLElement) ??
      null
    );
  }

  /** Active filter chips (excluding group-by). */
  readonly activeFilterChips = computed<FilterChip[]>(
    () =>
      this.store.filterChips?.() ??
      this.store.activeChips().filter((c) => !c.isGroupBy),
  );

  /** Active group-by chips. */
  readonly activeGroupByChips = computed<FilterChip[]>(
    () =>
      this.store.groupByChips?.() ??
      this.store.activeChips().filter((c) => c.isGroupBy),
  );

  /** Whether group-by criteria are supported for the active search entity (Fleet only, not TJS). */
  readonly showGroupBySection = computed<boolean>(() => !this.store.isTjs?.());

  /** Whether there are any active filter or group-by chips. */
  readonly hasActiveChips = computed<boolean>(
    () => this.store.activeChips().length > 0,
  );

  /** Key of a filter currently being edited in ValuePicker or pending via Quick Filter, not yet applied as an active chip. */
  readonly pendingFilterKey = computed<string | null>(() => {
    if (this.store.pendingFilterKey) {
      return this.store.pendingFilterKey();
    }
    const pf = this.store.pendingFilter?.();
    const pk =
      pf?.key ||
      (this.store.showValuePicker?.() ? this.store.pickerKey?.() : null);
    if (!pk) return null;
    const hasApplied = this.activeFilterChips().some(
      (c) => getChipKey(c).toLowerCase() === pk.toLowerCase(),
    );
    return hasApplied ? null : pk;
  });

  /** Display title for the pending filter key being edited. */
  readonly pendingFilterTitle = computed<string>(
    () =>
      this.store.pendingFilterTitle?.() ||
      this.store.pendingFilter?.()?.displayName ||
      this.store.pickerTitle?.() ||
      this.pendingFilterKey() ||
      '',
  );

  /** Whether there are any applied chips or a pending filter key in progress. */
  readonly hasActiveCriteria = computed<boolean>(
    () => this.hasActiveChips() || !!this.pendingFilterKey(),
  );

  /** Live result count label displayed in the panel header (e.g., "918 devices"). Semantic text for TJS. */
  readonly resultCountLabel = computed<string>(() => {
    if (this.store.isTjs?.()) {
      return 'Active filters';
    }
    const count = this.store.effectiveTotalCount?.() ?? null;
    if (count !== null && count !== undefined) {
      const entity = this.store.entity?.() || 'devices';
      const noun = count === 1 ? entity.replace(/s$/, '') : entity;
      return `${count.toLocaleString()} ${noun}`;
    }
    return 'Active criteria';
  });

  /** Whether autocomplete suggestions are currently loading from the BFF. */
  readonly isSuggestionsLoading = computed<boolean>(
    () => !!this.store.isSuggestionsLoading?.(),
  );

  /** Whether the suggestions popover should be displayed. */
  readonly shouldShowPopover = computed<boolean>(
    () =>
      this.store.isSuggestionsPanelOpen?.() ??
      (this.store.showSuggestions() &&
        (this.hasActiveCriteria() ||
          this.isSuggestionsLoading() ||
          this.store.suggestions().length > 0)),
  );

  /** Whether the 'Auto-collapse after applying' checkbox is checked (defaults to true if not in storage). */
  readonly isAutoCollapseChecked = computed<boolean>(
    () => this.store.autoCollapseAfterApply?.() ?? true,
  );

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
    if (this.store.autoCollapseAfterApply?.()) {
      this.store.showSuggestions.set(false);
      this.collapsePanel.emit();
    }
  }

  onPendingChipClick(
    pf: {key: string; displayName: string; metadata?: unknown},
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
        entity:
          this.store.entity?.() === 'hosts'
            ? SearchEntity.SEARCH_ENTITY_HOST
            : SearchEntity.SEARCH_ENTITY_DEVICE,
        fleet:
          this.store.fleet?.() === 'ats'
            ? Fleet.FLEET_ATS
            : Fleet.FLEET_SELF,
        activeFilters:
          (this.store as unknown as {effectiveFilters?: () => Filter[]})
            .effectiveFilters?.() || [],
        activeGroupBy: this.store.groupByKeys?.() || [],
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
    this.openKeyPicker('groupby');
    this.addGroupBy.emit({event});
  }

  onClearAll(event: MouseEvent) {
    event.stopPropagation();
    this.store.resetSearchState?.();
    this.store.showSuggestions.set(false);
    this.collapsePanel.emit();
  }

  onAutoCollapseChange(event: Event) {
    event.stopPropagation();
    const target = event.target as HTMLInputElement | null;
    if (target) {
      this.store.setAutoCollapseAfterApply?.(target.checked);
    }
  }

  onCollapse(event: MouseEvent) {
    event.stopPropagation();
    this.store.showSuggestions.set(false);
    this.collapsePanel.emit();
  }
}
