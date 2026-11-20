/**
 * @fileoverview Reactive store managing Fleet (Devices & Hosts) BFF search resources, grouping, column customization, pagination, and row selection.
 */

import {
  computed,
  inject,
  Injectable,
  linkedSignal,
  signal,
} from '@angular/core';
import {rxResource, takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Observable, of, throwError} from 'rxjs';
import {catchError, map} from 'rxjs/operators';

import {
  Column,
  Filter,
  FleetGroup,
  FleetSearchConfig,
  FleetSearchRequest,
  FleetSearchResults,
  FleetSuggestion,
  Row,
} from '../../../core/models/search';
import {SnackBarService} from '../../../shared/services/snackbar_service';
import {getErrorMessage} from '../../../shared/utils/error_utils';
import {
  DEFAULT_PAGE_SIZE,
  EntityType,
  FilterChip,
  FilterKeyMetadata,
  PromotedFilterKeyItem,
  PromotedGroupByKeyItem,
  SearchBoxSuggestion,
  ValuePickerApplyEvent,
  ValuePickerConfig,
  ValuePickerState,
} from '../models';
import {
  areFilterArraysEqual,
  buildFleetChipResolverInput,
  buildFleetFilterFromChip,
  buildFleetFilterFromPickerEvent,
  buildFleetGroupSort,
  buildFleetInitialPickerState,
  buildFleetKeyMetadataMap,
  buildFleetPickerConfig,
  computeFleetRangeText,
  extractAppliedFleetPickerChips,
  extractChipsFromFleetSuggestion,
  getStoredPageSize,
  isValuePickerSelectionEmpty,
  mapFleetPromotedKeysResponse,
  mapFleetResolvedChipsResponse,
  mapFleetValueListResponse,
  saveStoredPageSize,
  toFleetProto,
  toSearchEntityProto,
} from '../utils';
import {SearchPageStore} from './search_page_store';
import {
  useArsenalTranslator,
  useGroupAccordion,
  useTableSelection,
  useVisibleColumns,
} from './slices';

/** State store managing search, grouping, pagination, and selection for Fleet entities (Devices & Hosts). */
@Injectable()
export class FleetSearchStore extends SearchPageStore<FleetSuggestion, Filter> {
  private static readonly SUPPORTED_ENTITIES = new Set<EntityType>([
    'devices',
    'hosts',
  ]);
  private static readonly LEGACY_QUERY_PARAM_KEYS = ['arsenal_query'] as const;

  override readonly supportsFleetScope = true;
  protected override readonly supportsLandingPage = true;

  private readonly snackBarService = inject(SnackBarService);

  constructor() {
    super();
    // The entity is fixed for the lifetime of this store instance, so the saved
    // "Rows per page" of the flat table can be restored once up front.
    const storedPageSize = getStoredPageSize(this.entity());
    if (storedPageSize !== null) {
      this.pageSize.set(storedPageSize);
    }
  }

  /** Returns `'devices'` as the default Fleet entity type. */
  override getDefaultEntity(): EntityType {
    return 'devices';
  }

  /** Returns the supported Fleet entity types (`'devices'` and `'hosts'`). */
  override getSupportedEntities(): Set<EntityType> {
    return FleetSearchStore.SUPPORTED_ENTITIES;
  }

  /** Returns legacy query parameter keys (`'arsenal_query'`) handled by Fleet search. */
  override getLegacyQueryParamKeys(): readonly string[] {
    return FleetSearchStore.LEGACY_QUERY_PARAM_KEYS;
  }

  /** Memoized protobuf `entity` and `fleet` scope fields for Fleet BFF requests. */
  private readonly baseScope = computed(
    () => ({
      entity: toSearchEntityProto(this.entity()),
      fleet: toFleetProto(this.fleet()),
    }),
    {equal: (a, b) => a.entity === b.entity && a.fleet === b.fleet},
  );

  override readonly searchPlaceholder = computed(() =>
    this.entity() === 'hosts'
      ? 'Type a host name, or "status is", or a host ID'
      : 'Type "pixel 10", or "model is", or a device ID',
  );

