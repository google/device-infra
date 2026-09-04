import {computed, inject, Injectable, linkedSignal} from '@angular/core';
import {
  rxResource,
  takeUntilDestroyed,
  toObservable,
} from '@angular/core/rxjs-interop';
import {Observable, of} from 'rxjs';
import {catchError, filter, map, switchMap, take} from 'rxjs/operators';
import {
  Column,
  Row,
  TjsFilter,
  TjsPromotedKey,
  TjsResolveChipsRequest,
  TjsSearchConfig,
  TjsSearchRequest,
  TjsSearchResponse,
  TjsSuggestion,
  TjsSuggestionRequest,
} from '../../../core/models/search';
import {SnackBarService} from '../../../shared/services/snackbar_service';
import {
  EntityType,
  FilterChip,
  FilterKeyMetadata,
  FilterPair,
  PickerValueItem,
  PromotedFilterKeyItem,
  SearchBoxSuggestion,
  ValuePickerApplyEvent,
  ValuePickerConfig,
  ValuePickerState,
} from '../models';
import {
  buildResolvedFilterChips,
  extractFilterChipFromTjsSuggestion,
  extractRawValuesFromTjsFilter,
  getChipKey,
  mapToSearchBoxSuggestion,
  pdtDateTimeToUtcIso,
  resolveInitialChips,
  toTjsEntityProto,
} from '../utils';
import {SearchPageStore} from './search_page_store';

/**
 * State store for Test / Job / Session (TJS) search pages.
 *
 * Extends base SearchPageStore with TJS-specific RPC communications, token-stack
 * pagination, and single-level filter handling.
 */
@Injectable()
export class TjsSearchStore extends SearchPageStore {
  private readonly snackBarService = inject(SnackBarService);
  // ===========================================================================
  // 1. Dynamic UI Prompts & Suggestion Signals
  // ===========================================================================

  private static readonly SUPPORTED_ENTITIES = new Set<EntityType>([
    'tests',
    'jobs',
    'sessions',
  ]);

  /** Subclass hook providing default entity type when route metadata is absent. */
  override getDefaultEntity(): EntityType {
    return 'tests';
  }

  override getSupportedEntities(): Set<EntityType> {
    return TjsSearchStore.SUPPORTED_ENTITIES;
  }

  /** Returns dynamic search placeholder text based on the active search entity. */
  override readonly searchPlaceholder = computed(() => {
    switch (this.entity()) {
      case 'tests':
        return 'Paste an ID, or type a user or a test name';
      case 'jobs':
        return 'Paste a job ID, or type a user or a job name';
      case 'sessions':
        return 'Paste a session ID, or type a user';
      default:
        return 'Type search query…';
    }
  });

  /** Reactive resource fetching auto-complete suggestions with debounce. */
  private readonly suggestionsResource = rxResource<
    TjsSuggestion[],
    | {
        entity: EntityType;
        input: string;
        filters: TjsFilter[];
      }
    | undefined
  >({
    params: () => {
      if (!this.isCurrentRouteActive()) return undefined;
      const show = this.showSuggestions();
      if (!show) return undefined;

      return {
        entity: this.entity(),
        input: this.debouncedSearchQuery(),
        filters: this.effectiveFilters(),
      };
    },
    stream: ({params: req}) => {
      if (!req) return of([]);
      const tjsReq: TjsSuggestionRequest = {
        entity: toTjsEntityProto(req.entity),
        input: req.input,
        filters: req.filters.length > 0 ? req.filters : undefined,
        limit: 10,
      };
      return this.searchService.getTjsSuggestions(tjsReq).pipe(
        map((res) => res.items || []),
        catchError(() => of([])),
      );
    },
  });

  /** Mapped search suggestions for SearchBox popover consumption. */
  override readonly suggestions = computed<SearchBoxSuggestion[]>(() => {
    return (this.suggestionsResource.value() || []).map(
      mapToSearchBoxSuggestion,
    );
  });

