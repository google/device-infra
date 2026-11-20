/**
 * @fileoverview Abstract base reactive store managing shared search bar state, active filter/group-by chips, ValuePicker overlay, and URL synchronization.
 */

import {
  computed,
  DestroyRef,
  inject,
  Injectable,
  linkedSignal,
  Signal,
  signal,
  WritableSignal,
} from '@angular/core';
import {toObservable, toSignal} from '@angular/core/rxjs-interop';
import {ActivatedRoute, NavigationEnd, Router} from '@angular/router';
import {Observable} from 'rxjs';
import {debounceTime, distinctUntilChanged, filter, map} from 'rxjs/operators';

import {
  Column,
  Filter,
  FleetSuggestion,
  Row,
  TjsFilter,
  TjsSuggestion,
} from '../../../core/models/search';
import {EnvUniverseService} from '../../../core/services/env_universe_service';
import {SEARCH_SERVICE} from '../../../core/services/search/search_service';
import {
  DEFAULT_PAGE_SIZE,
  EntityType,
  FilterChip,
  FilterKeyMetadata,
  FleetScope,
  GroupSortOption,
  MAX_GROUP_BY_KEYS,
  PromotedFilterKeyItem,
  PromotedGroupByKeyItem,
  SearchBoxSuggestion,
  SearchPageConfig,
  TableDensity,
  ValuePickerApplyEvent,
  ValuePickerConfig,
  ValuePickerState,
} from '../models';
import {
  buildUrlParamKey,
  createGroupByChip,
  getChipKey,
  getInitialRouterUrl,
  getStoredAutoCollapseAfterApply,
  isSameFilterChip,
  isSearchRouteActive,
  normalizeKey,
  resolveEntityFromPathOrUrl,
  resolveFleetScope,
  resolveInitialChips,
  saveStoredAutoCollapseAfterApply,
  serializeFilterChip,
  upsertFilterChipInList,
} from '../utils';
import {useSearchUrlSync, useValuePickerOverlay} from './slices';

/** Abstract base store defining shared search bar state, filter chips, and URL synchronization. */
@Injectable()
export abstract class SearchPageStore<
  TSuggestion = FleetSuggestion | TjsSuggestion,
  TFilter = Filter | TjsFilter,