  readonly effectiveFilters = computed<Filter[]>(
    () =>
      this.activeChips()
        .filter((c) => !c.isGroupBy)
        .map((c) => buildFleetFilterFromChip(c)),
    {equal: areFilterArraysEqual},
  );

  private readonly suggestionsResource = rxResource({
    params: () => {
      const input = this.getActiveSuggestionInput();
      if (input === null) return undefined;
      const filters = this.effectiveFilters();
      const groupBy = this.groupByKeys();
      return {
        ...this.baseScope(),
        input,
        filters: filters.length > 0 ? filters : undefined,
        groupBy: groupBy.length > 0 ? groupBy : undefined,
        limit: 10,
      };
    },
    stream: ({params: req}) =>
      req
        ? this.searchService.getFleetSuggestions(req).pipe(
            map((res) => res.items || []),
            catchError(() => of([])),
          )
        : of([]),
  });

  override readonly suggestions = computed<SearchBoxSuggestion[]>(
    () => this.suggestionsResource.value() ?? [],
  );
  override readonly isSuggestionsLoading = this.computeSuggestionsLoading(
    this.suggestionsResource.isLoading,
  );

  private readonly searchConfigResource = rxResource({
    params: () => (this.isCurrentRouteActive() ? this.baseScope() : undefined),
    stream: ({params: req}) =>
      req
        ? this.searchService
            .getFleetSearchConfig(req)
            .pipe(catchError(() => of(null)))
        : of(null),
  });

  override readonly searchConfig = computed<FleetSearchConfig | null>(
    () => this.searchConfigResource.value() || null,
  );
  override readonly isConfigLoading = this.searchConfigResource.isLoading;

  private readonly promotedKeysResource = rxResource({
    params: () => {
      if (!this.isCurrentRouteActive()) return undefined;
      const groupBy = this.groupByKeys();
      const filters = this.effectiveFilters();
      return {
        ...this.baseScope(),
        groupBy: groupBy.length > 0 ? groupBy : undefined,
        filters: filters.length > 0 ? filters : undefined,
      };
    },
    stream: ({params: req}) =>
      req
        ? this.searchService.getFleetPromotedKeys(req).pipe(
            map(mapFleetPromotedKeysResponse),
            catchError(() => of({filterKeys: [], groupByKeys: []})),
          )
        : of({filterKeys: [], groupByKeys: []}),
  });

  override readonly promotedFilterKeys = computed<PromotedFilterKeyItem[]>(
    () => this.promotedKeysResource.value()?.filterKeys || [],
  );
  override readonly promotedGroupByKeys = computed<PromotedGroupByKeyItem[]>(
    () => this.promotedKeysResource.value()?.groupByKeys || [],
  );
  override readonly isPromotedKeysLoading = this.promotedKeysResource.isLoading;
  override readonly keyMetadataMap = computed<Map<string, FilterKeyMetadata>>(
    () =>
      buildFleetKeyMetadataMap(
        this.activeChips(),
        this.pendingFilter(),
        this.promotedKeysResource.value(),
      ),
  );

  private readonly columnsSlice = useVisibleColumns(this);
  readonly visibleColumnDescriptors =
    this.columnsSlice.visibleColumnDescriptors;
  readonly visibleColumns = this.columnsSlice.visibleColumns;
  override readonly displayColumns = this.columnsSlice.displayColumns;
  readonly columnDescriptions = this.columnsSlice.columnDescriptions;
  readonly setVisibleColumns = this.columnsSlice.setVisibleColumns;
  readonly resetVisibleColumns = this.columnsSlice.resetVisibleColumns;

  readonly sortColumn = linkedSignal<string, string>({
    source: () =>
      this.searchConfig()?.columns?.defaults?.find((c) => c.locked)?.key ?? '',
    computation: (source) => source,
  });
  readonly sortAsc = signal<boolean>(true);

