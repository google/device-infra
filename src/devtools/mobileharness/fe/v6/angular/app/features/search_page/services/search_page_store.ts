import {CdkOverlayOrigin} from '@angular/cdk/overlay';
import {
  computed,
  DestroyRef,
  inject,
  Injectable,
  linkedSignal,
  Signal,
  signal,
} from '@angular/core';
import {takeUntilDestroyed, toObservable, toSignal} from '@angular/core/rxjs-interop';
import {ActivatedRoute, NavigationEnd, Router} from '@angular/router';
import {Observable, of} from 'rxjs';
import {catchError, debounceTime, distinctUntilChanged, filter, map} from 'rxjs/operators';

import {Column, Row} from '../../../core/models/search';
import {SEARCH_SERVICE} from '../../../core/services/search/search_service';
import {
  EntityType,
  FilterChip,
  FilterKeyMetadata,
  INITIAL_VALUE_PICKER_STATE,
  ParsedQueryFilter,
  PickerValueItem,
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
  getQueryParamAsArray,
  isSameFilterChip,
  normalizeKey,
  parseQueryFilterParam,
  serializeFilterChip,
} from '../utils';

/** Resolves the active EntityType from current router URL or ActivatedRoute hierarchy. */
function resolveInitialEntity(route: ActivatedRoute, router: Router): EntityType {
  const locationUrl =
    typeof window !== 'undefined' && window.location
      ? window.location.pathname || window.location.href
      : '';
  const url = router.url || locationUrl || '';
  if (url.includes('/hosts')) return 'hosts';
  if (url.includes('/devices')) return 'devices';
  if (url.includes('/tests')) return 'tests';
  if (url.includes('/jobs')) return 'jobs';
  if (url.includes('/sessions')) return 'sessions';

  if (route.snapshot) {
    for (const r of route.snapshot.pathFromRoot) {
      if (r.data?.['entity']) {
        return r.data['entity'] as EntityType;
      }
    }
  }
  if (route.snapshot?.data?.['entity']) {
    return route.snapshot.data['entity'] as EntityType;
  }

  return 'devices';
}
function resolveInitialChips(route: ActivatedRoute): FilterChip[] {
  const params = route.snapshot?.queryParams;
  if (!params) return [];

  const fParams = getQueryParamAsArray(params['f']);
  const gbParam = params['gb'] || '';
  const gbKeys = gbParam.split(',').filter(Boolean);

  if (fParams.length === 0 && gbKeys.length === 0) {
    return [];
  }

  const parsedFilters = fParams
    .map(parseQueryFilterParam)
    .filter(Boolean) as ParsedQueryFilter[];

  return [
    ...parsedFilters.map((pf) => ({
      key: pf.key,
      pillKey: pf.key,
      pillCondition: pf.fallbackPillCondition,
      rawValues: pf.rawValues,
      negated: pf.negated,
      complex: pf.complex,
    })),
    ...gbKeys.map((gb: string) => ({
      key: 'group_by_' + gb,
      pillKey: gb,
      pillCondition: gb,
      isGroupBy: true,
    })),
  ];
}