  // ===========================================================================
  // 2. Page Configuration & Metadata Resources
  // ===========================================================================

  /** Reactive resource fetching TJS search configuration (default chips, promoted keys). */
  private readonly searchConfigResource = rxResource<
    TjsSearchConfig | null,
    EntityType | undefined
  >({
    params: () => {
      if (!this.isCurrentRouteActive()) return undefined;
      return this.entity();
    },
    stream: ({params: entity}) => {
      if (!entity) return of(null);
      return this.searchService
        .getTjsSearchConfig({entity: toTjsEntityProto(entity)})
        .pipe(catchError(() => of(null)));
    },
  });

  /** Exposes current TJS search configuration. */
  override readonly searchConfig = computed<TjsSearchConfig | null>(
    () => this.searchConfigResource.value() || null,
  );

  /** Observable stream of searchConfig for reactive waiting during cold-load chip resolution. */
  private readonly searchConfig$ = toObservable(this.searchConfig);

  /** Formatted entity display label provided by BFF search config (e.g. "Tests", "Jobs", "Sessions"). */
  readonly entityLabel = computed<string>(() => {
    return this.searchConfig()?.entityLabel || this.entity();
  });

  /** Whether initial search configuration is loading. */
  override readonly isConfigLoading = computed<boolean>(() =>
    this.searchConfigResource.isLoading(),
  );

  /** Whether auxiliary promoted keys are currently loading. */
  override readonly isPromotedKeysLoading = computed<boolean>(() =>
    this.searchConfigResource.isLoading(),
  );

  /** Tracks whether the initial route entry contained filter query parameters. */
  private readonly hasInitialUrlFilters = !!(
    this.route.snapshot?.queryParams?.['f'] ||
    this.route.snapshot?.queryParams?.['gb']
  );

  /**
   * Synchronizes active filter chips with search configuration default chips
   * using Angular 20+ linkedSignal.
   *
   * On initial route entry with filters, retains URL filters.
   * On clean route entry, populates default chips once searchConfig resolves.
   * Subsequent user modifications are preserved.
   */
  override readonly activeChips = linkedSignal<
    TjsSearchConfig | null,
    FilterChip[]
  >({
    source: () => this.searchConfig(),
    computation: (cfg, previous) => {
      if (!previous) {
        return resolveInitialChips(this.route);
      }
      if (!this.hasInitialUrlFilters && cfg && previous.value.length === 0) {
        const defaults = this.mapDefaultChips(cfg);
        if (defaults.length > 0) return defaults;
      }
      return previous.value;
    },
  });

  /** Returns default chips from active search configuration if available. */
  override getDefaultChips(): FilterChip[] {
    return this.mapDefaultChips(this.searchConfig());
  }

  /** Helper to map TjsSearchConfig default chips to FilterChip array. */
  private mapDefaultChips(cfg: TjsSearchConfig | null): FilterChip[] {
    if (cfg?.defaultChips && cfg.defaultChips.length > 0) {
      return cfg.defaultChips.map((c) => {
        const rawVals = extractRawValuesFromTjsFilter(c.filter);
        const key = c.filter?.key || c.pillKey;
        const meta = this.getKeyMetadata(key);
        return {
          key,
          pillKey: c.pillKey || c.keyDisplayName || meta?.keyDisplayName || key,
          pillCondition: c.pillCondition || c.filter?.stringValue?.value || '',
          rawValues: rawVals,
          metadata: meta,
          tjsFilter: c.filter,
        };
      });
    }
    return [];
  }

  /** Promoted filter key preset items derived directly from active TJS search config. */
  override readonly promotedFilterKeys = computed<PromotedFilterKeyItem[]>(
    () => {
      const cfg = this.searchConfig();
      if (!cfg?.promotedKeys) return [];
      return cfg.promotedKeys.map((pk: TjsPromotedKey) => ({
        key: pk.key,
        metadata: {
          ...pk,
          key: pk.key,
          keyDisplayName: pk.displayName || pk.key,
        },
      }));
    },
  );