  /** Clears selection, resets sort if the sorted column was removed, and refreshes open groups or flat pagination. */
  onVisibleColumnsChanged(): void {
    const currentSort = this.sortColumn();
    if (currentSort && !this.visibleColumns().has(currentSort)) {
      const defaultSort =
        this.searchConfig()?.columns?.defaults?.find((c) => c.locked)?.key ??
        this.visibleColumnDescriptors().find((c) => c.locked)?.key ??
        '';
      this.sortColumn.set(defaultSort);
      this.sortAsc.set(true);
    }
    this.clearSelection();
    if (this.groupByKeys().length > 0) {
      this.groupAccordion.reloadOpenGroupsForColumnChange();
    } else {
      this.resetPagination();
    }
  }

  private readonly groupAccordion = useGroupAccordion(this);
  readonly openGroupIds = this.groupAccordion.openGroupIds;
  readonly focusedGroupId = this.groupAccordion.focusedGroupId;
  readonly groupSort = this.groupAccordion.groupSort;
  readonly expandedGroupPages = this.groupAccordion.expandedGroupPages;
  override readonly groupSortOptions = this.groupAccordion.groupSortOptions;
  readonly groupSortActiveLabel = this.groupAccordion.groupSortActiveLabel;
  readonly setGroupSort = this.groupAccordion.setGroupSort;
  readonly toggleGroup = this.groupAccordion.toggleGroup;
  readonly toggleGroupRowSort = this.groupAccordion.toggleGroupRowSort;
  readonly setGroupPageSize = this.groupAccordion.setGroupPageSize;
  readonly onLoadGroupRows = this.groupAccordion.onLoadGroupRows;

  private readonly selectionScopeKey = computed(
    () =>
      `${this.groupAccordion.searchScopeKey()}:${this.sortColumn()}:${this.sortAsc()}:${this.pageIndex()}:${this.pageToken()}:${this.pageSize()}:${this.groupHeaderPageSize()}`,
  );
  private readonly selectionSlice = useTableSelection(
    this.selectionScopeKey,
    computed(() => this.rows()),
  );
  readonly selectedItems = this.selectionSlice.selectedItems;
  readonly selectionState = this.selectionSlice.selectionState;
  readonly clearSelection = this.selectionSlice.clearSelection;
  readonly deselectRows = this.selectionSlice.deselectRows;
  readonly toggleSelectRows = this.selectionSlice.toggleSelectRows;

  private readonly arsenalTranslator = useArsenalTranslator(this);
  override readonly cancelPendingLegacyTranslation =
    this.arsenalTranslator.cancelPendingLegacyTranslation;
  override readonly handleLegacyQueryParams =
    this.arsenalTranslator.handleLegacyQueryParams;

  /** Toggles ascending/descending sort direction or switches the active sort column for the flat table. */
  toggleSort(colKey: string): void {
    if (this.sortColumn() === colKey) {
      this.sortAsc.update((asc) => !asc);
    } else {
      this.sortColumn.set(colKey);
      this.sortAsc.set(true);
    }
    this.resetPagination();
  }

  /**
   * Updates the flat table's rows per page, persists it per entity, resets pagination, and clears
   * expanded groups.
   */
  override setPageSize(newSize: number): void {
    if (this.pageSize() === newSize) return;
    saveStoredPageSize(this.entity(), newSize);
    super.setPageSize(newSize);
    this.groupAccordion.resetExpandedGroups();
  }

  /**
   * Groups per page of the grouped header list. Kept apart from the flat table's `pageSize` (which
   * is persisted and goes up to 5000 rows) so a large saved rows-per-page never turns into
   * thousands of group cards; the grouped footer keeps its own small, session-only choice.
   */
  readonly groupHeaderPageSize = signal<number>(DEFAULT_PAGE_SIZE);

  /** Updates groups per page, resets pagination, and clears row selection and expanded groups. */
  setGroupHeaderPageSize(newSize: number): void {
    if (this.groupHeaderPageSize() === newSize) return;
    this.groupHeaderPageSize.set(newSize);
    this.resetPagination();
    this.groupAccordion.resetExpandedGroups();
  }

  private readonly valueListResource = rxResource({
    params: () => {
      const key = this.pickerKey();
      if (!this.isCurrentRouteActive() || !this.showValuePicker() || !key) {
        return undefined;
      }
      const filters = this.effectiveFilters();
      return {
        ...this.baseScope(),
        key,
        filters: filters.length > 0 ? filters : undefined,
      };
    },
    stream: ({params: req}) =>
      req
        ? this.searchService.getFleetValueList(req).pipe(
            map(mapFleetValueListResponse),
            catchError(() => of([])),
          )
        : of([]),
  });

