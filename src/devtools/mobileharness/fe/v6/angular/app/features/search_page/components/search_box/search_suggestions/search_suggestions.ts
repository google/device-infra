/**
 * @fileoverview Search suggestions popover displaying active criteria chips, pending filter chip, and BFF typeahead suggestions.
 */

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
import {toSignal} from '@angular/core/rxjs-interop';
import {MatDialog} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTooltipModule} from '@angular/material/tooltip';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {of} from 'rxjs';

import {Filter} from '../../../../../core/models/search';
import {CommonParamsService} from '../../../../../core/services/common_params_service';
import {
  IS_ASSISTANT_ENABLED,
  assistantLinkQueryParams,
} from '../../../../../shared/components/diagnose_in_assistant/diagnose_in_assistant';
import {TooltipIfTruncatedDirective} from '../../../../../shared/directives/tooltip_if_truncated/tooltip_if_truncated';
import {
  FilterChip,
  FocusChipType,
  KeyPickerMode,
  MAX_GROUP_BY_KEYS,
  PendingFilterItem,
  SearchBoxSuggestion,
} from '../../../models';
import {SearchPageStore} from '../../../services/search_page_store';
import {
  findChipAnchorElement,
  formatSuggestionCount,
  getChipKey,
  getEntityNoun,
  getSuggestionCountTooltip,
  isChipFocusedInPopover,
  isPendingChipFocusedInPopover,
  toFleetProto,
  toSearchEntityProto,
  trackChip,
  trackSuggestion,
} from '../../../utils';
import {KeyPickerComponent} from '../key_picker/key_picker';

/**
 * Search-page query parameters that describe the applied search state
 * (matching `buildOutgoingUrlQueryParams`), forwarded in the Assistant link's
 * `from` page path.
 */
const PAGE_CONTEXT_QUERY_PARAMS = ['f', 'gb', 'fleet'] as const;

/** Standalone popover component rendering active filter/group-by chips and typeahead search suggestions. */
@Component({
  selector: 'app-search-suggestions',
  standalone: true,
  templateUrl: './search_suggestions.ng.html',
  styleUrl: './search_suggestions.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    RouterLink,
    TooltipIfTruncatedDirective,
  ],
})
export class SearchSuggestions {
  readonly store = inject(SearchPageStore);
  private readonly dialog = inject(MatDialog, {optional: true});
  private readonly commonParamsService = inject(CommonParamsService);
  private readonly isAssistantEnabledFn = inject(IS_ASSISTANT_ENABLED, {
    optional: true,
  });
  private readonly queryParamMap = toSignal(
    inject(ActivatedRoute, {optional: true})?.queryParamMap ?? of(null),
    {requireSync: true},
  );
  private readonly router = inject(Router);
  readonly getChipKey = getChipKey;
  readonly trackChip = trackChip;

  /** Template references to active filter chips rendered inside the popover. */
  readonly filterChipEls = viewChildren('filterChipEl', {read: ElementRef});

  /** Reference to the pending chip element rendered in the popover. */
  readonly pendingChipEl = viewChild<ElementRef<HTMLElement>>('pendingChipEl');

  /** Reference to the popover container element as a fallback anchor. */
  readonly popoverContainer =
    viewChild<ElementRef<HTMLElement>>('popoverContainer');

  /** References to rendered suggestion items for auto-scroll. */
  readonly suggestionItems = viewChildren('suggestionItem', {read: ElementRef});

  /** The Ask Assistant link under No matches, opened from the keyboard. */
  private readonly askAssistantLink =
    viewChild<ElementRef<HTMLAnchorElement>>('askAssistantLink');

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

  /** Whether keyboard navigation highlights the Ask Assistant row. */
  readonly askAssistantActive = input<boolean>(false);

  /** Event emitted when a suggestion is selected. */
  readonly selectSuggestion = output<{item: SearchBoxSuggestion}>();

  /** Event emitted when an active filter chip inside the popover is clicked to edit. */
  readonly editChip = output<{
    chip: FilterChip;
    anchor: HTMLElement;
    event: MouseEvent;
  }>();

  /** Event emitted when the Add filter button is clicked in the popover. */
  readonly addFilter = output<void>();

  /** Event emitted when the Add group-by button is clicked in the popover. */
  readonly addGroupBy = output<void>();