  /** Dynamic dictionary map containing metadata for all known filter keys. */
  override readonly keyMetadataMap = computed<Map<string, FilterKeyMetadata>>(
    () => {
      const nextMap = new Map<string, FilterKeyMetadata>();
      const cfg = this.searchConfig();
      if (cfg?.promotedKeys) {
        for (const pk of cfg.promotedKeys) {
          nextMap.set(pk.key, {
            ...pk,
            key: pk.key,
            keyDisplayName: pk.displayName || pk.key,
          });
        }
      }
      return nextMap;
    },
  );

  // ===========================================================================
  // 3. Primary Search Resource & Execution
  // ===========================================================================

  /** Reactive resource fetching main TJS search results data. */
  private readonly tjsResource = rxResource<
    TjsSearchResponse,
    | {
        entity: EntityType;
        pageToken: string;
        filters: TjsFilter[];
      }
    | undefined
  >({
    params: () => {
      if (!this.isCurrentRouteActive()) return undefined;

      // On clean page entry, wait for searchConfig to load and populate default chips
      if (!this.hasInitialUrlFilters && !this.searchConfig?.()) {
        return undefined;
      }

      return {
        entity: this.entity(),
        pageToken: this.pageToken(),
        filters: this.effectiveFilters(),
      };
    },
    stream: ({params: req}) => {
      if (!req) return of({columns: [], rows: []} as TjsSearchResponse);

      const tjsReq: TjsSearchRequest = {
        entity: toTjsEntityProto(req.entity),
        filters: req.filters.length > 0 ? req.filters : undefined,
        pageToken: req.pageToken || undefined,
      };

      return this.searchService.searchTjs(tjsReq).pipe(
        catchError(() =>
          of({
            columns: [],
            rows: [],
          } as TjsSearchResponse),
        ),
      );
    },
  });

  /** Exposes loading status of primary TJS search resource. */
  override readonly isLoading = computed(() => this.tjsResource.isLoading());

  /** Columns to render in TJS search results table. */
  override readonly displayColumns = computed<Column[]>(() => {
    return this.tjsResource.value()?.columns || [];
  });

  /** Rows to render in TJS search results table. */
  override readonly rows = computed<Row[]>(() => {
    return (this.tjsResource.value()?.rows as Row[]) || [];
  });

  /** Determines whether a next page token exists in TJS search response. */
  override readonly hasNextPage = computed<boolean>(() => {
    return !!this.tjsResource.value()?.nextPageToken;
  });

  /** Pre-formatted semantic range text for TJS cursor pagination display (e.g. "showing 1–25"). */
  override readonly rangeText = computed<string>(() => {
    const count = this.rows().length;
    if (count === 0) return '';
    const start = this.pageIndex() * this.pageSize() + 1;
    const end = this.pageIndex() * this.pageSize() + count;
    return `showing ${start.toLocaleString()}–${end.toLocaleString()}`;
  });

  /** Triggers execution of primary TJS search and forces resource reload. */
  override executeSearch() {
    this.pageIndex.set(0);
    this.pageToken.set('');
    this.prevPageTokens.set([]);
    this.tjsResource.reload();
  }

  /** Memoized effective filter list guarding downstream search resources from display-only chip updates. */
  readonly effectiveFilters = computed<TjsFilter[]>(
    () =>
      this.activeChips()
        .map((chip) => this.buildTjsFilterFromChip(chip))
        .filter(Boolean) as TjsFilter[],
    {
      equal: (a, b) => {
        if (a === b) return true;
        if (a.length !== b.length) return false;
        return JSON.stringify(a) === JSON.stringify(b);
      },
    },
  );