  override readonly effectivePickerState = computed<ValuePickerState>(() => {
    const raw = this.pickerState();
    const asyncVals = this.valueListResource.value();
    return {
      ...raw,
      loading: raw.loading || this.valueListResource.isLoading(),
      values: asyncVals?.length ? asyncVals : raw.values,
    };
  });

  readonly searchResource = rxResource({
    params: (): FleetSearchRequest | undefined => {
      if (!this.isCurrentRouteActive() || this.isLandingState()) {
        return undefined;
      }
      const pageToken = this.pageToken() || undefined;
      const filters = this.effectiveFilters();
      const groupBy = this.groupByKeys();
      const base: FleetSearchRequest = {
        ...this.baseScope(),
        filters: filters.length > 0 ? filters : undefined,
      };
      if (groupBy.length > 0) {
        return {
          ...base,
          groupHeader: {
            groupBy,
            sort: buildFleetGroupSort(this.groupSort()),
            page: {pageSize: this.groupHeaderPageSize(), pageToken},
          },
        };
      }
      if (
        this.isConfigLoading() &&
        (this.visibleColumns().size === 0 || !this.sortColumn())
      ) {
        return undefined;
      }
      return {
        ...base,
        flat: {
          columns: Array.from(this.visibleColumns()),
          sort: {key: this.sortColumn(), ascending: this.sortAsc()},
          page: {pageSize: this.pageSize(), pageToken},
        },
      };
    },
    stream: ({params: req}) =>
      req
        ? this.searchService
            .searchFleet(req)
            .pipe(
              catchError((err: unknown) =>
                this.columnsSlice.recoverFromUnknownColumnKey(err)
                  ? of(null)
                  : throwError(() => err),
              ),
            )
        : of(null),
  });

  readonly searchResults = computed<FleetSearchResults | null>(() =>
    this.searchResource.hasValue()
      ? (this.searchResource.value() ?? null)
      : null,
  );
  readonly searchError = computed<string | null>(() => {
    const err = this.searchResource.error();
    return err ? getErrorMessage(err) : null;
  });
  readonly groupedResults = computed(() => this.searchResults()?.grouped);
  readonly groups = computed<FleetGroup[]>(
    () => this.groupedResults()?.groups || [],
  );
  readonly groupKeysText = computed(() =>
    (this.searchResults()?.grouped?.groupByKeys || [])
      .map((k: Column) => k.displayName || k.key)
      .join(' × '),
  );

  override readonly isLoading = computed<boolean>(
    () =>
      this.searchResource.isLoading() ||
      (!this.isLandingState() &&
        this.groupByKeys().length === 0 &&
        this.isConfigLoading() &&
        (this.visibleColumns().size === 0 || !this.sortColumn())) ||
      (this.isResolvingChips() &&
        this.groups().length === 0 &&
        this.rows().length === 0),
  );
  override readonly rows = computed<Row[]>(
    () => this.searchResults()?.flat?.rows || [],
  );
  override readonly hasNextPage = computed<boolean>(() =>
    Boolean(this.getPaginationToken('next')),
  );
  override readonly hasPrevPage = computed<boolean>(() =>
    this.groupByKeys().length > 0
      ? Boolean(this.getPaginationToken('prev'))
      : this.pageIndex() > 0,
  );
  override readonly rangeText = computed<string>(() =>
    computeFleetRangeText(this.groupByKeys().length > 0, this.searchResults()),
  );
  override readonly effectiveTotalCount = computed<number>(
    () =>
      (this.groupByKeys().length > 0
        ? this.groupedResults()?.totalItems
        : this.searchResults()?.flat?.total) || 0,
  );

  /** Resets pagination, expanded groups, and row selection, then reloads the primary Fleet search resource. */
  override executeSearch(): void {
    this.resetPagination();
    this.groupAccordion.resetExpandedGroups();
    this.clearSelection();
    this.searchResource.reload();
  }