> {
  protected readonly searchService = inject(SEARCH_SERVICE);
  readonly destroyRef = inject(DestroyRef);
  readonly route = inject(ActivatedRoute);
  readonly router = inject(Router);
  protected readonly envUniverseService = inject(EnvUniverseService);

  readonly supportsFleetScope: boolean = false;
  protected readonly supportsLandingPage: boolean = false;

  /** Returns the set of entity types supported by this store implementation. */
  protected abstract getSupportedEntities(): Set<EntityType>;
  /** Returns the default entity type when the URL does not specify a supported entity. */
  protected abstract getDefaultEntity(): EntityType;

  /** Returns true if the given entity string is supported by this store. */
  isEntitySupported(entity: string): boolean {
    return this.getSupportedEntities().has(entity as EntityType);
  }

  protected readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects || e.url),
    ),
    {initialValue: getInitialRouterUrl(this.router)},
  );

  readonly entity: Signal<EntityType> = signal(
    this.isEntitySupported(this.route.routeConfig?.path ?? '')
      ? (this.route.routeConfig!.path as EntityType)
      : resolveEntityFromPathOrUrl(
          getInitialRouterUrl(this.router),
          this.getSupportedEntities(),
          this.getDefaultEntity(),
        ),
  ).asReadonly();

  readonly isCurrentRouteActive = computed<boolean>(() =>
    isSearchRouteActive(this.currentUrl(), this.entity()),
  );

  readonly isFleetSwitchable = this.envUniverseService.isGoogleInternal();
  readonly fleet = signal<FleetScope>(
    resolveFleetScope(this.route.snapshot?.queryParams, this.isFleetSwitchable),
  );
  readonly searchQuery = signal<string>('');

  protected readonly debouncedSearchQuery = toSignal(
    toObservable(this.searchQuery).pipe(
      debounceTime(300),
      distinctUntilChanged(),
    ),
    {initialValue: this.searchQuery()},
  );

  readonly activeChips: WritableSignal<FilterChip[]> = signal<FilterChip[]>(
    resolveInitialChips(this.route),
  );
  readonly browseAll = signal<boolean>(false);
  readonly showSuggestions = signal<boolean>(false);
  readonly focusInputTrigger = signal<boolean>(false);

  readonly autoCollapseAfterApply = linkedSignal<
    {entity: string; fleet: string; open: boolean},
    boolean
  >({
    source: () => ({
      entity: this.entity(),
      fleet: this.fleet(),
      open: this.showSuggestions(),
    }),
    computation: ({entity, fleet}) =>
      getStoredAutoCollapseAfterApply(entity, fleet) ?? true,
  });

  /** Updates and persists the auto-collapse-after-apply preference for the current entity and fleet. */
  setAutoCollapseAfterApply(enabled: boolean): void {
    this.autoCollapseAfterApply.set(enabled);
    saveStoredAutoCollapseAfterApply(this.entity(), this.fleet(), enabled);
  }

  readonly filterChips = computed<FilterChip[]>(() =>
    this.activeChips().filter((c) => !c.isGroupBy),
  );
  readonly groupByChips = computed<FilterChip[]>(() =>
    this.activeChips().filter((c) => c.isGroupBy),
  );
  readonly groupByKeys = computed<string[]>(
    () => this.groupByChips().map(getChipKey).filter(Boolean),
    {equal: (a, b) => a.length === b.length && a.every((k, i) => k === b[i])},
  );

  private readonly pickerOverlay = useValuePickerOverlay(this);

  readonly showValuePicker = this.pickerOverlay.showValuePicker;
  readonly pickerAnchor = this.pickerOverlay.pickerAnchor;
  readonly pickerConfig = this.pickerOverlay.pickerConfig;
  readonly pickerState = this.pickerOverlay.pickerState;
  readonly pickerKey = this.pickerOverlay.pickerKey;
  readonly pickerTitle = this.pickerOverlay.pickerTitle;
  readonly pendingFilter = this.pickerOverlay.pendingFilter;
  readonly focusChipKey = this.pickerOverlay.focusChipKey;
  readonly focusChipType = this.pickerOverlay.focusChipType;
  readonly pendingFilterOpenRequest =
    this.pickerOverlay.pendingFilterOpenRequest;
  readonly pendingFilterKey = this.pickerOverlay.pendingFilterKey;
  readonly pendingFilterTitle = this.pickerOverlay.pendingFilterTitle;
  readonly effectivePickerState: Signal<ValuePickerState> =
    this.pickerState.asReadonly();
  readonly isChipPickerActive = this.pickerOverlay.isChipPickerActive;
  readonly isKeyPickerActive = this.pickerOverlay.isKeyPickerActive;
  readonly openQuickFilter = this.pickerOverlay.openQuickFilter;
  readonly openQuickGroupBy = this.pickerOverlay.openQuickGroupBy;
  readonly openValuePicker = this.pickerOverlay.openValuePicker;
  readonly closeValuePicker = this.pickerOverlay.closeValuePicker;

  readonly pageIndex = linkedSignal({
    source: () => ({chips: this.activeChips(), query: this.searchQuery()}),
    computation: () => 0,
  });
  readonly pageSize = signal<number>(DEFAULT_PAGE_SIZE);
  readonly pageToken = linkedSignal({
    source: () => ({chips: this.activeChips(), query: this.searchQuery()}),
    computation: () => '',
  });
  readonly density = signal<TableDensity>('default');

  /** Updates the page size and resets pagination to the first page. */
  setPageSize(newSize: number): void {
    this.pageSize.set(newSize);
    this.resetPagination();
  }

  /** Resets pagination index and cursor token to the first page. */
  resetPagination(): void {
    this.pageIndex.set(0);
    this.pageToken.set('');
  }

  readonly isTjs: Signal<boolean> = signal(false);
  readonly showScopeSwitcher = computed(
    () => this.supportsFleetScope && this.isFleetSwitchable,
  );
  readonly searchPlaceholder = computed(() => `Search ${this.entity()}...`);
  readonly hasActiveFilters = computed<boolean>(
    () => this.activeChips().length > 0 || this.searchQuery().trim().length > 0,
  );
  readonly showSearchClear = computed<boolean>(
    () => this.searchQuery().length > 0,
  );

  /** Clears the user's typed text in the search input box. */
  clearSearchQuery(): void {
    this.searchQuery.set('');
  }

  readonly isLandingDisabled = computed<boolean>(() => {
    const landing = this.searchConfig()?.landing;
    return landing != null && landing.enabled !== true;
  });

  readonly isLandingState = computed<boolean>(() => {
    if (!this.supportsLandingPage || this.isLandingDisabled()) return false;
    return this.activeChips().length === 0 && !this.browseAll();
  });

  readonly isSuggestionsPanelOpen = computed<boolean>(
    () =>
      this.showSuggestions() &&
      (!this.isTjs() ||
        this.activeChips().length > 0 ||
        Boolean(this.pendingFilterKey()) ||
        this.isSuggestionsLoading() ||
        this.suggestions().length > 0 ||
        Boolean(this.searchQuery().trim())),
  );

  readonly serializedActiveFilters = computed<string[]>(() =>
    this.filterChips().map(serializeFilterChip).filter(Boolean),
  );
  readonly currentUrlParamKey = computed<string>(() =>
    buildUrlParamKey(
      this.serializedActiveFilters(),
      this.groupByKeys(),
      this.fleet(),
    ),
  );

  abstract readonly suggestions: Signal<SearchBoxSuggestion[]>;
  readonly isSuggestionsLoading: Signal<boolean> = signal(false);

  /** Returns the debounced search query when the suggestions popover is active, or null when inactive. */
  getActiveSuggestionInput(): string | null {
    if (!this.isCurrentRouteActive() || !this.showSuggestions()) return null;
    const rawInput = this.searchQuery().trim();
    const debouncedInput = this.debouncedSearchQuery().trim();
    if (rawInput && !debouncedInput) return null;
    return rawInput ? debouncedInput : '';
  }

  /** Creates a computed signal indicating whether suggestions are loading or debouncing. */
  protected computeSuggestionsLoading(
    resourceIsLoading: Signal<boolean>,
  ): Signal<boolean> {
    return computed(() => {
      if (!this.showSuggestions()) return false;
      const rawInput = this.searchQuery().trim();
      return (
        resourceIsLoading() ||
        (Boolean(rawInput) && rawInput !== this.debouncedSearchQuery().trim())
      );
    });
  }

  abstract readonly isLoading: Signal<boolean>;
  abstract readonly effectiveFilters: Signal<TFilter[]>;
  abstract readonly searchConfig: Signal<SearchPageConfig | null>;
  abstract readonly isConfigLoading: Signal<boolean>;
  abstract readonly promotedFilterKeys: Signal<PromotedFilterKeyItem[]>;
  readonly promotedGroupByKeys: Signal<PromotedGroupByKeyItem[]> = signal([]);
  readonly groupSortOptions: Signal<GroupSortOption[]> = signal([]);
  abstract readonly isPromotedKeysLoading: Signal<boolean>;
  abstract readonly keyMetadataMap: Signal<Map<string, FilterKeyMetadata>>;

  /** Looks up metadata for a filter or group-by key (case-insensitively). */
  getKeyMetadata(key?: string): FilterKeyMetadata | undefined {
    if (!key) return undefined;
    const map = this.keyMetadataMap?.();
    return map?.get(key) || map?.get(key.toLowerCase());
  }

  abstract readonly displayColumns: Signal<Column[]>;
  abstract readonly rows: Signal<Row[]>;
  abstract readonly hasNextPage: Signal<boolean>;
  readonly hasPrevPage = computed<boolean>(() => this.pageIndex() > 0);
  abstract readonly rangeText: Signal<string>;
  readonly effectiveTotalCount: Signal<number | null> = signal<number | null>(
    null,
  );

  /** Resets pagination and triggers a fresh search RPC. */
  abstract executeSearch(): void;
  /** Applies the selection from the FilterValuePicker overlay. */
  abstract applyValuePicker(event: ValuePickerApplyEvent): void;
  /** Navigates to the previous page of results. */
  abstract prevPage(): void;
  /** Navigates to the next page of results. */
  abstract nextPage(): void;

  /** Reads the active ValuePicker key/metadata, closes the picker overlay, and updates suggestion visibility. */
  protected consumeActivePicker() {
    const key = this.pickerKey();
    const meta = key ? this.getKeyMetadata(key) : undefined;
    const displayTitle = meta?.keyDisplayName || this.pickerTitle();
    this.closeValuePicker();
    this.showSuggestions.set(!this.autoCollapseAfterApply());
    return {key, meta, displayTitle};
  }

  /** Applies a selected typeahead suggestion item and updates popover visibility. */
  selectSuggestion(item: SearchBoxSuggestion): void {
    if (!item) return;
    this.searchQuery.set('');
    if (item.openPicker) {
      this.showSuggestions.set(true);
    } else {
      this.closeValuePicker();
      this.showSuggestions.set(!this.autoCollapseAfterApply());
    }
    this.applySuggestion(item as TSuggestion);
  }

  /** Domain-specific handler for applying a selected BFF suggestion item. */
  protected abstract applySuggestion(rawItem: TSuggestion): void;
  /** Validates and enriches parsed URL filter and group-by chips via the backend BFF. */
  abstract resolveChipsFromBackend(
    parsedFilters: FilterChip[],
    groupByKeys: string[],
    source?: string,
  ): Observable<FilterChip[]>;
  /** Builds the ValuePicker configuration for the specified filter key. */
  abstract buildPickerConfig(
    key: string,
    title?: string,
    metadata?: FilterKeyMetadata,
  ): ValuePickerConfig;
  /** Builds the initial ValuePicker state for the specified filter key and active chip. */
  abstract buildInitialPickerState(
    key: string,
    activeChip?: FilterChip,
    metadata?: FilterKeyMetadata,
    stagedValues?: string[],
  ): ValuePickerState;

  /** Returns the default filter chips applied when no URL criteria are present. */
  getDefaultChips(): FilterChip[] {
    return [];
  }

  /** Cancels any in-flight legacy query parameter translation. */
  cancelPendingLegacyTranslation(): void {}
  /** Returns legacy URL query parameter keys that should be stripped on URL sync. */
  getLegacyQueryParamKeys(): readonly string[] {
    return [];
  }
  /** Handles legacy URL query parameters if present, returning true if handled. */
  handleLegacyQueryParams(_params: Record<string, unknown>): boolean {
    return false;
  }

  private readonly urlSync = useSearchUrlSync(this);

  readonly isResolvingChips = this.urlSync.isResolvingChips;
  readonly cancelPendingChipResolution =
    this.urlSync.cancelPendingChipResolution;
  readonly syncUrl = this.urlSync.syncUrl;

  /** Switches the active fleet scope ('internal' or 'ats') and resets search criteria. */
  setFleet(fleet: 'internal' | 'ats'): void {
    if (this.fleet() === fleet) return;
    this.fleet.set(fleet);
    this.resetSearchState(false);
    this.syncUrl(this.getDefaultChips(), fleet);
  }

  /** Inserts or replaces a filter or group-by chip and synchronizes the URL. */
  addFilterChip(chip: FilterChip): void {
    const nextChips = upsertFilterChipInList(this.activeChips(), chip);
    this.activeChips.set(nextChips);
    this.syncUrl(nextChips);
  }

  /** Commits a chip list after removal, updating browseAll, suggestions visibility, and the URL. */
  private commitChipsRemoval(nextChips: FilterChip[]): void {
    this.activeChips.set(nextChips);
    if (nextChips.length === 0 && !this.searchQuery().trim()) {
      this.browseAll.set(false);
    }
    if (this.autoCollapseAfterApply()) {
      this.showSuggestions.set(false);
    }
    this.syncUrl(nextChips);
  }

  /** Removes a specific filter or group-by chip and closes the ValuePicker if it was editing that chip. */
  removeFilterChip(chip: FilterChip): void {
    const nextChips = this.activeChips().filter(
      (c) => !isSameFilterChip(c, chip),
    );
    const currentPickerKey = this.pickerConfig()?.key;
    if (
      this.showValuePicker() &&
      (nextChips.length === 0 ||
        !currentPickerKey ||
        isSameFilterChip(chip, {key: currentPickerKey, isGroupBy: false}))
    ) {
      this.closeValuePicker();
    }
    this.commitChipsRemoval(nextChips);
  }

  /** Removes all active filter chips while preserving group-by chips. */
  clearFilterChips(): void {
    if (this.showValuePicker()) this.closeValuePicker();
    this.commitChipsRemoval(this.groupByChips());
  }

  /** Removes all active group-by chips while preserving filter chips. */
  clearGroupByChips(): void {
    this.commitChipsRemoval(this.filterChips());
  }

  /** Toggles a group-by key chip on or off (up to `MAX_GROUP_BY_KEYS`). */
  toggleGroupBy(gbKey: string, displayName?: string): void {
    const currentGbKeys = this.groupByKeys();
    if (currentGbKeys.includes(gbKey)) {
      const existing = this.activeChips().find((c) =>
        isSameFilterChip(c, {key: gbKey, isGroupBy: true}),
      );
      if (existing) this.removeFilterChip(existing);
      return;
    }
    if (currentGbKeys.length < MAX_GROUP_BY_KEYS) {
      this.addFilterChip(createGroupByChip(gbKey, displayName));
    }
  }

  /** Removes the active filter chip for the given key or closes the ValuePicker if open for that key. */
  removeChipForKey(key: string): void {
    const existing = this.activeChips().find((c) =>
      isSameFilterChip(c, {key, isGroupBy: false}),
    );
    if (existing) {
      this.removeFilterChip(existing);
      return;
    }
    const currentPickerKey = this.pickerConfig()?.key;
    if (
      this.showValuePicker() &&
      currentPickerKey &&
      normalizeKey(key) === normalizeKey(currentPickerKey)
    ) {
      this.closeValuePicker();
    }
  }

  /** Resets transient UI state (query text, browseAll, suggestions popover, and ValuePicker). */
  protected resetTransientUiState(): void {
    this.searchQuery.set('');
    this.browseAll.set(false);
    this.showSuggestions.set(false);
    this.focusInputTrigger.set(false);
    this.closeValuePicker();
  }

  /** Clears all active chips and resets transient UI state. */
  resetSearchState(updateUrl = true): void {
    this.applySearchState([], updateUrl);
  }

  /** Restores default filter chips and resets transient UI state. */
  restoreDefaultState(updateUrl = false): void {
    this.applySearchState(this.getDefaultChips(), updateUrl);
  }

  /** Cancels pending chip resolution, resets transient UI state, and applies the given chip list. */
  protected applySearchState(chips: FilterChip[], updateUrl: boolean): void {
    this.cancelPendingChipResolution();
    this.resetTransientUiState();
    if (chips.length > 0 || this.activeChips().length > 0) {
      this.activeChips.set(chips);
    }
    if (updateUrl) this.syncUrl(chips);
  }
}