  /** Maps FilterChip to TjsFilter Protobuf payload. */
  private buildTjsFilterFromChip(chip: Partial<FilterChip>): TjsFilter | null {
    if (chip.tjsFilter) {
      return chip.tjsFilter;
    }
    const rawKey = chip.key || chip.pillKey;
    if (!rawKey) return null;

    if (this.searchConfig() && !this.getKeyMetadata(rawKey)) {
      return null;
    }

    const meta =
      chip.metadata ||
      this.getKeyMetadata(chip.key) ||
      this.getKeyMetadata(chip.pillKey);

    if (meta?.timeRange) {
      const fromStr = chip.rawValues?.[0] || '';
      const toStr = chip.rawValues?.[1] || '';
      const isoFrom = this.toIsoString(fromStr);
      const isoTo = this.toIsoString(toStr);

      if (!isoFrom && !isoTo) {
        return null;
      }

      return {
        key: rawKey,
        timeRange: {
          from: isoFrom,
          to: isoTo,
        },
      };
    }

    if (meta?.namedPair) {
      const name = chip.rawValues?.[0] || '';
      const value = chip.rawValues?.[1] || '';
      if (!name && !value) return null;
      return {
        key: rawKey,
        namedValue: {
          name,
          value,
        },
      };
    }

    if (meta?.enumPicker) {
      const vals =
        chip.rawValues && chip.rawValues.length > 0 ? chip.rawValues : [];
      if (vals.length === 0) return null;
      return {
        key: rawKey,
        enumValues: {
          values: vals,
        },
      };
    }

    if (chip.rawValues && chip.rawValues.length > 0) {
      const validVals = chip.rawValues.filter(Boolean);
      if (validVals.length === 0) return null;
      return {
        key: rawKey,
        stringValue: {
          value: validVals.join(','),
        },
      };
    }

    if (chip.pillCondition) {
      return {
        key: rawKey,
        stringValue: {
          value: chip.pillCondition,
        },
      };
    }

    return null;
  }

  /** Converts ISO or PDT timestamp string to UTC ISO format string. */
  private toIsoString(val: string): string | undefined {
    if (!val) return undefined;
    return pdtDateTimeToUtcIso(val) || undefined;
  }

  // ===========================================================================
  // 4. Pagination Stack & History Controls
  // ===========================================================================

  /** History stack of page tokens used for backward pagination, automatically reset when chips or query change. */
  readonly prevPageTokens = linkedSignal<
    {chips: FilterChip[]; query: string},
    string[]
  >({
    source: () => ({chips: this.activeChips(), query: this.searchQuery()}),
    computation: () => [],
  });

  /** Navigates to previous page token by popping from token history stack. */
  override prevPage() {
    const tokens = this.prevPageTokens();
    if (tokens.length === 0) return;
    const prev = tokens[tokens.length - 1];
    this.prevPageTokens.set(tokens.slice(0, tokens.length - 1));
    this.pageToken.set(prev);
    this.pageIndex.update((i) => Math.max(0, i - 1));
  }

  /** Navigates to next page token and records current token onto pagination stack. */
  override nextPage() {
    const nextToken = this.tjsResource.value()?.nextPageToken;
    if (!nextToken) return;
    this.prevPageTokens.update((tokens) => [...tokens, this.pageToken()]);
    this.pageToken.set(nextToken);
    this.pageIndex.update((i) => i + 1);
  }

  // ===========================================================================
  // 5. URL Resolution & Backend Metadata Enrichment
  // ===========================================================================

  /** Resolves parsed URL query filters into FilterChip list using TJS resolve API. */
  protected override resolveChipsFromBackend(
    parsedFilters: FilterChip[],
    _groupByKeys: string[],
  ): Observable<FilterChip[]> {
    if (parsedFilters.length === 0) {
      return of([]);
    }

    if (!this.searchConfig()) {
      return this.searchConfig$.pipe(
        filter((cfg): cfg is TjsSearchConfig => cfg !== null),
        take(1),
        switchMap(() => this.executeTjsResolve(parsedFilters)),
      );
    }

    return this.executeTjsResolve(parsedFilters);
  }

