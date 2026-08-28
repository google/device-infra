import {
  computed,
  inject,
  Injectable,
  linkedSignal,
  signal,
} from '@angular/core';
import {rxResource, takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Observable, of} from 'rxjs';
import {catchError, map} from 'rxjs/operators';

import {
  Column,
  ComplexMatch,
  Filter,
  FleetChipResolverRequest,
  FleetColumnDescriptor,
  FleetFilterChipMetadata,
  FleetFlatResults,
  FleetGroup,
  FleetPromotedKeysRequest,
  FleetSearchConfig,
  FleetSearchConfigRequest,
  FleetSearchRequest,
  FleetSearchResults,
  FleetSuggestion,
  FleetSuggestionRequest,
  FleetValueListRequest,
  Row,
} from '../../../core/models/search';
import {SnackBarService} from '../../../shared/services/snackbar_service';
import {
  AdvancedMatchMode,
  EntityType,
  FilterChip,
  FilterKeyMetadata,
  FilterPair,
  PickerValueItem,
  PromotedFilterKeyItem,
  PromotedGroupByKeyItem,
  SearchBoxSuggestion,
  ValuePickerApplyEvent,
  ValuePickerConfig,
  ValuePickerState,
} from '../models';
import {
  buildComplexMatchFromEvent,
  buildFleetFilterFromChip,
  buildFleetGroupSort,
  buildResolvedFilterChips,
  buildSimpleFleetFilter,
  clearStoredVisibleColumns,
  createGroupByChip,
  EMPTY_FILTER_VALUE,
  extractAdvancedStateFromChip,
  extractComplexMatchInfo,
  extractFilterChipFromFleetSuggestion,
  getChipKey,
  getStoredVisibleColumns,
  isChipNegated,
  isValuePickerSelectionEmpty,
  mapToSearchBoxSuggestion,
  saveStoredVisibleColumns,
  toFleetProto,
  toSearchEntityProto,
} from '../utils';
import {SearchPageStore} from './search_page_store';

/**
 * State store managing search, grouping, pagination, and selection for Fleet entities (Devices & Hosts).
 */
@Injectable()
export class FleetSearchStore extends SearchPageStore {
  private readonly snackBarService = inject(SnackBarService);
  // ===========================================================================
  // 1. Dynamic UI Prompts & Suggestion Signals
  // ===========================================================================

  private static readonly SUPPORTED_ENTITIES = new Set<EntityType>([
    'devices',
    'hosts',
  ]);

  /** Subclass hook providing default entity type when route metadata is absent. */
  override getDefaultEntity(): EntityType {
    return 'devices';
  }

  override getSupportedEntities(): Set<EntityType> {
    return FleetSearchStore.SUPPORTED_ENTITIES;
  }

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
      if (!this.isCurrentRouteActive()) return undefined;
      const show = this.showSuggestions();
      if (!show) return undefined;