/** Resolves initial fleet operational scope from ActivatedRoute snapshot query params. */
function resolveInitialFleet(route: ActivatedRoute): 'internal' | 'ats' {
  const fleetParam = route.snapshot?.queryParams?.['fleet'];
  return fleetParam === 'ats' ? 'ats' : 'internal';
}

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

  /** Current active search entity type (e.g. 'devices', 'hosts', 'tests', 'jobs', 'sessions'). */
  readonly entity = signal<EntityType>(
    resolveInitialEntity(this.route, this.router),
  );

  /** Fleet operational scope ('internal' vs 'ats'). */
  readonly fleet = signal<'internal' | 'ats'>(
    resolveInitialFleet(this.route),
  );

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
  readonly activeChips = signal<FilterChip[]>(
    resolveInitialChips(this.route),
  );

  /** Indicates whether the user is browsing all records with no filters (Fleet only). */
  readonly browseAll = signal<boolean>(false);

  /** Controls visibility of the search box suggestions popover dropdown. */
  readonly showSuggestions = signal<boolean>(false);

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

  /** Indicates whether enum options are being asynchronously loaded for the ValuePicker. */
  readonly isPickerLoading: Signal<boolean> = signal(false);

  /** Asynchronously fetched enum/value options for the ValuePicker. */
  readonly asyncPickerValues: Signal<PickerValueItem[]> = signal([]);

  /** Asynchronously fetched value list format ('counted' | 'plain') for the ValuePicker. */
  readonly asyncPickerValuesType: Signal<'counted' | 'plain' | undefined> = signal(undefined);

  /** Effective merged ValuePicker state combining user inputs and async fetched values/loading. */
  readonly effectivePickerState = computed<ValuePickerState>(() => {
    const raw = this.pickerState();
    const asyncLoading = this.isPickerLoading();
    const asyncVals = this.asyncPickerValues();
    const asyncValuesType = this.asyncPickerValuesType();
    return {
      ...raw,
      loading: raw.loading || asyncLoading,
      values: asyncVals.length > 0 ? asyncVals : raw.values,
      valuesType: asyncValuesType || raw.valuesType,
    };
  });

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

  /** Whether the clear button ('X') in the search bar should be displayed. */
  readonly showSearchClear = computed(
    () => this.searchQuery().trim().length > 0 || this.activeChips().length > 0,
  );

  /** Whether the search view is currently in its initial landing / empty guide state. */
  readonly isLandingState = computed<boolean>(() => {
    if (this.isTjs()) return false;
    return this.activeChips().length === 0 && !this.browseAll();
  });

  /** List of active group-by field keys extracted from `activeChips`. */
  readonly groupByKeys = computed<string[]>(() =>
    this.activeChips()
      .filter((c) => c.isGroupBy)
      .map((c) => (c.key ? c.key.replace(/^group_by_/, '') : c.pillKey)),
  );

  /** Serialized active filter parameter strings formatted for URL query. */
  readonly serializedActiveFilters = computed<string[]>(() =>
    this.activeChips()
      .filter((c) => !c.isGroupBy)
      .map(serializeFilterChip)
      .filter(Boolean),
  );

  /** Serialized active group-by key strings formatted for URL query. */
  readonly serializedActiveGroupBys = computed<string[]>(() =>
    this.activeChips()
      .filter((c) => c.isGroupBy)
      .map((c) => c.key?.replace(/^group_by_/, '') || c.pillKey)
      .filter(Boolean),
  );

  /** Canonical URL query parameter key computed from current active state. */
  readonly currentUrlParamKey = computed<string>(() =>
    buildUrlParamKey(
      this.serializedActiveFilters(),
      this.serializedActiveGroupBys(),
      this.fleet(),
    ),
  );

  // ===========================================================================
  // 6. Abstract Domain Interface (Template Method Hooks)
  // ===========================================================================

  /** Domain-specific autocomplete and recommendation suggestions for the search box. */
  abstract readonly suggestions: Signal<SearchBoxSuggestion[]>;

  /** Loading state for primary search result data fetching. */
  abstract readonly isLoading: Signal<boolean>;

  /** Initial page configuration including metadata, default chips, and column definitions. */
  abstract readonly searchConfig: Signal<SearchPageConfig | null>;

  /** Whether initial search configuration is loading. */
  readonly isConfigLoading: Signal<boolean> = signal(false);

  /** Promoted / recommended filter keys shown as quick-action presets. */
  abstract readonly promotedFilterKeys: Signal<PromotedFilterKeyItem[]>;

  /** Promoted / recommended group-by keys shown as quick-action presets. */
  readonly promotedGroupByKeys: Signal<PromotedGroupByKeyItem[]> = signal([]);

  /** Options list for sorting group header cards in grouped mode. */
  readonly groupSortOptions: Signal<Array<{value: string; label: string}>> = signal([]);

  /** Whether auxiliary promoted keys are currently loading. */
  readonly isPromotedKeysLoading: Signal<boolean> = signal(false);

  /** Map of filter key metadata for display names, types, and capability flags. */
  abstract readonly keyMetadataMap: Signal<Map<string, FilterKeyMetadata>>;

  /** Visible column definitions for the search results table. */
  abstract readonly displayColumns: Signal<Column[]>;

  /** Result rows for the current page of search results. */
  abstract readonly rows: Signal<Row[]>;

  /** Whether a subsequent page of search results is available. */
  abstract readonly hasNextPage: Signal<boolean>;

  /** Triggers execution of the primary domain search query. */
  abstract executeSearch(): void;

  /** Applies selections from the ValuePicker to create or update filter chips. */
  abstract applyValuePicker(event: ValuePickerApplyEvent): void;

  /** Navigates to the previous page of search results. */
  abstract prevPage(): void;

  /** Navigates to the next page of search results. */
  abstract nextPage(): void;

  /** Handles user selection of an autocomplete suggestion from the search box popover. */
  abstract selectSuggestion(item: SearchBoxSuggestion): void;

  /** Subclass hook to resolve chips from domain-specific backend RPC. */
  protected abstract resolveChipsFromBackend(
    parsedFilters: ParsedQueryFilter[],
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
  ): ValuePickerState;

  /** Subclass hook to provide default filter chips on initial state / reset. Defaults to empty array. */
  getDefaultChips(): FilterChip[] {
    return [];
  }

  // ===========================================================================
  // 7. URL Serialization & Deserialization Methods
  // ===========================================================================

  /** Resolves parsed URL query filters and group-by keys into FilterChip list. */
  resolveUrlFilters(
    parsedFilters: ParsedQueryFilter[],
    groupByKeys: string[] = [],
  ): Observable<FilterChip[]> {
    if (parsedFilters.length === 0 && groupByKeys.length === 0) {
      return of([]);
    }

    return this.resolveChipsFromBackend(parsedFilters, groupByKeys).pipe(
      map((resolvedChips) => {
        if (resolvedChips.length > 0) {
          return resolvedChips;
        }
        return this.buildFallbackChips(parsedFilters, groupByKeys);
      }),
      catchError(() => of(this.buildFallbackChips(parsedFilters, groupByKeys))),
    );
  }

  /** Constructs a fallback FilterChip from a parsed URL query filter using local metadata. */
  buildFallbackChip(pf: ParsedQueryFilter): FilterChip {
    const meta = this.keyMetadataMap().get(pf.key);
    return {
      key: pf.key,
      pillKey: meta?.keyDisplayName || pf.key,
      pillCondition: pf.fallbackPillCondition,
      rawValues: pf.rawValues,
      metadata: meta,
      negated: pf.negated,
      complex: pf.complex,
    };
  }

  /** Batch constructs fallback FilterChips for parsed query filters and group-by keys. */
  buildFallbackChips(
    parsedFilters: ParsedQueryFilter[],
    groupByKeys: string[] = [],
  ): FilterChip[] {
    return [
      ...parsedFilters.map((pf) => this.buildFallbackChip(pf)),
      ...groupByKeys.map((gb) => this.buildGroupByChip(gb)),
    ];
  }

  /** Constructs a specialized group-by FilterChip. */
  buildGroupByChip(gbKey: string, displayName?: string): FilterChip {
    const meta = this.keyMetadataMap().get(gbKey);
    const display = displayName || meta?.keyDisplayName || gbKey;
    return {
      key: 'group_by_' + gbKey,
      pillKey: display,
      pillCondition: display,
      isGroupBy: true,
    };
  }

  /** Tracks the last URL query parameter key that was successfully synced or processed. */
  private lastSyncedUrlKey: string | null = null;

  constructor() {
    // 0. Sync entity type immediately on NavigationEnd to prevent stale route entity loading
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        const targetEntity = resolveInitialEntity(this.route, this.router);
        if (targetEntity !== this.entity()) {
          this.entity.set(targetEntity);
          this.resetSearchState(false);
        }
      });

    // 1. Sync entity type when route data changes
    this.route.data
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((data) => {
        const newEntity = data?.['entity'] as EntityType | undefined;
        if (newEntity && newEntity !== this.entity()) {
          this.entity.set(newEntity);
          this.resetSearchState(false);
        }
      });

    // 2. Initial resolution of URL query filters on page load / refresh
    const initialSnapshotParams = this.route.snapshot?.queryParams;
    if (initialSnapshotParams) {
      const fParams = getQueryParamAsArray(initialSnapshotParams['f']);
      const gbParam = initialSnapshotParams['gb'] || '';
      const gbKeys = gbParam.split(',').filter(Boolean);
      const fleetParam = (initialSnapshotParams['fleet'] || 'internal') as
        | 'internal'
        | 'ats';

      if (fParams.length > 0 || gbKeys.length > 0) {
        const parsedFilters = fParams
          .map(parseQueryFilterParam)
          .filter(Boolean) as ParsedQueryFilter[];

        const initialKey = buildUrlParamKey(fParams, gbKeys, fleetParam);
        this.lastSyncedUrlKey = initialKey;

        this.resolveUrlFilters(parsedFilters, gbKeys)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: (updatedChips) => {
              if (updatedChips && updatedChips.length > 0) {
                this.activeChips.set(updatedChips);
              }
            },
          });
      }
    }

    // 3. Sync URL query parameters -> Active Chips & Fleet state
    this.route.queryParams
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        if (!params) return;

        const targetEntity =
          (this.route.snapshot?.data?.['entity'] as EntityType | undefined) ||
          resolveInitialEntity(this.route, this.router);
        if (targetEntity && targetEntity !== this.entity()) {
          this.entity.set(targetEntity);
        }

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
          if (
            this.browseAll() ||
            this.activeChips().length > 0 ||
            this.searchQuery().trim()
          ) {
            this.resetSearchState(false);
          }
          return;
        }

        const parsedFilters = fParams
          .map(parseQueryFilterParam)
          .filter(Boolean) as ParsedQueryFilter[];

        // Immediately set synchronous fallback chips so UI is in sync without any latency/flicker
        const fallbackChips = this.buildFallbackChips(parsedFilters, gbKeys);
        this.activeChips.set(fallbackChips);

        // Asynchronously enhance chips with rich backend metadata
        this.resolveUrlFilters(parsedFilters, gbKeys)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: (updatedChips) => {
              if (updatedChips && updatedChips.length > 0) {
                this.activeChips.set(updatedChips);
              }
            },
          });
      });

    // 3. Reset to clean initial state when Navigation Menu is clicked (same-URL or clean navigation without filter params)
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((event) => {
        const activeEntity = resolveInitialEntity(this.route, this.router);
        if (activeEntity !== this.entity()) {
          return;
        }

        const tree = this.router.parseUrl(event.urlAfterRedirects || event.url);
        const f = tree.queryParams['f'];
        const gb = tree.queryParams['gb'];
        const fleetParam = (tree.queryParams['fleet'] || 'internal') as
          | 'internal'
          | 'ats';

        if (!f && !gb) {
          if (
            this.browseAll() ||
            this.activeChips().length > 0 ||
            this.searchQuery().trim()
          ) {
            this.resetSearchState(false);
          }
          if (this.fleet() !== fleetParam) {
            this.fleet.set(fleetParam);
          }
        }
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
      .map((c) => c.key?.replace(/^group_by_/, '') || c.pillKey)
      .filter(Boolean);

    this.lastSyncedUrlKey = buildUrlParamKey(filters, groupBys, fleet);

    const queryParams: Record<string, string | string[] | null> = {
      'f': filters.length > 0 ? filters : null,
      'gb': groupBys.length > 0 ? groupBys.join(',') : null,
      'fleet': fleet !== 'internal' ? fleet : null,
    };

    this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
      replaceUrl,
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
    const newChip: FilterChip = {
      ...chip,
      pillCondition: chip.pillCondition || '',
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

    // Automatically close the ValuePicker if all chips are removed or the chip being edited was removed.
    if (this.showValuePicker()) {
      const currentPickerKey = this.pickerConfig()?.key;
      if (
        nextChips.length === 0 ||
        !currentPickerKey ||
        isSameFilterChip(chip, {
          key: currentPickerKey,
          pillKey: currentPickerKey,
          pillCondition: '',
        })
      ) {
        this.closeValuePicker();
      }
    }
    this.syncUrl(nextChips);
  }

  /** Toggles a group-by key (adds it if not present, removes it if currently active). */
  toggleGroupBy(gbKey: string, displayName?: string) {
    const currentGbKeys = this.groupByKeys();
    if (currentGbKeys.includes(gbKey)) {
      const existing = this.activeChips().find(
        (c) => c.isGroupBy && (c.key === 'group_by_' + gbKey || c.pillKey === gbKey),
      );
      if (existing) {
        this.removeFilterChip(existing);
      }
    } else {
      this.addFilterChip(this.buildGroupByChip(gbKey, displayName));
    }
  }

  /** Removes any filter chip matching the specified key. */
  removeChipForKey(key: string) {
    const target: FilterChip = {
      key,
      pillKey: key,
      pillCondition: '',
      isGroupBy: false,
    };
    const existing = this.activeChips().find((c) =>
      isSameFilterChip(c, target),
    );
    if (existing) {
      this.removeFilterChip(existing);
    } else if (this.showValuePicker()) {
      const currentPickerKey = this.pickerConfig()?.key;
      if (
        currentPickerKey &&
        key.toLowerCase() === currentPickerKey.toLowerCase()
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
    const currentKey = normalizeKey(this.pickerKey());
    if (!currentKey) return false;
    const targetKey = normalizeKey(chip.key || chip.pillKey);
    return currentKey === targetKey;
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

  /** Opens the ValuePicker popover for the specified key, configuring layout and initial state. */
  openValuePicker(
    key: string,
    anchor?: CdkOverlayOrigin | HTMLElement | null,
    title?: string,
    metadata?: unknown,
  ) {
    if (anchor) {
      this.pickerAnchor.set(anchor);
    }
    const cleanKey = normalizeKey(key);
    const currentKey = normalizeKey(this.pickerConfig()?.key);
    const currentTitle = this.pickerConfig()?.title;

    if (
      this.showValuePicker() &&
      currentKey === cleanKey &&
      (!title || !currentTitle || currentTitle.toLowerCase() === title.toLowerCase())
    ) {
      this.closeValuePicker();
      return;
    }

    if (this.showValuePicker()) {
      this.closeValuePicker();
      if (anchor) {
        this.pickerAnchor.set(anchor);
      }
    }

    const activeChip = this.activeChips().find(
      (c) =>
        !c.isGroupBy &&
        ((c.key && c.key.toLowerCase() === key.toLowerCase()) ||
          c.pillKey.toLowerCase() === cleanKey),
    );

    const config = this.buildPickerConfig(key, title, metadata);
    const state = this.buildInitialPickerState(key, activeChip, metadata);

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
  }

  /** Resets the entire search state back to default (clears query, restores default chips, closes overlays). */
  resetSearchState(updateUrl = true) {
    this.searchQuery.set('');

    const defaultChips = this.getDefaultChips();
    this.activeChips.set(defaultChips);
    this.browseAll.set(false);
    this.showSuggestions.set(false);
    this.closeValuePicker();
    if (updateUrl) {
      this.syncUrl(defaultChips);
    }
  }
}