  /** Extracts the next or previous cursor token from the active grouped or flat search response. */
  private getPaginationToken(type: 'next' | 'prev'): string | undefined {
    const page =
      this.groupByKeys().length > 0
        ? this.searchResults()?.grouped
        : this.searchResults()?.flat;
    return type === 'next' ? page?.nextPageToken : page?.prevPageToken;
  }

  /** Navigates to the previous page of Fleet search results using the cursor token. */
  override prevPage(): void {
    const token = this.getPaginationToken('prev');
    if (token !== undefined || this.pageIndex() === 1) {
      this.pageToken.set(token || '');
      this.pageIndex.update((p) => Math.max(0, p - 1));
    }
  }

  /** Navigates to the next page of Fleet search results using the cursor token. */
  override nextPage(): void {
    const token = this.getPaginationToken('next');
    if (token) {
      this.pageToken.set(token);
      this.pageIndex.update((p) => p + 1);
    }
  }

  /** Resolves and validates parsed URL filter and group-by chips via `ResolveFleetChips`. */
  override resolveChipsFromBackend(
    parsedFilters: FilterChip[],
    groupByKeys: string[],
    source?: string,
  ): Observable<FilterChip[]> {
    const {filterPairs, req} = buildFleetChipResolverInput(
      parsedFilters,
      groupByKeys,
      this.entity(),
      this.fleet(),
      source,
    );
    return this.searchService.resolveFleetChips(req).pipe(
      map((res) => {
        const {chips, warningText} = mapFleetResolvedChipsResponse(
          filterPairs,
          groupByKeys,
          res,
        );
        if (warningText) this.snackBarService.showWarning(warningText);
        return chips;
      }),
    );
  }

  /** Builds Fleet ValuePicker configuration from key metadata and column descriptions. */
  override buildPickerConfig(
    key: string,
    title?: string,
    metadata?: FilterKeyMetadata,
  ): ValuePickerConfig {
    return buildFleetPickerConfig(
      key,
      title,
      metadata || this.getKeyMetadata(key),
      this.columnDescriptions(),
    );
  }

  /** Builds initial Fleet ValuePicker state from an active filter chip and staged values. */
  override buildInitialPickerState(
    _key: string,
    activeChip?: FilterChip,
    _metadata?: FilterKeyMetadata,
    stagedValues: string[] = [],
  ): ValuePickerState {
    return buildFleetInitialPickerState(activeChip, stagedValues);
  }

  /** Resolves the applied ValuePicker selection via `ResolveFleetChips` and updates active filter chips. */
  override applyValuePicker(event: ValuePickerApplyEvent): void {
    const {key, meta, displayTitle} = this.consumeActivePicker();
    if (isValuePickerSelectionEmpty(event)) {
      this.removeChipForKey(key);
      return;
    }
    const built = buildFleetFilterFromPickerEvent(key, event);
    this.searchService
      .resolveFleetChips({...this.baseScope(), filters: [built.filter]})
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          const {chips, warnings} = extractAppliedFleetPickerChips(
            key,
            displayTitle,
            meta,
            built,
            res,
          );
          for (const w of warnings) this.snackBarService.showWarning(w);
          for (const chip of chips) this.addFilterChip(chip);
        },
        error: (err: unknown) => {
          this.snackBarService.showWarning?.(getErrorMessage(err));
        },
      });
  }

  /** Applies a selected Fleet suggestion by opening the ValuePicker or adding filter/group-by chips. */
  protected override applySuggestion(rawItem: FleetSuggestion): void {
    if (!rawItem) return;
    if (rawItem.openPicker) {
      const op = rawItem.openPicker;
      this.openQuickFilter(
        op.key,
        op.metadata?.keyDisplayName || op.key,
        op.metadata,
        op.stagedModify?.values,
      );
      return;
    }
    for (const chip of extractChipsFromFleetSuggestion(rawItem)) {
      this.addFilterChip(chip);
    }
  }

  /** Resets transient UI state along with expanded group cards and row selection. */
  protected override resetTransientUiState(): void {
    super.resetTransientUiState();
    this.groupAccordion.resetExpandedGroups();
    this.clearSelection();
  }
}