      return {
        entity: this.entity(),
        input: this.debouncedSearchQuery(),
        fleet: this.fleet(),
        activeFilters: this.effectiveFilters(),
        groupBy: this.groupByKeys(),
      };
    },
    stream: ({params: req}) => {
      if (!req) return of([]);
      const fleetReq: FleetSuggestionRequest = {
        entity: toSearchEntityProto(req.entity),
        input: req.input,
        filters: req.activeFilters.length > 0 ? req.activeFilters : undefined,
        groupBy: req.groupBy.length > 0 ? req.groupBy : undefined,
        fleet: toFleetProto(req.fleet),
        limit: 10,
      };
      return this.searchService.getFleetSuggestions(fleetReq).pipe(
        map((res) => res.items || []),
        catchError(() => of([])),
      );
    },
  });

  /** Autocomplete suggestions transformed into SearchBoxSuggestion items. */
  override readonly suggestions = computed<SearchBoxSuggestion[]>(() => {
    return (this.suggestionsResource.value() || []).map(
      mapToSearchBoxSuggestion,
    );
  });

  // ===========================================================================
  // 2. Page Configuration & Metadata Resources
  // ===========================================================================

  /** Reactive resource fetching search configuration (defaults, landing settings, default columns). */
  private readonly searchConfigResource = rxResource<
    FleetSearchConfig | null,
    string | undefined
  >({
    params: () => {
      if (!this.isCurrentRouteActive()) return undefined;
      return `${this.entity()}:${this.fleet()}`;
    },
    stream: ({params: key}) => {
      if (!key) return of(null);
      const [e, fleet] = key.split(':') as [EntityType, string];
      const fleetReq: FleetSearchConfigRequest = {
        entity: toSearchEntityProto(e),
        fleet: toFleetProto(fleet),
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
  override readonly isConfigLoading = computed<boolean>(() =>
    this.searchConfigResource.isLoading(),
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
      if (!this.isCurrentRouteActive()) return undefined;
      return {
        entity: this.entity(),
        fleet: this.fleet(),
        groupBy: this.groupByKeys(),
        filters: this.effectiveFilters(),
      };
    },
    stream: ({params: req}) => {
      if (!req) return of({filterKeys: [], groupByKeys: []});

      const fleetReq: FleetPromotedKeysRequest = {
        entity: toSearchEntityProto(req.entity),
        fleet: toFleetProto(req.fleet),
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
  override readonly isPromotedKeysLoading = computed<boolean>(() =>
    this.promotedKeysResource.isLoading(),
  );

  /** Dynamic dictionary map containing metadata for all known filter and group-by keys. */
  override readonly keyMetadataMap = computed<Map<string, FilterKeyMetadata>>(
    () => {
      const res = this.promotedKeysResource.value();
      if (!res) return new Map();

      const nextMap = new Map<string, FilterKeyMetadata>();
      for (const pk of res.filterKeys) {
        if (pk.metadata) {
          nextMap.set(pk.key, pk.metadata);
        }
      }
      for (const gk of res.groupByKeys) {
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

  /** Whether all matching records across all pages are selected. */
  readonly selectAllMatching = signal<boolean>(false);

  /** Set of item IDs excluded from a select-all-matching selection. */
  readonly excludedItems = signal<Set<string>>(new Set());

  /** Total count of items excluded from a select-all-matching selection. */
  readonly excludedCount = computed(() => this.excludedItems().size);

  /** Total count of selected items, respecting selectAllMatching and excluded items. */
  readonly selectedCount = computed(() => {
    if (this.selectAllMatching()) {
      return Math.max(0, this.effectiveTotalCount() - this.excludedCount());
    }
    return this.selectedItems().size;
  });

  /** Clears the current selection state (both individual and select-all-matching). */
  clearSelection() {
    this.selectedItems.set(new Set());
    this.selectAllMatching.set(false);
    this.excludedItems.set(new Set());
  }

  /** Selects all matching records across all pages. */
  selectAllMatchingRecords() {
    this.selectAllMatching.set(true);
    this.excludedItems.set(new Set());
  }

  /** Toggles individual row selection state. */
  toggleSelectRow(id: string) {
    if (this.selectAllMatching()) {
      const currentExcluded = new Set(this.excludedItems());
      if (currentExcluded.has(id)) {
        currentExcluded.delete(id);
      } else {
        currentExcluded.add(id);
      }
      this.excludedItems.set(currentExcluded);
      if (currentExcluded.size === this.effectiveTotalCount()) {
        this.clearSelection();
      }
    } else {
      const nextSet = new Set(this.selectedItems());
      if (nextSet.has(id)) {
        nextSet.delete(id);
      } else {
        nextSet.add(id);
      }
      this.selectedItems.set(nextSet);
    }
  }

  /** Toggles selection of all rows currently visible on page. */
  toggleSelectAll() {
    if (this.selectAllMatching() || this.isAllSelected()) {
      this.clearSelection();
    } else {
      const nextSet = new Set(this.selectedItems());
      this.visibleRowIds().forEach((id) => {
        nextSet.add(id);
      });
      this.selectedItems.set(nextSet);
    }
  }

  /** Toggles selection for a list of rows (e.g. from an expanded group card page). */
  toggleSelectRows(rows: Array<{id: string}> | undefined) {
    if (!rows || rows.length === 0) return;
    const current = new Set(this.selectedItems());
    const allSelected = rows.every((r) => current.has(r.id));
    if (allSelected) {
      rows.forEach((r) => {
        current.delete(r.id);
      });
    } else {
      rows.forEach((r) => {
        current.add(r.id);
      });
    }
    this.selectedItems.set(current);
  }

  /** Checks whether a specific row is selected. */
  isRowSelected(id: string): boolean {
    if (this.selectAllMatching()) {
      return !this.excludedItems().has(id);
    }
    return this.selectedItems().has(id);
  }

  /** Current column key used for table sorting, defaulting to the locked identity column from BFF. */
  readonly sortColumn = linkedSignal<string, string>({
    source: () => this.lockedColumns()[0] ?? '',
    computation: (source) => source,
  });

  /** Sort order direction (true = ascending, false = descending). */
  readonly sortAsc = signal<boolean>(true);

  /** Toggles sort order for the specified column (flips direction if active, or activates ascending). */
  toggleSort(colKey: string) {
    if (this.sortColumn() === colKey) {
      this.sortAsc.update((asc) => !asc);
    } else {
      this.sortColumn.set(colKey);
      this.sortAsc.set(true);
    }
  }

  /** Updates page size, resets pagination, and clears row selection. */
  override setPageSize(newSize: number) {
    super.setPageSize(newSize);
    this.clearSelection();
  }

  /** Current visible column descriptors (isomorphic support for localStorage and searchConfig defaults). */
  readonly visibleColumnDescriptors = linkedSignal<
    {cfg: FleetSearchConfig | null; entity: string; fleet: string},
    FleetColumnDescriptor[]
  >({
    source: () => ({
      cfg: this.searchConfig(),
      entity: this.entity(),
      fleet: this.fleet(),
    }),
    computation: ({cfg, entity, fleet}) => {
      const stored = getStoredVisibleColumns(entity, fleet);
      if (stored && stored.length > 0) {
        return stored;
      }
      return cfg?.columns?.defaults ?? [];
    },
  });

  /** Set of column keys currently requested/visible in the data table for search query execution. */
  readonly visibleColumns = computed<Set<string>>(() => {
    return new Set(this.visibleColumnDescriptors().map((c) => c.key));
  });

  /** Sets the visible table columns, persists them to localStorage, and re-executes search query. */
  setVisibleColumns(columns: Array<FleetColumnDescriptor | string>) {
    const defaultMap = new Map(
      (this.searchConfig()?.columns?.defaults ?? []).map((d) => [
        d.key,
        d.displayName,
      ]),
    );
    const descriptors: FleetColumnDescriptor[] = columns.map((c) => {
      if (typeof c === 'string') {
        const meta = this.getKeyMetadata(c);
        const displayName = meta?.keyDisplayName || defaultMap.get(c) || c;
        return {key: c, displayName};
      }
      return c;
    });

    const defs = this.defaultColumns();
    const matchesDefaults =
      defs.length > 0 &&
      descriptors.length === defs.length &&
      descriptors.every((d, idx) => d.key === defs[idx]);

    if (matchesDefaults) {
      this.resetVisibleColumns();
      return;
    }

    this.visibleColumnDescriptors.set(descriptors);
    saveStoredVisibleColumns(this.entity(), this.fleet(), descriptors);
    this.executeSearch();
  }

  /** Resets visible table columns to searchConfig defaults, clears custom saved preferences, and re-executes search query. */
  resetVisibleColumns() {
    clearStoredVisibleColumns(this.entity(), this.fleet());
    const defaults = this.searchConfig()?.columns?.defaults ?? [];
    this.visibleColumnDescriptors.set(defaults);
    this.executeSearch();
  }

  /** Authoritative default column keys provided by BFF search config. */
  readonly defaultColumns = computed<string[]>(() => {
    return this.searchConfig()?.columns?.defaults?.map((c) => c.key) ?? [];
  });

  /** Authoritative locked identity column keys provided by BFF search config. */
  readonly lockedColumns = computed<string[]>(() => {
    return (
      this.searchConfig()
        ?.columns?.defaults?.filter((c) => c.locked)
        .map((c) => c.key) ?? []
    );
  });

  /** Display columns to render in search results table. */
  override readonly displayColumns = computed<Column[]>(() => {
    const cols = this.searchResource.value()?.flat?.columns;
    if (cols && cols.length > 0) {
      return cols;
    }
    const descriptors = this.visibleColumnDescriptors();
    if (descriptors.length > 0) {
      return descriptors.map((c) => ({
        key: c.key,
        displayName: c.displayName,
      }));
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

  /**
   * Sorting mode for grouped view headers.
   * Automatically falls back to 'count:desc' if the active sort key is removed from groupBy.
   */
  readonly groupSort = linkedSignal<string[], string>({
    source: () => this.groupByKeys(),
    computation: (keys, previous) => {
      if (!previous) return 'count:desc';
      if (previous.value.startsWith('gb_')) {
        const sortedKey = previous.value.slice(previous.value.indexOf(':') + 1);
        if (!keys.includes(sortedKey)) {
          return 'count:desc';
        }
      }
      return previous.value;
    },
  });

  /** Map holding pagination data and loading states for expanded group headers, automatically reset when query scope changes. */
  readonly expandedGroupPages = linkedSignal<
    string,
    Map<string, {loading?: boolean; data?: FleetFlatResults; error?: string}>
  >({
    source: () => this.searchScopeKey(),
    computation: () => new Map(),
  });

  /** Grouped results object from the primary search resource. */
  readonly groupedResults = computed(
    () => this.searchResource.value()?.grouped,
  );

  /** Groups array to render in grouped view accordion. */
  readonly groups = computed<FleetGroup[]>(() => {
    return this.groupedResults()?.groups || [];
  });

  /** Formats display label text for active group-by headers (e.g. "Model × Status"). */
  readonly groupKeysText = computed(() => {
    const keys = this.searchResource.value()?.grouped?.groupByKeys || [];
    return keys.map((k: Column) => k.displayName || k.key).join(' × ');
  });

  /** Dynamic options list for sorting group header cards based on entity and active group-by chips. */
  override readonly groupSortOptions = computed<
    Array<{value: string; label: string}>
  >(() => {
    const entityLabel = this.entity() === 'hosts' ? 'hosts' : 'devices';
    const countOptions = [
      {value: 'count:desc', label: `Most ${entityLabel}`},
      {value: 'count:asc', label: `Fewest ${entityLabel}`},
    ];

    const groupByChips = this.activeChips().filter((c) => c.isGroupBy);
    if (groupByChips.length === 0) {
      return countOptions;
    }

    const gbOptions: Array<{value: string; label: string}> = [];
    for (const chip of groupByChips) {
      const rawKey = getChipKey(chip);
      const meta = this.getKeyMetadata(rawKey);
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

  /** Formats user-facing active label for group sorting button (e.g. "Most devices" or "Model (A–Z)"). */
  readonly groupSortActiveLabel = computed<string>(() => {
    const activeVal = this.groupSort();
    const options = this.groupSortOptions();
    const hit = options.find((o) => o.value === activeVal);
    return hit ? hit.label : 'Sort';
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
    const filters = this.effectiveFilters();

    const req: FleetSearchRequest = {
      entity: toSearchEntityProto(this.entity()),
      fleet: toFleetProto(this.fleet()),
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
      if (!this.isCurrentRouteActive()) return undefined;
      const key = this.pickerKey();
      const show = this.showValuePicker();
      if (!show || !key) return undefined;

      return {
        entity: this.entity(),
        key,
        activeFilters: this.effectiveFilters(),
        fleet: this.fleet(),
      };
    },
    stream: ({params: req}) => {
      if (!req) return of([]);
      const fleetReq: FleetValueListRequest = {
        entity: toSearchEntityProto(req.entity),
        key: req.key,
        filters: req.activeFilters.length > 0 ? req.activeFilters : undefined,
        fleet: toFleetProto(req.fleet),
      };
      return this.searchService.getFleetValueList(fleetReq).pipe(
        map((res) => {
          if (res.counted) {
            const list: PickerValueItem[] = (res.counted.values || []).map(
              (v) => ({
                value: v.value,
                displayLabel: v.displayLabel || v.value,
                filtered: v.filtered ?? 0,
                total: v.total ?? 0,
              }),
            );
            if (res.counted.noValueEntry) {
              list.push({
                value: EMPTY_FILTER_VALUE,
                displayLabel: '(no value)',
                filtered: res.counted.noValueEntry.filtered ?? 0,
                total: res.counted.noValueEntry.total ?? 0,
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
                value: EMPTY_FILTER_VALUE,
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

  /** Effective ValuePicker state combining base selections with async loaded values and loading status. */
  override readonly effectivePickerState = computed<ValuePickerState>(() => {
    const raw = this.pickerState();
    const asyncVals = this.valueListResource.value();
    return {
      ...raw,
      loading: raw.loading || this.valueListResource.isLoading(),
      values: asyncVals && asyncVals.length > 0 ? asyncVals : raw.values,
    };
  });

  // ===========================================================================
  // 6. Primary Search Resource & Execution
  // ===========================================================================

  /** Primary reactive resource fetching flat or grouped fleet search results. */
  readonly searchResource = rxResource<
    FleetSearchResults | null,
    FleetSearchRequest | undefined
  >({
    params: () => {
      if (!this.isCurrentRouteActive()) return undefined;
      if (this.isLandingState()) return undefined;
      // Wait for searchConfig to load default columns on clean page entry to prevent sending empty columns
      if (this.isConfigLoading() && this.visibleColumns().size === 0) {
        return undefined;
      }

      const fleet = this.fleet();
      const pageSize = this.pageSize();
      const pageToken = this.pageToken();

      const filters = this.effectiveFilters();
      const groupByKeys = this.groupByKeys();

      const req: FleetSearchRequest = {
        entity: toSearchEntityProto(this.entity()),
        fleet: toFleetProto(fleet),
        filters: filters.length > 0 ? filters : undefined,
      };

      if (groupByKeys.length > 0) {
        const groupSort = this.groupSort();
        req.groupHeader = {
          groupBy: groupByKeys,
          sort: buildFleetGroupSort(groupSort),
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

  /** Array of row IDs visible on the current flat search results page. */
  readonly visibleRowIds = computed<string[]>(() => {
    return this.rows().map((r) => r.id);
  });

  /** Checks whether all visible rows on current flat results page are selected. */
  readonly isAllSelected = computed<boolean>(() => {
    if (this.selectAllMatching()) {
      return this.excludedItems().size === 0;
    }
    const visibleIds = this.visibleRowIds();
    return (
      visibleIds.length > 0 &&
      visibleIds.every((id) => this.selectedItems().has(id))
    );
  });

  /** Checks whether some (but not all) visible rows on current flat results page are selected. */
  readonly isSomeSelected = computed<boolean>(() => {
    if (this.selectAllMatching()) {
      const excludedCount = this.excludedCount();
      return excludedCount > 0 && excludedCount < this.effectiveTotalCount();
    }
    const visibleIds = this.visibleRowIds();
    const len = visibleIds.length;
    if (len === 0) return false;
    const selected = this.selectedItems();
    let count = 0;
    for (const id of visibleIds) {
      if (selected.has(id)) count++;
    }
    return count > 0 && count < len;
  });

  /** Whether to show the select-all-matching banner across pages. */
  readonly showSelectAllMatchingBanner = computed<boolean>(() => {
    if (this.groupByKeys().length > 0) return false;
    if (this.selectAllMatching()) return true;
    return (
      this.isAllSelected() &&
      this.effectiveTotalCount() > this.visibleRowIds().length
    );
  });

  /** Determines whether a next page is available based on API nextPageToken. */
  override readonly hasNextPage = computed<boolean>(() => {
    return !!this.getPaginationToken('next');
  });

  /** Determines whether a previous page is available based on API prevPageToken or pageIndex. */
  override readonly hasPrevPage = computed<boolean>(() => {
    return this.groupByKeys().length > 0
      ? !!this.getPaginationToken('prev')
      : this.pageIndex() > 0;
  });

  /** Unified pre-formatted semantic range text for pagination footer display across flat and grouped modes. */
  override readonly rangeText = computed<string>(() => {
    if (this.groupByKeys().length > 0) {
      const res = this.groupedResults();
      if (!res) return '';
      const start = res.rangeStart || 0;
      const end = res.rangeEnd || 0;
      const total = (res.totalGroups || 0).toLocaleString();
      return `${start}–${end} of ${total} groups`;
    }
    const res = this.searchResource.value()?.flat;
    if (!res) return '';
    const start = res.rangeStart || 0;
    const end = res.rangeEnd || 0;
    const total = res.total || 0;
    if (start > 0 && end > 0 && total > 0) {
      return `${start.toLocaleString()} – ${end.toLocaleString()} of ${total.toLocaleString()}`;
    }
    if (start > 0 && end > 0) {
      return `showing ${start.toLocaleString()}–${end.toLocaleString()}`;
    }
    return '';
  });

  /** Total matching count across flat or grouped search. */
  readonly effectiveTotalCount = computed(() => {
    return (
      (this.groupByKeys().length > 0
        ? this.groupedResults()?.totalItems
        : this.searchResource.value()?.flat?.total) || 0
    );
  });

  /** Memoized effective filter list guarding downstream search resources from display-only chip updates. */
  readonly effectiveFilters = computed<Filter[]>(
    () =>
      this.activeChips()
        .filter((c) => !c.isGroupBy)
        .map((c) => buildFleetFilterFromChip(c)),
    {
      equal: (a, b) => {
        if (a === b) return true;
        if (a.length !== b.length) return false;
        return JSON.stringify(a) === JSON.stringify(b);
      },
    },
  );

  /** Reloads the primary search resource and resets page token and group expansions. */
  override executeSearch() {
    this.pageIndex.set(0);
    this.pageToken.set('');
    this.openGroupIds.set(new Set());
    this.expandedGroupPages.set(new Map());
    this.clearSelection();
    this.searchResource.reload();
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
    if (token !== undefined || this.pageIndex() === 1) {
      this.pageToken.set(token || '');
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
    parsedFilters: FilterChip[],
    groupByKeys: string[],
  ): Observable<FilterChip[]> {
    const filterPairs: Array<FilterPair<Filter>> = [];
    for (const chip of parsedFilters) {
      const filter = buildFleetFilterFromChip(chip);
      if (filter) {
        filterPairs.push({chip, filter});
      }
    }

    const req: FleetChipResolverRequest = {
      filters:
        filterPairs.length > 0 ? filterPairs.map((p) => p.filter) : undefined,
      groupByKeys: groupByKeys.length > 0 ? groupByKeys : undefined,
      entity: toSearchEntityProto(this.entity()),
      fleet: toFleetProto(this.fleet()),
    };

    return this.searchService.resolveFleetChips(req).pipe(
      map((res) => {
        // Scenarios 1, 2, 5: Backend-resolved filters only; invalid or un-returned are dropped with warning
        const updatedFilterChips = buildResolvedFilterChips(
          filterPairs,
          res.filterChips,
          (chip, resolved) => {
            if (resolved.invalid?.reason) {
              this.snackBarService.showWarning(resolved.invalid.reason);
            }
            if (resolved.invalid || !resolved.valid) {
              return null;
            }
            const valid = resolved.valid;
            return {
              ...chip,
              pillKey: valid.pillKey || chip.pillKey,
              pillCondition: valid.pillCondition || chip.pillCondition,
              metadata: valid.metadata || chip.metadata,
            };
          },
        );

        // Scenario 6: Group-by chips are resolved and appended cleanly; invalid keys show warning
        const updatedGroupByChips: FilterChip[] = [];
        groupByKeys.forEach((gb, idx) => {
          const gbc = res.groupByChips?.[idx];
          if (gbc?.invalid?.reason) {
            this.snackBarService.showWarning(gbc.invalid.reason);
          }
          if (gbc?.invalid) {
            return;
          }
          if (gbc?.valid) {
            updatedGroupByChips.push(
              createGroupByChip(gb, gbc.valid.displayName || gbc.valid.pillKey),
            );
            return;
          }
          updatedGroupByChips.push(createGroupByChip(gb));
        });

        return [...updatedFilterChips, ...updatedGroupByChips];
      }),
      // TODO: Uniformly handle backend RPC error / 500 / offline display in search UI.
      catchError(() => of([])),
    );
  }

  /** Constructs Fleet-specific ValuePicker configuration. */
  protected override buildPickerConfig(
    key: string,
    title?: string,
    metadata?: unknown,
  ): ValuePickerConfig {
    const meta =
      (metadata as FleetFilterChipMetadata) || this.getKeyMetadata(key);
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
    stagedValues?: string[],
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
        isNegated = false;
      } else {
        isNegated = isChipNegated(activeChip);
        if (activeChip.fleetFilter?.simple?.values) {
          const vals = activeChip.fleetFilter.simple.values
            .map((v) => v.value || (v.noValue ? EMPTY_FILTER_VALUE : ''))
            .filter(Boolean);
          selectedVals = new Set(vals);
        } else if (activeChip.rawValues?.length) {
          selectedVals = new Set(activeChip.rawValues);
        }
      }
    }

    if (stagedValues && stagedValues.length > 0) {
      for (const sv of stagedValues) {
        selectedVals.add(sv);
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
    const meta = key ? this.getKeyMetadata(key) : undefined;
    const displayTitle = meta?.keyDisplayName || title;
    this.closeValuePicker();

    if (isValuePickerSelectionEmpty(event)) {
      this.removeChipForKey(key);
      return;
    }

    let rawValues: string[] | undefined = event.selected;
    let complex: ComplexMatch | undefined;
    let isNegated = !!event.negate;

    if (event.isAdvanced) {
      complex = buildComplexMatchFromEvent(event);
      const info = extractComplexMatchInfo(complex);
      rawValues = info?.values;
      isNegated = !!info?.isNegated;
    }

    const filter: Filter = complex
      ? {key, complex}
      : buildSimpleFleetFilter(key, rawValues || [], isNegated);

    const req: FleetChipResolverRequest = {
      filters: [filter],
      entity: toSearchEntityProto(this.entity()),
      fleet: toFleetProto(this.fleet()),
    };

    this.searchService
      .resolveFleetChips(req)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          if (!res.filterChips || res.filterChips.length === 0) {
            return;
          }
          for (const fc of res.filterChips) {
            if (fc.invalid?.reason) {
              this.snackBarService.showWarning(fc.invalid.reason);
            }
            if (fc.invalid || !fc.valid) {
              continue;
            }
            this.addFilterChip({
              key,
              pillKey: fc.valid.pillKey || displayTitle,
              pillCondition: fc.valid.pillCondition,
              isGroupBy: false,
              metadata: fc.valid.metadata || meta,
              rawValues,
              negated: isNegated,
              complex: event.isAdvanced ? complex : undefined,
              fleetFilter: filter,
            });
          }
        },
        // TODO: Uniformly handle backend RPC error / 500 / offline display in search UI.
        error: () => {},
      });
  }

  // ===========================================================================
  // 9. Suggestion Selection & State Reset
  // ===========================================================================

  /** Unpacks FleetSuggestion payload to open value picker or apply filter/group-by chips. */
  protected override applySuggestion(
    rawItem: unknown,
    anchor?: HTMLElement | null,
  ) {
    const raw = rawItem as FleetSuggestion;
    if (!raw) return;

    if (raw.openPicker) {
      const op = raw.openPicker;
      const displayTitle = op.metadata?.keyDisplayName || op.key;
      this.openValuePicker(
        op.key,
        anchor || null,
        displayTitle,
        op.metadata,
        op.stagedModify?.values,
      );
      return;
    }

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
      this.addFilterChip(createGroupByChip(actionKey, pillTitle));
    }
  }

  /** Resets search state including group view expand states and selected items. */
  override resetSearchState(updateUrl = true) {
    super.resetSearchState(updateUrl);

    this.openGroupIds.set(new Set());
    this.expandedGroupPages.set(new Map());
    this.clearSelection();
  }
}