  /** Event emitted when the user clicks Collapse or Clear all in the panel. */
  readonly collapsePanel = output<void>();

  /** Automatically opens ValuePicker anchored to the rendered popover chip when requested. */
  private readonly autoOpenValuePickerEffect = effect(() => {
    const req = this.store.pendingFilterOpenRequest();
    if (!req) return;

    const isPending = Boolean(this.store.pendingFilter());
    let chipEl: HTMLElement | null = null;

    if (isPending) {
      chipEl = this.pendingChipEl()?.nativeElement ?? null;
      if (!chipEl) return;
    } else {
      chipEl =
        this.getChipElement(req.key, req.title) ??
        this.getPopoverContainerElement();
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
  });

  /** Locates the HTMLElement for an active filter chip with the given key using filterChipEls. */
  getChipElement(key: string, title?: string): HTMLElement | null {
    return findChipAnchorElement(this.filterChipEls(), key, title);
  }

  /** Returns the popover container element as a fallback anchor. */
  getPopoverContainerElement(): HTMLElement | null {
    return this.popoverContainer()?.nativeElement ?? null;
  }

  /** Checks whether the given chip matches the currently focused chip key and type in the store. */
  isChipFocused(chip: FilterChip, expectedType: FocusChipType): boolean {
    return isChipFocusedInPopover(
      chip,
      expectedType,
      this.store.focusChipType(),
      this.store.focusChipKey(),
    );
  }

  /** Checks whether the pending chip matches the currently focused filter key in the store. */
  isPendingChipFocused(pKey: string): boolean {
    return isPendingChipFocusedInPopover(
      pKey,
      this.store.focusChipType(),
      this.store.focusChipKey(),
    );
  }

  /** Active filter chips (excluding group-by). */
  readonly activeFilterChips = this.store.filterChips;

  /** Active group-by chips. */
  readonly activeGroupByChips = this.store.groupByChips;

  /** Whether adding more group-by keys is disabled (maximum reached). */
  readonly isAddGroupByDisabled = computed<boolean>(
    () => this.activeGroupByChips().length >= MAX_GROUP_BY_KEYS,
  );

  /** Whether group-by criteria and the Add filter button are supported (Fleet only, not TJS). */
  readonly showGroupBySection = computed<boolean>(() => !this.store.isTjs());
  readonly showAddFilterButton = this.showGroupBySection;

  /** Whether there are any active filter or group-by chips. */
  readonly hasActiveChips = computed<boolean>(
    () => this.store.activeChips().length > 0,
  );

  /** Key of a filter currently being edited in ValuePicker or pending via Quick Filter, not yet applied as an active chip. */
  readonly pendingFilterKey = this.store.pendingFilterKey;

  /** Display title for the pending filter key being edited. */
  readonly pendingFilterTitle = this.store.pendingFilterTitle;

  /** Whether to show the criteria section (filters and group-by chips/buttons). */
  readonly showCriteriaSection = computed<boolean>(
    () =>
      this.showGroupBySection() ||
      this.hasActiveChips() ||
      Boolean(this.pendingFilterKey()),
  );

  /** Live result count label displayed in the panel header (e.g., "918 devices"). Semantic text for TJS. */
  readonly resultCountLabel = computed<string>(() => {
    if (this.store.isTjs()) {
      return 'Active filters';
    }
    const count = this.store.effectiveTotalCount();
    if (count !== null && count !== undefined) {
      const noun = getEntityNoun(this.store.entity() || 'devices', count !== 1);
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

  /**
   * Whether the no-matches item offers to ask the OmniLab Assistant: on device
   * and host search, when the Assistant is enabled. OSS builds do not provide
   * `IS_ASSISTANT_ENABLED`, so they never offer it.
   */
  readonly showAskAssistant = computed<boolean>(
    () =>
      !this.store.isTjs() &&
      (this.isAssistantEnabledFn?.(this.queryParamMap()) ?? false),
  );

  /** Whether the Ask Assistant row is rendered under No matches. */
  readonly showAskAssistantRow = computed<boolean>(
    () => this.hasNoMatches() && this.showAskAssistant(),
  );

  /** Label of the Ask Assistant link, quoting the typed text. */
  readonly askAssistantLabel = computed<string>(
    () => `Ask Assistant about “${this.noMatchesQueryText()}”`,
  );

  /**
   * This search page's path and query, such as
   * `/devices?f=device_field::status~IDLE&gb=dimension::model`, which the
   * Assistant link passes as the page the question came from so the Assistant
   * knows what filters and group-by were applied. Only the search-state
   * parameters (`f`, `gb`, and `fleet`) are kept.
   */
  readonly askAssistantPage = computed<string>(() => {
    const params = this.queryParamMap();
    const queryParams: Record<string, string[]> = {};
    for (const key of PAGE_CONTEXT_QUERY_PARAMS) {
      const values = params?.getAll(key) ?? [];
      if (values.length > 0) {
        queryParams[key] = values;
      }
    }
    return this.router.serializeUrl(
      this.router.createUrlTree(['/', this.store.entity()], {queryParams}),
    );
  });

  /**
   * Query parameters of the `/assistant` link, which asks the typed text as
   * the user wrote it, about this search page.
   */
  readonly askAssistantQueryParams = computed<Record<string, string>>(() =>
    assistantLinkQueryParams(
      this.commonParamsService.getCommonParams(),
      this.noMatchesQueryText(),
      this.askAssistantPage(),
    ),
  );

  /** Opens the Ask Assistant link in a new tab, as clicking it does. */
  openAskAssistant(): void {
    this.askAssistantLink()?.nativeElement.click();
  }

  /** Whether the 'Auto-collapse after applying' checkbox is checked (defaults to true if not in storage). */
  readonly isAutoCollapseChecked = this.store.autoCollapseAfterApply;

  /** Stable tracking function for suggestion items in `@for` loop to avoid DOM churn. */
  readonly trackSuggestion = trackSuggestion;

  /** Formats the suggestion count badge text according to BFF count specifications. */
  readonly formatCount = formatSuggestionCount;

  /** Tooltip for counts that exceed maximum scan threshold. */
  readonly getCountTooltip = getSuggestionCountTooltip;

  /** Emits `selectSuggestion` when a typeahead suggestion row is clicked. */
  onItemClick(item: SearchBoxSuggestion) {
    this.selectSuggestion.emit({item});
  }

  /** Emits `editChip` when an active filter chip inside the popover is clicked. */
  onChipClick(chip: FilterChip, anchor: HTMLElement, event: MouseEvent) {
    event.stopPropagation();
    if (chip.isGroupBy) return;
    this.editChip.emit({chip, anchor, event});
  }

  /** Removes a filter or group-by chip and collapses the popover if auto-collapse is enabled. */
  onRemoveChip(chip: FilterChip, event: MouseEvent) {
    event.stopPropagation();
    this.store.removeFilterChip(chip);
    if (this.store.autoCollapseAfterApply()) {
      this.collapsePanel.emit();
    }
  }

  /** Opens the FilterValuePicker anchored to a pending filter chip when clicked. */
  onPendingChipClick(
    pf: PendingFilterItem,
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

  /** Opens the KeyPicker dialog in `'filter'` mode and notifies the parent search input. */
  onAddFilterClick(event: MouseEvent) {
    event.stopPropagation();
    this.openKeyPicker('filter');
    this.addFilter.emit();
  }

  /** Opens the KeyPicker dialog in `'groupby'` mode and notifies the parent search input. */
  onAddGroupByClick(event: MouseEvent) {
    event.stopPropagation();
    if (this.isAddGroupByDisabled()) {
      return;
    }
    this.openKeyPicker('groupby');
    this.addGroupBy.emit();
  }

  /** Clears all search criteria and collapses the suggestions popover. */
  onClearAll(event: MouseEvent) {
    event.stopPropagation();
    this.store.resetSearchState();
    this.collapsePanel.emit();
  }

  /** Updates the persisted auto-collapse-after-apply preference when the footer checkbox is toggled. */
  onAutoCollapseChange(event: Event) {
    event.stopPropagation();
    const target = event.target as HTMLInputElement | null;
    if (target) {
      this.store.setAutoCollapseAfterApply(target.checked);
    }
  }

  /** Emits `collapsePanel` when the Collapse button in the popover footer is clicked. */
  onCollapse(event: MouseEvent) {
    event.stopPropagation();
    this.collapsePanel.emit();
  }
}
