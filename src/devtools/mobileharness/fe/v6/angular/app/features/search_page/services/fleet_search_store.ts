import {
  computed,
  Injectable,
  linkedSignal,
  signal,
} from '@angular/core';
import {rxResource, takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Observable, of} from 'rxjs';
import {catchError, map} from 'rxjs/operators';

import {
  Column,
  Filter,
  Fleet,
  FleetChipResolverRequest,
  FleetFilterChipMetadata,
  FleetFlatResults,
  FleetPromotedGroupByKey,
  FleetPromotedKeysRequest,
  FleetSearchConfig,
  FleetSearchConfigRequest,
  FleetSearchRequest,
  FleetSearchResults,
  FleetSuggestion,
  FleetSuggestionRequest,
  FleetValueListRequest,
  Row,
  SearchEntity,
} from '../../../core/models/search';
import {
  EntityType,
  FilterChip,
  FilterKeyMetadata,
  ParsedQueryFilter,
  PickerValueItem,
  PromotedFilterKeyItem,
  PromotedGroupByKeyItem,
  SearchBoxSuggestion,
  ValuePickerApplyEvent,
  ValuePickerConfig,
  ValuePickerState,
} from '../models';
import {
  AdvancedMatchMode,
  buildComplexMatchFromEvent,
  buildFleetFilterFromChip,
  buildFleetGroupSort,
  buildSimpleFleetFilter,
  extractAdvancedStateFromChip,
  extractFilterChipFromFleetSuggestion,
  formatPillConditionFromEvent,
  isChipNegated,
  isValuePickerSelectionEmpty,
  mapToSearchBoxSuggestion,
} from '../utils';
import {SearchPageStore} from './search_page_store';

/**
 * State store managing search, grouping, pagination, and selection for Fleet entities (Devices & Hosts).
 */
@Injectable()
export class FleetSearchStore extends SearchPageStore {
  // ===========================================================================
  // 1. Dynamic UI Prompts & Suggestion Signals
  // ===========================================================================

  /** Placeholder text for search box input based on current entity (devices vs hosts). */
  override readonly searchPlaceholder = computed(() => {
    switch (this.entity()) {
      case 'devices':
        return 'Type "pixel 10", or "model is", or a device ID';
      case 'hosts':
        return 'Type a host name, or "status is", or a host ID';
      default:
        return 'Type search query…';
    }
  });

  /** Autocomplete suggestions transformed into SearchBoxSuggestion items. */
  override readonly suggestions = computed<SearchBoxSuggestion[]>(() => {
    return this.fleetSuggestions().map(mapToSearchBoxSuggestion);
  });

  /** Reactive resource fetching search bar autocomplete suggestions. */
  private readonly suggestionsResource = rxResource<
    FleetSuggestion[],
    | {
        entity: EntityType;
        input: string;
        fleet: string;
        activeFilters: Filter[];
        groupBy: string[];
      }
    | undefined
  >({
    params: () => {
      const e = this.entity();
      if (e !== 'devices' && e !== 'hosts') return undefined;
      const show = this.showSuggestions();
      if (!show) return undefined;

      return {
        entity: e,
        input: this.debouncedSearchQuery(),
        fleet: this.fleet(),
        activeFilters: this.getAllEffectiveFilters(),
        groupBy: this.groupByKeys(),
      };
    },
    stream: ({params: req}) => {
      if (!req) return of([]);
      const fleetReq: FleetSuggestionRequest = {
        entity: this.getSearchEntityProto(req.entity),
        input: req.input,
        filters: req.activeFilters.length > 0 ? req.activeFilters : undefined,
        groupBy: req.groupBy.length > 0 ? req.groupBy : undefined,
        fleet: req.fleet === 'ats' ? Fleet.FLEET_ATS : Fleet.FLEET_SELF,
        limit: 10,
      };
      return this.searchService.getFleetSuggestions(fleetReq).pipe(
        map((res) => res.items || []),
        catchError(() => of([])),
      );
    },
  });

  /** Exposes current array of fleet suggestions. */
  readonly fleetSuggestions = computed<FleetSuggestion[]>(
    () => this.suggestionsResource.value() || [],
  );

  // ===========================================================================
  // 2. Page Configuration & Metadata Resources
  // ===========================================================================

