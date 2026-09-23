import {CdkOverlayOrigin} from '@angular/cdk/overlay';
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
import {
  takeUntilDestroyed,
  toObservable,
  toSignal,
} from '@angular/core/rxjs-interop';
import {ActivatedRoute, NavigationEnd, Router} from '@angular/router';
import {asapScheduler, Observable, of, Subject} from 'rxjs';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  filter,
  map,
  observeOn,
  switchMap,
} from 'rxjs/operators';

import {Column, Row} from '../../../core/models/search';
import {SEARCH_SERVICE} from '../../../core/services/search/search_service';
import {
  EntityType,
  FilterChip,
  FilterKeyMetadata,
  INITIAL_VALUE_PICKER_STATE,
  PromotedFilterKeyItem,
  PromotedGroupByKeyItem,
  SearchBoxSuggestion,
  SearchPageConfig,
  ValuePickerApplyEvent,
  ValuePickerConfig,
  ValuePickerState,
} from '../models';
import {
  buildUrlParamKey,
  createGroupByChip,
  getChipKey,
  getInitialRouterUrl,
  getQueryParamAsArray,
  getSerializedChipsKey,
  getStoredAutoCollapseAfterApply,
  isChipNegated,
  isSameFilterChip,
  isSearchRouteActive,
  normalizeKey,
  parseUrlChips,
  resolveEntityFromPathOrUrl,
  resolveInitialChips,
  resolveInitialFleet,
  saveStoredAutoCollapseAfterApply,
  serializeFilterChip,
} from '../utils';

/**
 * Abstract base store defining shared search state, reactive signals, and actions.
 *
 * Implements the Template Method pattern to provide domain-agnostic search bar
 * interactions, filter chip state machine, URL resolution, and pagination
 * while delegating domain-specific RPC and rendering details to subclasses.
 */
@Injectable()
export abstract class SearchPageStore {
  // ===========================================================================
  // 1. Injected Dependencies
  // ===========================================================================

  /** Backend search service handling search queries and filter chip resolution. */
  protected readonly searchService = inject(SEARCH_SERVICE);

  /** Component/Store lifecycle destruction reference for cleaning up streams. */
  protected readonly destroyRef = inject(DestroyRef);

  /** Active route reference for reading and reacting to query parameters. */
  protected readonly route = inject(ActivatedRoute);

  /** Router service for navigating and syncing filter states to the browser URL. */
  protected readonly router = inject(Router);

  // ===========================================================================
  // 2. Core Domain & Query Signals
  // ===========================================================================

  /** Checks whether the given entity type is supported by this domain store. */
  isEntitySupported(entity: string): boolean {
    return this.getSupportedEntities().has(entity as EntityType);
  }

  /** Supported entity types for this store. */
  protected abstract getSupportedEntities(): Set<EntityType>;

  /** Subclass hook providing default entity type when route metadata is absent. */
  protected abstract getDefaultEntity(): EntityType;