  /** Executes TJS chip resolution after search configuration and promotedKeys are available. */
  private executeTjsResolve(
    parsedFilters: FilterChip[],
  ): Observable<FilterChip[]> {
    // Scenario 1 (TJS): Since promotedKeys are known in config, ignore unknown keys directly and notify user
    const filterPairs: Array<FilterPair<TjsFilter>> = [];
    for (const chip of parsedFilters) {
      const key = getChipKey(chip);
      if (!key || !this.getKeyMetadata(key)) {
        const label = chip.pillKey || chip.key || key || 'Unknown';
        this.snackBarService.showWarning(
          `Filter "${label}" is not supported for ${this.entity()} and has been ignored.`,
        );
        continue;
      }
      const filter = chip.tjsFilter || this.buildTjsFilterFromChip(chip);
      if (filter) {
        filterPairs.push({chip, filter});
      }
    }

    // If no matching filters, return empty array to clear invalid chips (Scenario 1 & 5)
    if (filterPairs.length === 0) {
      return of([]);
    }

    const req: TjsResolveChipsRequest = {
      filters: filterPairs.map((p) => p.filter),
    };

    return this.searchService.resolveTjsChips(req).pipe(
      map((res) =>
        buildResolvedFilterChips(
          filterPairs,
          res.chips,
          (chip, resolved, filter) => {
            const meta = chip.key
              ? this.getKeyMetadata(chip.key) ||
                this.getKeyMetadata(resolved.pillKey)
              : undefined;
            return {
              ...chip,
              pillKey: resolved.pillKey || chip.pillKey,
              pillCondition: resolved.pillCondition || chip.pillCondition,
              metadata: meta || chip.metadata,
              tjsFilter: filter,
            };
          },
        ),
      ),
      // TODO: Uniformly handle backend RPC error / 500 / offline display in search UI.
      catchError(() => of([])),
    );
  }

  // ===========================================================================
  // 6. ValuePicker Application & TJS Filter Helpers
  // ===========================================================================

  /** Constructs TJS-specific ValuePicker configuration based on key metadata. */
  protected override buildPickerConfig(
    key: string,
    title?: string,
    metadata?: unknown,
  ): ValuePickerConfig {
    const meta = (metadata as FilterKeyMetadata) || this.getKeyMetadata(key);
    const displayTitle = meta?.keyDisplayName || title || key;

    const namedPair =
      typeof meta?.namedPair === 'object' ? meta.namedPair : undefined;
    const textInput =
      typeof meta?.textInput === 'object' ? meta.textInput : undefined;

    let layoutType: 'list' | 'range' | 'namedPair' | 'text' = 'list';
    let needsName = false;
    let namePlaceholder = 'Name';
    let valPlaceholder = 'Value';

    if (meta?.timeRange) {
      layoutType = 'range';
    } else if (meta?.enumPicker) {
      layoutType = 'list';
    } else if (namedPair) {
      layoutType = 'namedPair';
      needsName = true;
      namePlaceholder = namedPair.namePlaceholder || 'Name';
      valPlaceholder = namedPair.valuePlaceholder || 'Value';
    } else if (textInput) {
      layoutType = 'text';
      valPlaceholder = textInput.placeholder || 'Value';
    } else {
      layoutType = 'text';
    }

    return {
      key,
      type: layoutType,
      title: displayTitle,
      needsName,
      canUseAdvanced: false,
      isPlural: !!meta?.isPlural,
      showNegateToggle: false,
      showAdvancedMenu: false,
      showSearchInput: layoutType === 'list',
      showRowActions: false,
      namePlaceholder,
      valPlaceholder,
    };
  }