  /** Reactive resource fetching search configuration (defaults, landing settings, default columns). */
  private readonly searchConfigResource = rxResource<
    FleetSearchConfig | null,
    string | undefined
  >({
    params: () => {
      const e = this.entity();
      if (e !== 'devices' && e !== 'hosts') return undefined;
      return `${e}:${this.fleet()}`;
    },
    stream: ({params: key}) => {
      if (!key) return of(null);
      const [e, fleet] = key.split(':') as [EntityType, string];
      const fleetReq: FleetSearchConfigRequest = {
        entity: this.getSearchEntityProto(e),
        fleet: fleet === 'ats' ? Fleet.FLEET_ATS : Fleet.FLEET_SELF,
      };
      return this.searchService
        .getFleetSearchConfig(fleetReq)
        .pipe(catchError(() => of(null)));
    },
  });

  /** Configuration for the current search page. */
  override readonly searchConfig = computed<FleetSearchConfig | null>(
    () => this.searchConfigResource.value() || null,
  );

  /** Whether initial search configuration is loading. */
  override readonly isConfigLoading = computed<boolean>(
    () => this.searchConfigResource.isLoading(),
  );

  /** Reactive resource fetching promoted filter keys and promoted group-by keys. */
  private readonly promotedKeysResource = rxResource<
    {
      filterKeys: PromotedFilterKeyItem[];
      groupByKeys: PromotedGroupByKeyItem[];
    },
    | {
        entity: EntityType;
        fleet: string;
        groupBy: string[];
        filters: Filter[];
      }
    | undefined
  >({
    params: () => {
      const e = this.entity();
      if (e !== 'devices' && e !== 'hosts') return undefined;

      // Wait for searchConfig to load default chips on clean page entry to prevent duplicate RPC calls
      const queryParams = this.route.snapshot?.queryParams;
      const hasUrlFilters = queryParams?.['f'] || queryParams?.['gb'];
      if (this.isConfigLoading() && !hasUrlFilters && this.activeChips().length === 0) {
        return undefined;
      }

      return {
        entity: e,
        fleet: this.fleet(),
        groupBy: this.groupByKeys(),
        filters: this.getAllEffectiveFilters(),
      };
    },
    stream: ({params: req}) => {
      if (!req) return of({filterKeys: [], groupByKeys: []});

      const fleetReq: FleetPromotedKeysRequest = {
        entity: this.getSearchEntityProto(req.entity),
        fleet: req.fleet === 'ats' ? Fleet.FLEET_ATS : Fleet.FLEET_SELF,
        groupBy: req.groupBy.length > 0 ? req.groupBy : undefined,
        filters: req.filters.length > 0 ? req.filters : undefined,
      };
      return this.searchService.getFleetPromotedKeys(fleetReq).pipe(
        map((res) => ({
          filterKeys: res.filterKeys || [],
          groupByKeys: res.groupByKeys || [],
        })),
        catchError(() => of({filterKeys: [], groupByKeys: []})),
      );
    },
  });

  /** Promoted filter key preset items. */
  override readonly promotedFilterKeys = computed<PromotedFilterKeyItem[]>(
    () => this.promotedKeysResource.value()?.filterKeys || [],
  );

  /** Promoted group-by key preset items. */
  override readonly promotedGroupByKeys = computed<PromotedGroupByKeyItem[]>(
    () => this.promotedKeysResource.value()?.groupByKeys || [],
  );

  /** Whether promoted keys are currently loading. */
  override readonly isPromotedKeysLoading = computed<boolean>(
    () => this.promotedKeysResource.isLoading(),
  );

  /** Dynamic dictionary map containing metadata for all known filter and group-by keys. */
  override readonly keyMetadataMap = computed<Map<string, FilterKeyMetadata>>(
    () => {
      const nextMap = new Map<string, FilterKeyMetadata>();
      const res = this.promotedKeysResource.value();
      for (const pk of res?.filterKeys || []) {
        if (pk.metadata) {
          nextMap.set(pk.key, pk.metadata);
        }
      }
      for (const gk of res?.groupByKeys || []) {
        if (!nextMap.has(gk.key)) {
          nextMap.set(gk.key, {keyDisplayName: gk.displayName});
        }
      }
      return nextMap;
    },
  );

  // ===========================================================================
  // 3. Table Columns, Selection, & View Configuration
  // ===========================================================================

