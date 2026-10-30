import {
  computed,
  inject,
  Injectable,
  linkedSignal,
  signal,
} from '@angular/core';
import {rxResource, takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {EMPTY, Observable, of, Subject} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';

import {
  Column,
  ComplexMatch,
  Filter,
  Fleet,
  FleetChipResolverRequest,
  FleetColumnDescriptor,
  FleetGroup,
  FleetPromotedKeysRequest,
  FleetResolvedGroupByChip,
  FleetSearchConfig,
  FleetSearchConfigRequest,
  FleetSearchRequest,
  FleetSearchResults,
  FleetSuggestion,
  FleetSuggestionRequest,
  FleetValueListRequest,
  Row,
  TranslateArsenalSearchResponse,
} from '../../../core/models/search';
import {SnackBarService} from '../../../shared/services/snackbar_service';
import {
  AdvancedMatchMode,
  EntityType,
  FilterChip,
  FilterKeyMetadata,
  FilterPair,
  FleetScope,
  GroupPageState,
  PickerValueItem,
  PromotedFilterKeyItem,
  PromotedGroupByKeyItem,
  SearchBoxSuggestion,
  ValuePickerApplyEvent,
  ValuePickerConfig,
  ValuePickerState,
} from '../models';
import {
  areFilterArraysEqual,
  buildComplexMatchFromEvent,
  buildFilterChipFromResolved,
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
  parseArsenalQueryParam,
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

  private static readonly LEGACY_QUERY_PARAM_KEYS = ['arsenal_query'] as const;

  private readonly translateArsenalSubject = new Subject<{
    arsenalKey: string;
    unpacked: ReturnType<typeof parseArsenalQueryParam>;
  } | null>();

  constructor() {
    super();

    this.translateArsenalSubject
      .pipe(
        switchMap((req) => {
          if (!req) {
            return EMPTY;
          }
          const {arsenalKey, unpacked} = req;
          return this.searchService
            .translateArsenalSearch({
              entity: toSearchEntityProto(this.entity()),
              filters:
                unpacked.filters.length > 0 ? unpacked.filters : undefined,
              columns:
                unpacked.columns.length > 0 ? unpacked.columns : undefined,
              groupByKeys:
                unpacked.groupByKeys.length > 0
                  ? unpacked.groupByKeys
                  : undefined,
            })
            .pipe(
              map((res) => ({res, arsenalKey, unpacked})),
              catchError(() => {
                if (
                  this.lastSyncedUrlKey === arsenalKey &&
                  this.isCurrentRouteActive()
                ) {
                  this.snackBarService.showWarning(
                    'Could not translate legacy Arsenal query.',
                  );
                }
                return EMPTY;
              }),
            );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({res, arsenalKey, unpacked}) => {
        if (
          this.lastSyncedUrlKey !== arsenalKey ||
          !this.isCurrentRouteActive()
        ) {
          return;
        }
        this.applyArsenalTranslation(res, unpacked);
      });
  }

  protected override getLegacyQueryParamKeys(): readonly string[] {
    return FleetSearchStore.LEGACY_QUERY_PARAM_KEYS;
  }

  protected override cancelPendingLegacyTranslation() {
    this.translateArsenalSubject.next(null);
  }

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
      const input = this.getActiveSuggestionInput();
      if (input === null) return undefined;

      return {
        entity: this.entity(),
        input,
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

  /** Autocomplete suggestions consumed directly from the BFF response without transformation. */
  override readonly suggestions = computed<SearchBoxSuggestion[]>(
    () => this.suggestionsResource.value() ?? [],
  );

  /** Whether autocomplete suggestions are currently being fetched or debounced. */
  override readonly isSuggestionsLoading = computed<boolean>(() =>
    this.computeSuggestionsLoading(this.suggestionsResource.isLoading()),
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

  /** Reactive resource fetching promoted filter keys and promoted group-by keys once on page entry. */
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
          filterKeys: (res.filterKeys || []).map((pk) => ({
            ...pk,
            displayName: pk.metadata?.keyDisplayName || pk.key,
          })),
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
      const nextMap = new Map<string, FilterKeyMetadata>();
      for (const chip of this.activeChips()) {
        const chipKey = getChipKey(chip);
        if (chipKey && chip.metadata) {
          nextMap.set(chipKey, chip.metadata);
        }
      }
      const pending = this.pendingFilter();
      if (pending?.metadata) {
        nextMap.set(pending.key, pending.metadata);
      }
      const res = this.promotedKeysResource.value();
      for (const pk of res?.filterKeys ?? []) {
        if (pk.metadata) {
          nextMap.set(pk.key, pk.metadata);
        }
      }
      for (const gk of res?.groupByKeys ?? []) {
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

  /** Clears the current selection state. */
  clearSelection() {
    this.selectedItems.set(new Set());
  }

  /** Toggles individual row selection state. */
  toggleSelectRow(id: string) {
    const nextSet = new Set(this.selectedItems());
    if (nextSet.has(id)) {
      nextSet.delete(id);
    } else {
      nextSet.add(id);
    }
    this.selectedItems.set(nextSet);
  }

  /** Toggles selection for a list of rows (e.g. current flat page or an expanded group card page). */
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
  setVisibleColumns(columns: readonly FleetColumnDescriptor[]) {
    const descriptors = [...columns];
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
        ...(c.description ? {description: c.description} : {}),
      }));
    }
    return [];
  });

  /**
   * Descriptions of the column keys that have one, for the table header tooltips. Seeds saved
   * visible column descriptors first so live key metadata, BFF default column descriptors, and
   * flat or grouped result columns override any cached localStorage text for the same key.
   */
  readonly columnDescriptions = computed<ReadonlyMap<string, string>>(() => {
    const descriptions = new Map<string, string>();
    for (const descriptor of this.visibleColumnDescriptors()) {
      if (descriptor.description) {
        descriptions.set(descriptor.key, descriptor.description);
      }
    }
    for (const [key, meta] of this.keyMetadataMap()) {
      if (meta.description) {
        descriptions.set(key, meta.description);
      }
    }
    const liveDescriptors = [
      ...(this.searchConfig()?.columns?.defaults ?? []),
      ...(this.searchResource.value()?.flat?.columns ?? []),
      ...(this.searchResource.value()?.grouped?.groupByKeys ?? []),
    ];
    for (const descriptor of liveDescriptors) {
      if (descriptor.description) {
        descriptions.set(descriptor.key, descriptor.description);
      }
    }
    return descriptions;
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
    Map<string, GroupPageState>
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
  private updateGroupExpandState(groupId: string, patch: GroupPageState) {
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

  /** Checks whether all visible rows on current flat results page are selected. */
  readonly isAllSelected = computed<boolean>(() => {
    const rows = this.rows();
    if (rows.length === 0) return false;
    const selected = this.selectedItems();
    return rows.every((r) => selected.has(r.id));
  });

  /** Checks whether some (but not all) visible rows on current flat results page are selected. */
  readonly isSomeSelected = computed<boolean>(() => {
    const rows = this.rows();
    if (rows.length === 0) return false;
    const selected = this.selectedItems();
    const count = rows.filter((r) => selected.has(r.id)).length;
    return count > 0 && count < rows.length;
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
  override readonly effectiveTotalCount = computed<number>(() => {
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
    {equal: areFilterArraysEqual},
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

        const updatedGroupByChips = this.resolveGroupByChips(
          groupByKeys,
          res.groupByChips,
        );

        return [...updatedFilterChips, ...updatedGroupByChips];
      }),
      // TODO: Uniformly handle backend RPC error / 500 / offline display in search UI.
      catchError(() => of([])),
    );
  }

  /** Intercepts ?arsenal_query=... on cold load, delegates translation to BFF, and rewrites URL in-place. */
  protected override handleLegacyQueryParams(
    params: Record<string, unknown>,
  ): boolean {
    const rawArsenalQuery =
      typeof params['arsenal_query'] === 'string'
        ? params['arsenal_query'].trim()
        : '';
    if (!rawArsenalQuery) {
      return false;
    }

    const arsenalKey = `arsenal:${rawArsenalQuery}`;
    if (arsenalKey === this.lastSyncedUrlKey) {
      return true;
    }
    this.lastSyncedUrlKey = arsenalKey;
    this.cancelPendingChipResolution();

    const unpacked = parseArsenalQueryParam(rawArsenalQuery);
    this.translateArsenalSubject.next({arsenalKey, unpacked});

    return true;
  }

  private applyArsenalTranslation(
    res: TranslateArsenalSearchResponse,
    unpacked: ReturnType<typeof parseArsenalQueryParam>,
  ) {
    const targetFleet: FleetScope =
      res.fleet === Fleet.FLEET_ATS ? 'ats' : 'internal';
    if (this.fleet() !== targetFleet) {
      this.fleet.set(targetFleet);
    }

    if (unpacked.columns.length > 0 && res.columns && res.columns.length > 0) {
      this.visibleColumnDescriptors.set(res.columns);
      saveStoredVisibleColumns(this.entity(), targetFleet, res.columns);
    }

    const filters = res.filters || [];
    const filterChips = res.filterChips || [];
    const resolvedFilterChips: FilterChip[] = [];
    for (let i = 0; i < filters.length; i++) {
      const fc = filterChips[i];
      if (fc?.invalid?.reason) {
        this.snackBarService.showWarning(fc.invalid.reason);
      }
      if (fc?.valid) {
        resolvedFilterChips.push(
          buildFilterChipFromResolved(filters[i], fc.valid),
        );
      }
    }

    const resolvedGroupByChips = this.resolveGroupByChips(
      res.groupByKeys || [],
      res.groupByChips,
    );

    const allChips = [...resolvedFilterChips, ...resolvedGroupByChips];
    const hadTranslatedCriteria =
      filters.length > 0 || (res.groupByKeys?.length ?? 0) > 0;
    const shouldBrowseAll = allChips.length === 0 && !hadTranslatedCriteria;
    this.browseAll.set(shouldBrowseAll);
    this.activeChips.set(allChips);
    this.syncUrl(allChips, targetFleet, /* replaceUrl= */ true);
    if (allChips.length > 0 || shouldBrowseAll) {
      this.executeSearch();
    }
  }

  /** Scenario 6: Resolves group-by keys against backend-validated chips, dropping invalid keys with a warning. */
  private resolveGroupByChips(
    groupByKeys: string[],
    groupByChips: FleetResolvedGroupByChip[] | undefined,
  ): FilterChip[] {
    const resolved: FilterChip[] = [];
    for (let i = 0; i < groupByKeys.length; i++) {
      const gbKey = groupByKeys[i];
      const gbc = groupByChips?.[i];
      if (gbc?.invalid?.reason) {
        this.snackBarService.showWarning(gbc.invalid.reason);
      }
      if (gbc?.invalid) {
        continue;
      }
      resolved.push(
        createGroupByChip(
          gbKey,
          gbc?.valid?.displayName || gbc?.valid?.pillKey || gbKey,
        ),
      );
    }
    return resolved;
  }

  /** Constructs Fleet-specific ValuePicker configuration. */
  protected override buildPickerConfig(
    key: string,
    title?: string,
    metadata?: FilterKeyMetadata,
  ): ValuePickerConfig {
    const meta = metadata || this.getKeyMetadata(key);
    const displayTitle = meta?.keyDisplayName || title || key;

    return {
      key,
      type: 'list',
      title: displayTitle,
      description:
        meta?.description || this.columnDescriptions().get(key) || undefined,
      canUseAdvanced: !!meta?.canUseAdvanced,
      isPlural: !!meta?.isPlural,
    };
  }

  /** Constructs initial ValuePicker interaction state from an active filter chip. */
  protected override buildInitialPickerState(
    key: string,
    activeChip?: FilterChip,
    metadata?: FilterKeyMetadata,
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
    this.showSuggestions.set(!this.autoCollapseAfterApply());

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
  protected override applySuggestion(rawItem: FleetSuggestion) {
    if (!rawItem) return;

    if (rawItem.openPicker) {
      const op = rawItem.openPicker;
      const displayTitle = op.metadata?.keyDisplayName || op.key;
      this.openQuickFilter(
        op.key,
        displayTitle,
        op.metadata,
        op.stagedModify?.values,
      );
      return;
    }

    this.applyFleetSuggestionDirectly(rawItem);
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

  /** Resets transient UI state including group view expand states and selected items. */
  protected override resetTransientUiState() {
    super.resetTransientUiState();

    this.openGroupIds.set(new Set());
    this.expandedGroupPages.set(new Map());
    this.clearSelection();
  }
}