  /** Reactive signal tracking the current active URL from NavigationEnd router events. */
  protected readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects || e.url),
    ),
    {initialValue: getInitialRouterUrl(this.router)},
  );

  /**
   * Specific entity type this store instance is permanently bound to.
   * Derived from the RouteConfig path (e.g. 'devices', 'hosts', 'tests', 'jobs', 'sessions')
   * with fallback to initial URL resolution or subclass default.
   */
  readonly entity: Signal<EntityType> = (() => {
    const routePath = this.route.routeConfig?.path;
    if (routePath && this.isEntitySupported(routePath)) {
      return signal(routePath as EntityType).asReadonly();
    }
    const fromUrl = resolveEntityFromPathOrUrl(
      getInitialRouterUrl(this.router),
      this.getSupportedEntities(),
      this.getDefaultEntity(),
    );
    return signal(fromUrl).asReadonly();
  })();

  /**
   * Reactive computed signal validating whether the active browser URL belongs to THIS store instance.
   * Adheres to Angular 20+ Signal graph and Google3 BFF exact-entity boundary.
   */
  readonly isCurrentRouteActive = computed<boolean>(() => {
    return isSearchRouteActive(this.currentUrl(), this.entity());
  });

  /** Fleet operational scope ('internal' vs 'ats'). */
  readonly fleet = signal<'internal' | 'ats'>(resolveInitialFleet(this.route));

  /** Raw text input query string entered by the user in the search box. */
  readonly searchQuery = signal<string>('');

  /** Debounced search query Signal (300ms debounce + distinctUntilChanged). */
  protected readonly debouncedSearchQuery = toSignal(
    toObservable(this.searchQuery).pipe(
      debounceTime(300),
      distinctUntilChanged(),
    ),
    {initialValue: ''},
  );

  /** Active list of filter and group-by chips applied to the current search. */
  readonly activeChips: WritableSignal<FilterChip[]> = signal<FilterChip[]>(
    resolveInitialChips(this.route),
  );

  /** Indicates whether the user is browsing all records with no filters (Fleet only). */
  readonly browseAll = signal<boolean>(false);

  /** Controls visibility of the search box suggestions popover dropdown. */
  readonly showSuggestions = signal<boolean>(false);

  /** Per-user preference controlling whether the suggestions popover auto-collapses after applying a filter or group-by (defaults to true when absent in localStorage). */
  readonly autoCollapseAfterApply = linkedSignal<
    {entity: string; fleet: string; open: boolean},
    boolean
  >({
    source: () => ({
      entity: this.entity(),
      fleet: this.fleet(),
      open: this.showSuggestions(),
    }),
    computation: ({entity, fleet}) => {
      const stored = getStoredAutoCollapseAfterApply(entity, fleet);
      return stored !== null ? stored : true;
    },
  });

  /** Updates and persists the auto-collapse preference to localStorage. */
  setAutoCollapseAfterApply(enabled: boolean) {
    this.autoCollapseAfterApply.set(enabled);
    saveStoredAutoCollapseAfterApply(this.entity(), this.fleet(), enabled);
  }

  // ===========================================================================
  // 3. ValuePicker Overlay State Signals
  // ===========================================================================

  /** Controls visibility of the ValuePicker CDK Overlay popover. */
  readonly showValuePicker = signal<boolean>(false);

  /** Dynamic DOM anchor or CDK Overlay Origin where ValuePicker is currently positioned. */
  readonly pickerAnchor = signal<CdkOverlayOrigin | HTMLElement | null>(null);

  /** Static layout and feature configuration for the currently open ValuePicker. */
  readonly pickerConfig = signal<ValuePickerConfig | null>(null);

  /** User interaction state within the ValuePicker (selected values, negation, advanced mode). */
  readonly pickerState = signal<ValuePickerState>(INITIAL_VALUE_PICKER_STATE);

  /** Target filter key currently being edited in the ValuePicker. */
  readonly pickerKey = computed(() => this.pickerConfig()?.key || '');

  /** Display title for the currently open ValuePicker. */
  readonly pickerTitle = computed(() => this.pickerConfig()?.title || '');

  /** Temporary filter key definition staged from a quick filter link before values are applied. */
  readonly pendingFilter = signal<{
    key: string;
    displayName: string;
    metadata?: unknown;
  } | null>(null);

  /** Specific chip key currently focused or pulsed inside the suggestions popover. */
  readonly focusChipKey = signal<string | null>(null);

  /**
   * Effective ValuePicker state presented to the popover overlay.
   * Subclasses with asynchronous option fetching (e.g. FleetSearchStore) override this.
   */
  readonly effectivePickerState: Signal<ValuePickerState> =
    this.pickerState.asReadonly();

  // ===========================================================================
  // 4. Pagination & Table Display State Signals
  // ===========================================================================

  /** Current zero-based page index. Automatically resets to 0 when chips or query change. */
  readonly pageIndex = linkedSignal({
    source: () => ({chips: this.activeChips(), query: this.searchQuery()}),
    computation: () => 0,
  });

  /** Number of rows displayed per page. */
  readonly pageSize = signal<number>(25);

  /** Updates the page size and resets page navigation cursor and index. */
  setPageSize(newSize: number) {
    this.pageSize.set(newSize);
    this.pageIndex.set(0);
    this.pageToken.set('');
  }

  /** Opaque cursor token for backend pagination. Resets when chips or query change. */
  readonly pageToken = linkedSignal({
    source: () => ({chips: this.activeChips(), query: this.searchQuery()}),
    computation: () => '',
  });

  /** Table row display density ('compact', 'default', or 'comfortable'). */
  readonly density = signal<'compact' | 'default' | 'comfortable'>('default');

  // ===========================================================================
  // 5. Derived Computed Signals
  // ===========================================================================

  /** Whether the current entity belongs to the Test/Job/Session domain. */
  readonly isTjs = computed(() => {
    const e = this.entity();
    return e === 'tests' || e === 'jobs' || e === 'sessions';
  });

  /** Determines whether the Fleet scope switcher (Internal vs ATS) should be displayed. */
  readonly showScopeSwitcher = computed(() => !this.isTjs());

  /** Placeholder text for the main search input box. */
  readonly searchPlaceholder = computed(() => `Search ${this.entity()}...`);

  /** Whether the user currently has active filter chips or a search query applied. */
  readonly hasActiveFilters = computed<boolean>(
    () => this.activeChips().length > 0 || this.searchQuery().trim().length > 0,
  );

  /** Whether the clear button ('X') in the search bar should be displayed (only when text input is non-empty). */
  readonly showSearchClear = computed<boolean>(
    () => this.searchQuery().length > 0,
  );

  /** Clears only the text input in the search box without removing active filter or group-by chips. */
  clearSearchQuery() {
    this.searchQuery.set('');
  }

  /** Whether the search view is currently in its initial landing / empty guide state. */
  readonly isLandingState = computed<boolean>(() => {
    if (this.isTjs()) return false;
    return this.activeChips().length === 0 && !this.browseAll();
  });

  /** Active filter chips (excluding group-by chips). */
  readonly filterChips = computed<FilterChip[]>(() =>
    this.activeChips().filter((c) => !c.isGroupBy),
  );

  /** Active group-by chips. */
  readonly groupByChips = computed<FilterChip[]>(() =>
    this.activeChips().filter((c) => c.isGroupBy),
  );

  /** Key of a filter currently being edited in ValuePicker or pending via Quick Filter, not yet applied as an active chip. */
  readonly pendingFilterKey = computed<string | null>(() => {
    const pf = this.pendingFilter();
    const pk = pf?.key || (this.showValuePicker() ? this.pickerKey() : null);
    if (!pk) return null;
    const hasApplied = this.filterChips().some(
      (c) => getChipKey(c).toLowerCase() === pk.toLowerCase(),
    );
    return hasApplied ? null : pk;
  });

  /** Display title for the pending filter key being edited. */
  readonly pendingFilterTitle = computed<string>(() => {
    return (
      this.pendingFilter()?.displayName ||
      this.pickerTitle() ||
      this.pendingFilterKey() ||
      ''
    );
  });

  /** Whether the suggestions popover panel should be open and visible below the search box. */
  readonly isSuggestionsPanelOpen = computed<boolean>(
    () =>
      this.showSuggestions() &&
      (this.activeChips().length > 0 ||
        !!this.pendingFilterKey() ||
        this.isSuggestionsLoading() ||
        this.suggestions().length > 0),
  );

  /** List of active group-by field keys extracted from `groupByChips`. */
  readonly groupByKeys = computed<string[]>(
    () => this.groupByChips().map(getChipKey).filter(Boolean),
    {
      equal: (a, b) => a.length === b.length && a.every((k, i) => k === b[i]),
    },
  );

  /** Serialized active filter parameter strings formatted for URL query. */
  readonly serializedActiveFilters = computed<string[]>(() =>
    this.filterChips().map(serializeFilterChip).filter(Boolean),
  );

  /** Canonical URL query parameter key computed from current active state. */
  readonly currentUrlParamKey = computed<string>(() =>
    buildUrlParamKey(
      this.serializedActiveFilters(),
      this.groupByKeys(),
      this.fleet(),
    ),
  );

  // ===========================================================================
  // 6. Abstract Domain Interface (Template Method Hooks)
  // ===========================================================================

  /** Domain-specific autocomplete and recommendation suggestions for the search box. */
  abstract readonly suggestions: Signal<SearchBoxSuggestion[]>;

  /** Whether search box autocomplete suggestions are currently loading. */
  readonly isSuggestionsLoading: Signal<boolean> = signal(false);

  /** Loading state for primary search result data fetching. */
  abstract readonly isLoading: Signal<boolean>;

  /** Initial page configuration including metadata, default chips, and column definitions. */
  abstract readonly searchConfig: Signal<SearchPageConfig | null>;

  /** Whether initial search configuration is loading. */
  abstract readonly isConfigLoading: Signal<boolean>;

  /** Promoted / recommended filter keys shown as quick-action presets. */
  abstract readonly promotedFilterKeys: Signal<PromotedFilterKeyItem[]>;

  /** Promoted / recommended group-by keys shown as quick-action presets. */
  readonly promotedGroupByKeys: Signal<PromotedGroupByKeyItem[]> = signal([]);

  /** Options list for sorting group header cards in grouped mode. */
  readonly groupSortOptions: Signal<Array<{value: string; label: string}>> =
    signal([]);

  /** Whether auxiliary promoted keys are currently loading. */
  abstract readonly isPromotedKeysLoading: Signal<boolean>;

  /** Map of filter key metadata for display names, types, and capability flags. */
  abstract readonly keyMetadataMap: Signal<Map<string, FilterKeyMetadata>>;

  /** Safe lookup for key metadata across domain implementations. */
  getKeyMetadata(key?: string): FilterKeyMetadata | undefined {
    if (!key) return undefined;
    const map = this.keyMetadataMap?.();
    return map?.get(key) || map?.get(key.toLowerCase());
  }

  /** Visible column definitions for the search results table. */
  abstract readonly displayColumns: Signal<Column[]>;

  /** Result rows for the current page of search results. */
  abstract readonly rows: Signal<Row[]>;

  /** Whether a subsequent page of search results is available. */
  abstract readonly hasNextPage: Signal<boolean>;

  /** Whether a preceding page of search results is available based on current page index. */
  readonly hasPrevPage = computed<boolean>(() => this.pageIndex() > 0);

  /** Pre-formatted semantic range text for pagination footer display (e.g. "1 – 25 of 1,250", "showing 1–25"). */
  abstract readonly rangeText: Signal<string>;

  /** Total matching count across search modes, or null if not available (e.g. in TJS cursor pagination). */
  readonly effectiveTotalCount: Signal<number | null> = signal<number | null>(null);

  /** Triggers execution of the primary domain search query. */
  abstract executeSearch(): void;

  /** Applies selections from the ValuePicker to create or update filter chips. */
  abstract applyValuePicker(event: ValuePickerApplyEvent): void;

  /** Navigates to the previous page of search results. */
  abstract prevPage(): void;

  /** Navigates to the next page of search results. */
  abstract nextPage(): void;

  /** Handles user selection of an autocomplete suggestion from the search box popover. */
  selectSuggestion(item: SearchBoxSuggestion, anchor?: HTMLElement | null) {
    if (!item.rawItem) return;
    this.searchQuery.set('');
    if (item.openPicker) {
      this.showSuggestions.set(true);
    } else {
      this.showSuggestions.set(!this.autoCollapseAfterApply());
    }
    this.applySuggestion(item.rawItem, anchor);
  }

  /** Subclass hook to unpack domain-specific suggestion payload and apply to store. */
  protected abstract applySuggestion(
    rawItem: unknown,
    anchor?: HTMLElement | null,
  ): void;

  /** Subclass hook to resolve chips from domain-specific backend RPC. */
  protected abstract resolveChipsFromBackend(
    parsedFilters: FilterChip[],
    groupByKeys: string[],
  ): Observable<FilterChip[]>;

  /** Subclass hook to construct ValuePicker configuration for a filter key. */
  protected abstract buildPickerConfig(
    key: string,
    title?: string,
    metadata?: unknown,
  ): ValuePickerConfig;

  /** Subclass hook to construct initial ValuePicker state for a filter key. */
  protected abstract buildInitialPickerState(
    key: string,
    activeChip?: FilterChip,
    metadata?: unknown,
    stagedValues?: string[],
  ): ValuePickerState;

  /** Subclass hook to provide default filter chips on initial state / reset. Defaults to empty array. */
  getDefaultChips(): FilterChip[] {
    return [];
  }

  // ===========================================================================
  // 7. URL Serialization & Deserialization Methods
  // ===========================================================================

  /** Tracks the last URL query parameter key that was successfully synced or processed. */
  private lastSyncedUrlKey: string | null = null;

  /** Flag to prevent internal URL synchronizations from re-triggering navigation reset handlers. */
  private isInternalUrlSync = false;

  /** Subject driving asynchronous filter chip resolution with race condition protection via switchMap. */
  private readonly resolveFiltersSubject = new Subject<{
    parsedFilters: FilterChip[];
    gbKeys: string[];
  }>();

  constructor() {
    // Asynchronously enhance chips with rich backend metadata via race-free switchMap
    this.resolveFiltersSubject
      .pipe(
        switchMap(({parsedFilters, gbKeys}) => {
          if (parsedFilters.length === 0 && gbKeys.length === 0) {
            return of([]);
          }
          return this.resolveChipsFromBackend(parsedFilters, gbKeys).pipe(
            map((resolvedChips) => resolvedChips || []),
            catchError(() => of([])),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (updatedChips) => {
          // Scenario 1 & 5: Clear or update activeChips directly with backend resolved result
          this.activeChips.set(updatedChips || []);
        },
      });

    // Sync URL query parameters -> Active Chips & Fleet state
    this.route.queryParams
      .pipe(observeOn(asapScheduler), takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        if (!params || !this.isCurrentRouteActive()) return;

        const fleetParam = (params['fleet'] || 'internal') as
          | 'internal'
          | 'ats';
        if (fleetParam !== this.fleet()) {
          this.fleet.set(fleetParam);
        }

        const fParams = getQueryParamAsArray(params['f']);
        const gbParam = params['gb'] || '';
        const gbKeys = gbParam.split(',').filter(Boolean);

        const incomingKey = buildUrlParamKey(fParams, gbKeys, fleetParam);
        if (incomingKey === this.lastSyncedUrlKey) {
          return;
        }
        this.lastSyncedUrlKey = incomingKey;

        if (fParams.length === 0 && gbKeys.length === 0) {
          this.restoreDefaultState(false);
          return;
        }

        const {parsedFilters, initialChips} = parseUrlChips(params);

        // Immediately set synchronous initial chips from URL query parameters so UI renders without latency/flicker
        this.activeChips.set(initialChips);

        // Asynchronously enhance chips with rich backend metadata
        this.resolveFiltersSubject.next({parsedFilters, gbKeys});
      });

    // React to top-level router navigation events (e.g. clicking the active nav menu item to return to landing page)
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        observeOn(asapScheduler),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        if (!this.isCurrentRouteActive()) return;
        if (this.isInternalUrlSync) return;
        const qp = this.route.snapshot?.queryParams;
        if (qp?.['f'] || qp?.['gb']) return;
        this.restoreIfSearchActive(false);
      });
  }

  // ===========================================================================
  // 8. Filter Chip Management & URL Synchronization
  // ===========================================================================

  /** Synchronizes active filter chips and fleet state to the browser URL. */
  syncUrl(
    chips: FilterChip[] = this.activeChips(),
    fleet: 'internal' | 'ats' = this.fleet(),
    replaceUrl = false,
  ) {
    const filters = chips
      .filter((c) => !c.isGroupBy)
      .map(serializeFilterChip)
      .filter(Boolean);
    const groupBys = chips
      .filter((c) => c.isGroupBy)
      .map(getChipKey)
      .filter(Boolean);

    this.lastSyncedUrlKey = getSerializedChipsKey(chips, fleet);

    const queryParams: Record<string, string | string[] | null> = {
      'f': filters.length > 0 ? filters : null,
      'gb': groupBys.length > 0 ? groupBys.join(',') : null,
      'fleet': fleet !== 'internal' ? fleet : null,
    };

    this.isInternalUrlSync = true;
    this.router
      .navigate([], {
        relativeTo: this.route,
        queryParams,
        queryParamsHandling: 'merge',
        replaceUrl,
      })
      .then(() => {
        setTimeout(() => {
          this.isInternalUrlSync = false;
        }, 0);
      });
  }

  /** Sets the active fleet operational scope and updates URL. */
  setFleet(fleet: 'internal' | 'ats') {
    if (this.fleet() === fleet) return;
    this.fleet.set(fleet);
    this.resetSearchState(false);
    this.syncUrl(this.getDefaultChips(), fleet);
  }

  /** Adds a new filter chip or updates an existing one with matching key and type. */
  addFilterChip(chip: FilterChip) {
    const isNegated = isChipNegated(chip);
    const newChip: FilterChip = {
      ...chip,
      pillCondition: chip.pillCondition || '',
      negated: isNegated,
    };

    const current = this.activeChips();
    const idx = current.findIndex((c) => isSameFilterChip(c, chip));

    const nextChips =
      idx !== -1
        ? current.map((c, i) => (i === idx ? newChip : c))
        : [...current, newChip];

    this.activeChips.set(nextChips);
    this.syncUrl(nextChips);
  }

  /** Removes a specific filter chip and closes the ValuePicker if the active chip is removed. */
  removeFilterChip(chip: FilterChip) {
    const nextChips = this.activeChips().filter(
      (c) => !isSameFilterChip(c, chip),
    );
    this.activeChips.set(nextChips);
    if (nextChips.length === 0 && !this.searchQuery().trim()) {
      this.browseAll.set(false);
    }

    if (this.autoCollapseAfterApply()) {
      this.showSuggestions.set(false);
    }

    // Automatically close the ValuePicker if all chips are removed or the chip being edited was removed.
    if (this.showValuePicker()) {
      const currentPickerKey = this.pickerConfig()?.key;
      if (
        nextChips.length === 0 ||
        !currentPickerKey ||
        isSameFilterChip(chip, {key: currentPickerKey, isGroupBy: false})
      ) {
        this.closeValuePicker();
      }
    }
    this.syncUrl(nextChips);
  }

  /** Removes all active filter chips while preserving any active group-by chips. */
  clearFilterChips() {
    const nextChips = this.groupByChips();
    this.activeChips.set(nextChips);
    if (nextChips.length === 0 && !this.searchQuery().trim()) {
      this.browseAll.set(false);
    }
    if (this.autoCollapseAfterApply()) {
      this.showSuggestions.set(false);
    }
    if (this.showValuePicker()) {
      this.closeValuePicker();
    }
    this.syncUrl(nextChips);
  }

  /** Removes all active group-by chips while preserving any active filter chips. */
  clearGroupByChips() {
    const nextChips = this.filterChips();
    this.activeChips.set(nextChips);
    if (nextChips.length === 0 && !this.searchQuery().trim()) {
      this.browseAll.set(false);
    }
    if (this.autoCollapseAfterApply()) {
      this.showSuggestions.set(false);
    }
    this.syncUrl(nextChips);
  }

  /** Toggles a group-by key (adds it if not present, removes it if currently active). */
  toggleGroupBy(gbKey: string, displayName?: string) {
    const currentGbKeys = this.groupByKeys();
    if (currentGbKeys.includes(gbKey)) {
      const existing = this.activeChips().find((c) =>
        isSameFilterChip(c, {key: gbKey, isGroupBy: true}),
      );
      if (existing) {
        this.removeFilterChip(existing);
      }
    } else {
      this.addFilterChip(createGroupByChip(gbKey, displayName));
    }
  }

  /** Removes any filter chip matching the specified key. */
  removeChipForKey(key: string) {
    const existing = this.activeChips().find((c) =>
      isSameFilterChip(c, {key, isGroupBy: false}),
    );
    if (existing) {
      this.removeFilterChip(existing);
    } else if (this.showValuePicker()) {
      const currentPickerKey = this.pickerConfig()?.key;
      if (
        currentPickerKey &&
        normalizeKey(key) === normalizeKey(currentPickerKey)
      ) {
        this.closeValuePicker();
      }
    }
  }

  // ===========================================================================
  // 9. Lifecycle & State Reset Methods
  // ===========================================================================

  /** Determines whether the ValuePicker is currently active for the specified chip. */
  isChipPickerActive(chip: FilterChip): boolean {
    if (!this.showValuePicker() || chip.isGroupBy) return false;
    const currentKey = this.pickerKey();
    if (!currentKey) return false;
    return isSameFilterChip(chip, {key: currentKey, isGroupBy: false});
  }

  /** Determines whether the ValuePicker is currently active for the specified key and optional title. */
  isKeyPickerActive(key: string, title?: string): boolean {
    if (!this.showValuePicker()) return false;
    const currentKey = normalizeKey(this.pickerKey());
    if (!currentKey) return false;
    const targetKey = normalizeKey(key);
    if (currentKey !== targetKey) return false;
    if (
      title &&
      this.pickerTitle() &&
      this.pickerTitle().toLowerCase() !== title.toLowerCase()
    ) {
      return false;
    }
    return true;
  }

  /** Function provided by SearchSuggestions to locate a popover chip's HTMLElement via Angular viewChildren. */
  readonly locatePopoverChip = signal<
    ((key: string, title?: string) => HTMLElement | null) | null
  >(null);

  /** Function provided by SearchSuggestions to locate the popover container element as a fallback anchor. */
  readonly getPopoverContainer = signal<(() => HTMLElement | null) | null>(null);

  /**
   * Opens the Quick Filter flow matching prototype UI design:
   * Opens the suggestions popover, creates a temporary/pending chip if the filter is not yet applied,
   * focuses the chip in the popover, and opens the ValuePicker anchored to that popover chip in requestAnimationFrame.
   */
  openQuickFilter(
    key: string,
    title?: string,
    metadata?: unknown,
    stagedValues?: string[],
  ) {
    this.closeValuePicker();
    const activeChip = this.activeChips().find(
      (c) => !c.isGroupBy && isSameFilterChip(c, {key, isGroupBy: false}),
    );
    if (!activeChip) {
      this.pendingFilter.set({
        key,
        displayName: title || key,
        metadata,
      });
    } else {
      this.pendingFilter.set(null);
    }
    this.focusChipKey.set(key);
    this.showSuggestions.set(true);

    const tryOpen = (retries = 10) => {
      requestAnimationFrame(() => {
        this.focusChipKey.set(key);
        const locator = this.locatePopoverChip();
        const chipEl = locator ? locator(key, title) : null;
        if (chipEl) {
          this.openValuePicker(
            key,
            chipEl,
            title,
            metadata,
            stagedValues,
            /* keepSuggestionsOpen= */ true,
          );
        } else if (retries > 0) {
          tryOpen(retries - 1);
        } else {
          // If the specific chip element is not yet found after animation frames,
          // anchor to the suggestions popover container to guarantee ValuePicker opens.
          const fallback = this.getPopoverContainer()?.() || null;
          this.openValuePicker(
            key,
            fallback,
            title,
            metadata,
            stagedValues,
            /* keepSuggestionsOpen= */ true,
          );
        }
      });
    };
    tryOpen();
  }

  /**
   * Opens the Quick Group-by flow matching prototype UI design:
   * Adds the group-by key if not yet applied.
   * Clicking an already-selected groupby always opens the suggestions popover to view/locate it;
   * applying a new groupby respects autoCollapseAfterApply preference.
   */
  openQuickGroupBy(key: string, displayName?: string) {
    const isAlreadyActive = this.groupByKeys().includes(key);
    if (!isAlreadyActive) {
      this.toggleGroupBy(key, displayName);
    }
    this.closeValuePicker();
    this.focusChipKey.set(key);

    if (isAlreadyActive) {
      this.showSuggestions.set(true);
    } else if (this.autoCollapseAfterApply()) {
      this.showSuggestions.set(false);
    } else {
      this.showSuggestions.set(true);
    }
  }

  /** Opens the ValuePicker popover for the specified key, configuring layout and initial state. */
  openValuePicker(
    key: string,
    anchor?: CdkOverlayOrigin | HTMLElement | null,
    title?: string,
    metadata?: unknown,
    stagedValues?: string[],
    keepSuggestionsOpen = false,
  ) {
    if (!keepSuggestionsOpen) {
      this.showSuggestions.set(false);
    }

    if (this.isKeyPickerActive(key, title)) {
      this.closeValuePicker();
      return;
    }

    if (anchor) {
      this.pickerAnchor.set(anchor);
    }

    const activeChip = this.activeChips().find((c) =>
      isSameFilterChip(c, {key, isGroupBy: false}),
    );

    const config = this.buildPickerConfig(key, title, metadata);
    const state = this.buildInitialPickerState(
      key,
      activeChip,
      metadata,
      stagedValues,
    );

    this.pickerConfig.set(config);
    this.pickerState.set(state);
    this.showValuePicker.set(true);
  }

  /** Closes the ValuePicker overlay and resets its configuration and temporary state. */
  closeValuePicker() {
    this.showValuePicker.set(false);
    this.pickerConfig.set(null);
    this.pickerState.set(INITIAL_VALUE_PICKER_STATE);
    this.pickerAnchor.set(null);
    this.pendingFilter.set(null);
    this.focusChipKey.set(null);
  }

  /** Resets transient UI state (query, overlays, suggestions). Override in subclasses to clear custom state. */
  protected resetTransientUiState() {
    this.searchQuery.set('');
    this.browseAll.set(false);
    this.showSuggestions.set(false);
    this.closeValuePicker();
  }

  /** Resets the entire search state (clears query, clears all filter chips, closes overlays). */
  resetSearchState(updateUrl = true) {
    this.applySearchState([], updateUrl);
  }

  /** Restores local search state back to default configuration (including default chips). */
  restoreDefaultState(updateUrl = false) {
    this.applySearchState(this.getDefaultChips(), updateUrl);
  }

  /** Resets UI overlays and applies the specified active chips and URL synchronization. */
  protected applySearchState(chips: FilterChip[], updateUrl: boolean) {
    this.resetTransientUiState();
    this.activeChips.set(chips);
    if (updateUrl) {
      this.syncUrl(chips);
    }
  }

  /** Returns true if the store is currently in its clean default/empty state. */
  private isDefaultState(): boolean {
    if (this.browseAll() || this.searchQuery().trim().length > 0) {
      return false;
    }
    if (this.showValuePicker() || this.showSuggestions()) {
      return false;
    }

    const currentKey = getSerializedChipsKey(this.activeChips(), this.fleet());
    const defaultKey = getSerializedChipsKey(
      this.getDefaultChips(),
      this.fleet(),
    );
    return currentKey === defaultKey;
  }

  /** Restores local search state back to default if currently in a non-default search state. */
  private restoreIfSearchActive(updateUrl = false) {
    if (!this.isDefaultState()) {
      this.restoreDefaultState(updateUrl);
    }
  }
}