  /** Set of currently selected item (device/host) IDs across search results. */
  readonly selectedItems = signal<Set<string>>(new Set());

  /** Current column key used for table sorting. */
  readonly sortColumn = signal<string>('id');

  /** Sort order direction (true = ascending, false = descending). */
  readonly sortAsc = signal<boolean>(true);

  /** Set of column keys currently requested/visible in the data table (linked to searchConfig defaults). */
  readonly visibleColumns = linkedSignal<FleetSearchConfig | null, Set<string>>({
    source: () => this.searchConfig(),
    computation: (cfg) => {
      const keys = cfg?.columns?.defaults?.map((c) => c.key) ?? [];
      return new Set(keys);
    },
  });

  /** Available table column choices derived from search config or default fallback list. */
  readonly availableColumns = computed<Array<{key: string; label: string}>>(
    () => {
      const configCols = this.searchConfig()?.columns?.defaults;
      if (configCols && configCols.length > 0) {
        return configCols.map((c) => ({key: c.key, label: c.displayName}));
      }
      return [
        {key: 'id', label: 'Device ID / UUID'},
        {key: 'hostName', label: 'Host Name'},
        {key: 'status', label: 'Status'},
        {key: 'type', label: 'Device Type'},
        {key: 'owner', label: 'Owner'},
        {key: 'model', label: 'Model'},
      ];
    },
  );

  /** Display columns to render in search results table. */
  override readonly displayColumns = computed<Column[]>(() => {
    const cols = this.searchResource.value()?.flat?.columns;
    if (cols && cols.length > 0) {
      return cols;
    }
    const cfgCols = this.searchConfig()?.columns?.defaults;
    if (cfgCols && cfgCols.length > 0) {
      return cfgCols.map((c) => ({key: c.key, displayName: c.displayName}));
    }
    return [];
  });

  // ===========================================================================
  // 4. Grouped View & Header Accordion States
  // ===========================================================================

  /** Unique scope key reflecting the active query parameters for invalidating expanded group state. */
  private readonly searchScopeKey = computed(() => {
    return `${this.entity()}:${this.currentUrlParamKey()}:${this.groupSort()}`;
  });

  /** Set of expanded group header IDs, automatically reset when query scope changes. */
  readonly openGroupIds = linkedSignal<string, Set<string>>({
    source: () => this.searchScopeKey(),
    computation: () => new Set<string>(),
  });

  /** Sorting mode for grouped view headers. */
  readonly groupSort = signal<string>('count:desc');

  /** Map holding pagination data and loading states for expanded group headers, automatically reset when query scope changes. */
  readonly expandedGroupPages = linkedSignal<
    string,
    Map<string, {loading?: boolean; data?: FleetFlatResults; error?: string}>
  >({
    source: () => this.searchScopeKey(),
    computation: () => new Map(),
  });

  /** Grouped results object from the primary search resource. */
  readonly groupedResults = computed(() => this.searchResource.value()?.grouped);

  /** Formats display label text for active group-by headers (e.g. "Model × Status"). */
  readonly groupKeysText = computed(() => {
    const keys = this.searchResource.value()?.grouped?.groupByKeys || [];
    return keys
      .map((k: FleetPromotedGroupByKey) => k.displayName || k.key)
      .join(' × ');
  });

  /** Dynamic options list for sorting group header cards based on entity and active group-by chips. */
  override readonly groupSortOptions = computed<Array<{value: string; label: string}>>(() => {
    const entityLabel = this.entity() === 'hosts' ? 'hosts' : 'devices';
    const countOptions = [
      {value: 'count:desc', label: `Most ${entityLabel}`},
      {value: 'count:asc', label: `Fewest ${entityLabel}`},
    ];

    const groupByChips = this.activeChips().filter((c) => c.isGroupBy);
    if (groupByChips.length === 0) {
      return [
        ...countOptions,
        {value: 'name:asc', label: 'Name (A–Z)'},
        {value: 'name:desc', label: 'Name (Z–A)'},
      ];
    }

    const gbOptions: Array<{value: string; label: string}> = [];
    for (const chip of groupByChips) {
      const rawKey = chip.key ? chip.key.replace(/^group_by_/, '') : chip.pillKey;
      const meta = this.keyMetadataMap().get(rawKey);
      const displayName = chip.pillKey || meta?.keyDisplayName || rawKey;

      gbOptions.push({
        value: `gb_asc:${rawKey}`,
        label: `${displayName} (A–Z)`,
      });
      gbOptions.push({
        value: `gb_desc:${rawKey}`,
        label: `${displayName} (Z–A)`,
      });
    }

    return [...countOptions, ...gbOptions];
  });