  /** Constructs initial ValuePicker interaction state for TJS filter keys. */
  protected override buildInitialPickerState(
    key: string,
    activeChip?: FilterChip,
    metadata?: unknown,
    stagedValues?: string[],
  ): ValuePickerState {
    const meta = (metadata as FilterKeyMetadata) || this.getKeyMetadata(key);

    let enumOptions: PickerValueItem[] = [];
    const enumPicker =
      typeof meta?.enumPicker === 'object' ? meta.enumPicker : undefined;
    if (enumPicker?.options && enumPicker.options.length > 0) {
      enumOptions = enumPicker.options.map((o) => ({
        value: o.value,
        displayLabel: o.label || o.value,
      }));
    }

    let selectedVals = new Set<string>();
    if (activeChip) {
      if (activeChip.tjsFilter) {
        const rawVals = extractRawValuesFromTjsFilter(activeChip.tjsFilter);
        if (rawVals && rawVals.length > 0) {
          selectedVals = new Set(rawVals);
        }
      } else if (activeChip.rawValues && activeChip.rawValues.length > 0) {
        selectedVals = new Set(activeChip.rawValues);
      } else if (activeChip.pillCondition) {
        const existingVals = activeChip.pillCondition
          .split(',')
          .map((s: string) => s.trim())
          .filter(Boolean);
        selectedVals = new Set(existingVals);
      }
    }

    if (stagedValues && stagedValues.length > 0) {
      selectedVals = new Set([...selectedVals, ...stagedValues]);
    }

    return {
      loading: false,
      values: enumOptions,
      selectedValues: selectedVals,
      negated: false,
      advanced: {
        active: false,
        mode: 'prefix',
        text: '',
        values: [],
      },
    };
  }

  /** Handles ValuePicker apply events by resolving chips from API. */
  override applyValuePicker(event: ValuePickerApplyEvent) {
    const key = this.pickerKey();
    const title = this.pickerTitle();
    const meta = key ? this.getKeyMetadata(key) : undefined;
    const displayTitle = meta?.keyDisplayName || title;
    this.closeValuePicker();

    const rawValues = this.extractRawValues(event, meta);
    const draftChip: Partial<FilterChip> = {
      key,
      pillKey: displayTitle,
      rawValues,
      metadata: meta,
    };

    const tjsFilter = this.buildTjsFilterFromChip(draftChip);
    if (!tjsFilter) {
      this.removeChipForKey(key);
      return;
    }

    const req: TjsResolveChipsRequest = {filters: [tjsFilter]};
    this.searchService
      .resolveTjsChips(req)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          if (!res.chips || res.chips.length === 0) {
            return;
          }
          for (const c of res.chips) {
            this.addFilterChip({
              key,
              pillKey: c.pillKey || displayTitle,
              pillCondition: c.pillCondition,
              rawValues,
              metadata: meta,
              tjsFilter,
            });
          }
        },
        // TODO: Uniformly handle backend RPC error / 500 / offline display in search UI.
        error: () => {},
      });
  }

  /** Extracts raw values string array from ValuePickerApplyEvent. */
  private extractRawValues(
    event: ValuePickerApplyEvent,
    meta?: FilterKeyMetadata,
  ): string[] | undefined {
    if (event.rangeFrom || event.rangeTo) {
      return [event.rangeFrom || '', event.rangeTo || ''];
    }
    if (meta?.namedPair || event.propName) {
      return [event.propName || '', event.textVal || ''];
    }
    if (event.textVal?.trim()) {
      return [event.textVal.trim()];
    }
    if (event.selected && event.selected.length > 0) {
      return event.selected;
    }
    return undefined;
  }

  // ===========================================================================
  // 7. Suggestion Selection, Chip Mutators, & State Reset
  // ===========================================================================

  /** Unpacks TjsSuggestion payload to open value picker or apply filter chips. */
  protected override applySuggestion(
    rawItem: unknown,
    anchor?: HTMLElement | null,
  ) {
    const raw = rawItem as TjsSuggestion;
    if (!raw) return;

    if (raw.openPicker) {
      const op = raw.openPicker;
      const meta = this.getKeyMetadata(op.key);
      const displayTitle = op.keyDisplayName || meta?.keyDisplayName || op.key;
      this.openValuePicker(op.key, anchor || null, displayTitle, meta);
      return;
    }

    const chip = extractFilterChipFromTjsSuggestion(raw);
    if (chip) {
      this.addFilterChip(chip);
    }
  }
}