  /** Sets the sort order mode for group header cards and resets pagination. */
  setGroupSort(value: string) {
    this.pageIndex.set(0);
    this.pageToken.set('');
    this.groupSort.set(value);
  }

  /** Toggles accordion open/collapsed state for a group and loads rows if newly opened and not cached. */
  toggleGroup(groupId: string) {
    const current = new Set(this.openGroupIds());
    if (current.has(groupId)) {
      current.delete(groupId);
    } else {
      current.add(groupId);
      const existing = this.expandedGroupPages().get(groupId);
      if (!existing?.data && !existing?.loading) {
        this.onLoadGroupRows(groupId);
      }
    }
    this.openGroupIds.set(current);
  }

  /** Loads flat rows for a specific expanded group header. */
  onLoadGroupRows(groupId: string, pageToken?: string) {
    const e = this.entity();
    if (e !== 'devices' && e !== 'hosts') return;

    const filters = this.getAllEffectiveFilters();

    const req: FleetSearchRequest = {
      entity: this.getSearchEntityProto(e),
      fleet: this.fleet() === 'ats' ? Fleet.FLEET_ATS : Fleet.FLEET_SELF,
      filters: filters.length > 0 ? filters : undefined,
      groupExpand: {
        groupId,
        columns: Array.from(this.visibleColumns()),
        pageToken,
      },
    };

    this.updateGroupExpandState(groupId, {loading: true});

    this.searchService
      .searchFleet(req)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.updateGroupExpandState(groupId, {
            loading: false,
            data: res.flat,
          });
        },
        error: (err) => {
          this.updateGroupExpandState(groupId, {
            loading: false,
            error: err?.message || 'Failed to load group rows.',
          });
        },
      });
  }

  /** Helper to update state for an expanded group in expandedGroupPages map. */
  private updateGroupExpandState(
    groupId: string,
    patch: {loading?: boolean; data?: FleetFlatResults; error?: string},
  ) {
    this.expandedGroupPages.update((map) => {
      const next = new Map(map);
      const existing = next.get(groupId);
      next.set(groupId, {...existing, ...patch});
      return next;
    });
  }

  // ===========================================================================
  // 5. ValuePicker Popover Async Options Resource
  // ===========================================================================

  /** Reactive resource fetching value list items for the active Popover ValuePicker. */
  private readonly valueListResource = rxResource<
    PickerValueItem[],
    | {
        entity: EntityType;
        key: string;
        activeFilters: Filter[];
        fleet: string;
      }
    | undefined
  >({
    params: () => {
      const e = this.entity();
      if (e !== 'devices' && e !== 'hosts') return undefined;
      const key = this.pickerKey();
      const show = this.showValuePicker();
      if (!show || !key) return undefined;

      return {
        entity: e,
        key,
        activeFilters: this.getAllEffectiveFilters(),
        fleet: this.fleet(),
      };
    },
    stream: ({params: req}) => {
      if (!req) return of([]);
      const fleetReq: FleetValueListRequest = {
        entity: this.getSearchEntityProto(req.entity),
        key: req.key,
        filters: req.activeFilters.length > 0 ? req.activeFilters : undefined,
        fleet: req.fleet === 'ats' ? Fleet.FLEET_ATS : Fleet.FLEET_SELF,
      };
      return this.searchService.getFleetValueList(fleetReq).pipe(
        map((res) => {
          if (res.counted) {
            const list: PickerValueItem[] = (res.counted.values || []).map(
              (v) => ({
                value: v.value,
                displayLabel: v.displayLabel || v.value,
                filtered: v.filtered,
                total: v.total,
              }),
            );
            if (res.counted.noValueEntry) {
              list.push({
                value: '<empty>',
                displayLabel: '(no value)',
                filtered: res.counted.noValueEntry.filtered,
                total: res.counted.noValueEntry.total,
                isNoValue: true,
              });
            }
            return list;
          }

          if (res.plain) {
            const list: PickerValueItem[] = (res.plain.values || []).map(
              (v) => ({
                value: v.value,
                displayLabel: v.displayLabel || v.value,
              }),
            );
            if (res.plain.noValueEntry) {
              list.push({
                value: '<empty>',
                displayLabel: '(no value)',
                isNoValue: true,
              });
            }
            return list;
          }

          return [];
        }),
        catchError(() => of([])),
      );
    },
  });

  /** Loading state indicator for the async Popover ValuePicker options. */
  override readonly isPickerLoading = computed<boolean>(
    () => this.valueListResource.isLoading(),
  );

  /** Async options list delivered to the Popover ValuePicker. */
  override readonly asyncPickerValues = computed<PickerValueItem[]>(
    () => this.valueListResource.value() || [],
  );

  // ===========================================================================
  // 6. Primary Search Resource & Execution
  // ===========================================================================

  /** Primary reactive resource fetching flat or grouped fleet search results. */
  readonly searchResource = rxResource<
    FleetSearchResults | null,
    FleetSearchRequest | undefined
  >({
    params: () => {
      const e = this.entity();
      if (e !== 'devices' && e !== 'hosts') return undefined;
      if (this.isLandingState()) return undefined;

      const fleet = this.fleet();
      const pageSize = this.pageSize();
      const pageToken = this.pageToken();

      const filters = this.getAllEffectiveFilters();
      const groupByKeys = this.groupByKeys();

      const req: FleetSearchRequest = {
        entity: this.getSearchEntityProto(e),
        fleet: fleet === 'ats' ? Fleet.FLEET_ATS : Fleet.FLEET_SELF,
        filters: filters.length > 0 ? filters : undefined,
      };

      if (groupByKeys.length > 0) {
        const groupSort = this.groupSort();
        req.groupHeader = {
          groupBy: groupByKeys,
          sort: buildFleetGroupSort(groupSort, groupByKeys),
          page: {
            pageSize,
            pageToken: pageToken || undefined,
          },
        };
      } else {
        const visibleColumns = Array.from(this.visibleColumns());
        const sortColumn = this.sortColumn();
        const sortAsc = this.sortAsc();
        req.flat = {
          columns: visibleColumns,
          sort: {key: sortColumn, ascending: sortAsc},
          page: {
            pageSize,
            pageToken: pageToken || undefined,
          },
        };
      }
      return req;
    },
    stream: ({params: req}) => {
      if (!req) {
        return of(null);
      }
      return this.searchService
        .searchFleet(req)
        .pipe(catchError(() => of(null)));
    },
  });

  /** Exposes loading status of primary search resource. */
  override readonly isLoading = computed(() => this.searchResource.isLoading());

  /** Rows array to render in search results table. */
  override readonly rows = computed<Row[]>(() => {
    return (this.searchResource.value()?.flat?.rows as Row[]) || [];
  });

  /** Determines whether a next page is available based on API nextPageToken. */
  override readonly hasNextPage = computed<boolean>(() => {
    const res = this.searchResource.value();
    const isGrouped = this.groupByKeys().length > 0;
    const token = isGrouped
      ? res?.grouped?.nextPageToken
      : res?.flat?.nextPageToken;
    return !!token;
  });

  /** Total matching count across flat or grouped search. */
  readonly effectiveTotalCount = computed(() => {
    return (
      (this.groupByKeys().length > 0
        ? this.groupedResults()?.totalItems
        : this.searchResource.value()?.flat?.total) || 0
    );
  });

  /** Start 1-based row range returned directly from API. */
  readonly effectiveRangeStart = computed(() => {
    const res = this.searchResource.value();
    return (
      (this.groupByKeys().length > 0
        ? res?.grouped?.rangeStart
        : res?.flat?.rangeStart) || 0
    );
  });

  /** End 1-based row range returned directly from API. */
  readonly effectiveRangeEnd = computed(() => {
    const res = this.searchResource.value();
    return (
      (this.groupByKeys().length > 0
        ? res?.grouped?.rangeEnd
        : res?.flat?.rangeEnd) || 0
    );
  });

  /** Combines active filter chips into backend Filter array. */
  private getAllEffectiveFilters(): Filter[] {
    return this.activeChips()
      .filter((c) => !c.isGroupBy)
      .map((c) => buildFleetFilterFromChip(c));
  }

  /** Reloads the primary search resource and resets page token and group expansions. */
  override executeSearch() {
    this.pageIndex.set(0);
    this.pageToken.set('');
    this.openGroupIds.set(new Set());
    this.expandedGroupPages.set(new Map());
    this.searchResource.reload();
  }

  /** Maps string entity type to SearchEntity protobuf enum value. */
  private getSearchEntityProto(entity: EntityType): SearchEntity {
    return entity === 'hosts'
      ? SearchEntity.SEARCH_ENTITY_HOST
      : SearchEntity.SEARCH_ENTITY_DEVICE;
  }

  // ===========================================================================
  // 7. Pagination Controls
  // ===========================================================================

  /** Helper to extract next/prev page token from search results. */
  private getPaginationToken(type: 'next' | 'prev'): string | undefined {
    const res = this.searchResource.value();
    const isGrouped = this.groupByKeys().length > 0;
    if (type === 'next') {
      return isGrouped ? res?.grouped?.nextPageToken : res?.flat?.nextPageToken;
    }
    return isGrouped ? res?.grouped?.prevPageToken : res?.flat?.prevPageToken;
  }

  /** Navigates to previous page of search results. */
  override prevPage() {
    const token = this.getPaginationToken('prev');
    if (token) {
      this.pageToken.set(token);
      this.pageIndex.update((p) => Math.max(0, p - 1));
    }
  }

  /** Navigates to next page of search results. */
  override nextPage() {
    const token = this.getPaginationToken('next');
    if (token) {
      this.pageToken.set(token);
      this.pageIndex.update((p) => p + 1);
    }
  }

  // ===========================================================================
  // 8. URL Resolution & ValuePicker Application
  // ===========================================================================

  /** Resolves parsed URL query filters and group-by keys into FilterChip list via Fleet RPC. */
  protected override resolveChipsFromBackend(
    parsedFilters: ParsedQueryFilter[],
    groupByKeys: string[],
  ): Observable<FilterChip[]> {
    const req: FleetChipResolverRequest = {
      filters:
        parsedFilters.length > 0
          ? parsedFilters.map((pf) => pf.filter)
          : undefined,
      groupByKeys: groupByKeys.length > 0 ? groupByKeys : undefined,
    };

    return this.searchService.resolveFleetChips(req).pipe(
      map((res) => {
        const updatedChips: FilterChip[] = [];
        if (res.filterChips && res.filterChips.length > 0) {
          res.filterChips.forEach((fc, idx) => {
            const pf = parsedFilters[idx];
            updatedChips.push({
              key: pf?.key,
              pillKey: fc.pillKey,
              pillCondition: fc.pillCondition,
              metadata: fc.metadata || pf?.metadata,
              rawValues: pf?.rawValues,
              negated: pf?.negated,
              complex: pf?.complex,
            });
          });
        }

        groupByKeys.forEach((gb, idx) => {
          const gbc = res.groupByChips?.[idx];
          updatedChips.push(
            this.buildGroupByChip(gb, gbc?.displayName || gbc?.pillKey),
          );
        });

        return updatedChips;
      }),
    );
  }

  /** Constructs Fleet-specific ValuePicker configuration. */
  protected override buildPickerConfig(
    key: string,
    title?: string,
    metadata?: unknown,
  ): ValuePickerConfig {
    const meta =
      (metadata as FleetFilterChipMetadata) ||
      this.keyMetadataMap().get(key);
    const displayTitle = meta?.keyDisplayName || title || key;

    return {
      key,
      type: 'list',
      title: displayTitle,
      needsName: false,
      canUseAdvanced: !!meta?.canUseAdvanced,
      isPlural: !!meta?.isPlural,
      showNegateToggle: true,
      showAdvancedMenu: !!meta?.canUseAdvanced,
      showSearchInput: true,
      showRowActions: true,
      namePlaceholder: 'Name',
      valPlaceholder: 'Value',
    };
  }

  /** Constructs initial ValuePicker interaction state from an active filter chip. */
  protected override buildInitialPickerState(
    key: string,
    activeChip?: FilterChip,
    metadata?: unknown,
  ): ValuePickerState {
    let isAdv = false;
    let advMode: AdvancedMatchMode = 'prefix';
    let advText = '';
    let advValues: string[] = [];
    let isNegated = false;
    let selectedVals = new Set<string>();

    if (activeChip) {
      const state = extractAdvancedStateFromChip(activeChip);
      isAdv = state.isAdv;
      if (isAdv) {
        advMode = state.advMode;
        advText = state.advText;
        advValues = state.advValues;
      }
      isNegated = isChipNegated(activeChip);

      if (!isAdv) {
        if (activeChip.rawValues && activeChip.rawValues.length > 0) {
          selectedVals = new Set(activeChip.rawValues);
        } else if (activeChip.pillCondition) {
          const existingVals = activeChip.pillCondition
            .replace(
              /^(Starts with|Contains|Does not contain|Matches regex|Does not match regex|Is exactly|Is at least)\s*"?([^"]*)"?$/i,
              '$2',
            )
            .split(',')
            .map((s: string) => s.trim())
            .map((s: string) => (s === '(no value)' ? '<empty>' : s))
            .filter(Boolean);
          selectedVals = new Set(existingVals);
        }
      }
    }

    return {
      loading: false,
      values: [],
      selectedValues: selectedVals,
      negated: isNegated,
      advanced: {
        active: isAdv,
        mode: advMode,
        text: advText,
        values: advValues,
      },
    };
  }

  /** Applies selections or advanced matching input from Popover ValuePicker. */
  override applyValuePicker(event: ValuePickerApplyEvent) {
    const key = this.pickerKey();
    const title = this.pickerTitle();
    const meta = key ? this.keyMetadataMap().get(key) : undefined;
    const displayTitle = meta?.keyDisplayName || title;
    this.closeValuePicker();

    if (isValuePickerSelectionEmpty(event)) {
      this.removeChipForKey(key);
      return;
    }

    const fallbackPillCondition = formatPillConditionFromEvent(event);

    const rawValues = event.isAdvanced
      ? event.advValues && event.advValues.length > 0
        ? event.advValues
        : event.advText
          ? [event.advText.trim()]
          : undefined
      : event.selected;

    const filter: Filter = event.isAdvanced
      ? {key, complex: buildComplexMatchFromEvent(event)}
      : buildSimpleFleetFilter(key, event.selected || [], event.negate);
    const complex = filter.complex;

    const req: FleetChipResolverRequest = {
      filters: [filter],
    };

    const applyFallback = () => {
      this.addFilterChip({
        key,
        pillKey: displayTitle,
        pillCondition: fallbackPillCondition,
        isGroupBy: false,
        metadata: meta,
        rawValues,
        negated: event.isAdvanced ? false : event.negate,
        complex: event.isAdvanced ? complex : undefined,
      });
    };

    this.searchService
      .resolveFleetChips(req)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          if (res.filterChips && res.filterChips.length > 0) {
            for (const fc of res.filterChips) {
              this.addFilterChip({
                key,
                pillKey: fc.pillKey,
                pillCondition: fc.pillCondition,
                isGroupBy: false,
                metadata: fc.metadata || meta,
                rawValues,
                negated: event.isAdvanced ? false : event.negate,
                complex: event.isAdvanced ? complex : undefined,
              });
            }
          } else {
            applyFallback();
          }
        },
        error: applyFallback,
      });
  }

  // ===========================================================================
  // 9. Suggestion Selection & State Reset
  // ===========================================================================

  /** Directly resolves and applies fleet suggestion filters or group-by selections to store. */
  override selectSuggestion(item: SearchBoxSuggestion) {
    const raw = item.rawItem as FleetSuggestion;
    if (!raw) return;

    this.showSuggestions.set(false);
    this.searchQuery.set('');

    this.applyFleetSuggestionDirectly(raw);
  }

  /** Helper to directly apply a FleetSuggestion locally without redundant backend resolution. */
  private applyFleetSuggestionDirectly(item: FleetSuggestion) {
    const chip = extractFilterChipFromFleetSuggestion(item);
    if (chip) {
      this.addFilterChip(chip);
    }
    if (item.addGroupBy) {
      const actionKey = item.addGroupBy.key;
      const pillTitle = item.addGroupBy.pillKey || item.addGroupBy.key;
      this.addFilterChip(this.buildGroupByChip(actionKey, pillTitle));
    }
  }

  /** Resets search state including group view expand states and selected items. */
  override resetSearchState(updateUrl = true) {
    super.resetSearchState(updateUrl);

    this.openGroupIds.set(new Set());
    this.expandedGroupPages.set(new Map());
    this.selectedItems.set(new Set());
  }
}
