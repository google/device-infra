/**
 * @fileoverview Composable store slice managing Fleet grouped view accordion expansion, group header sorting, and lazy inner-table RPC loads.
 */

import {computed, DestroyRef, inject, linkedSignal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Subscription} from 'rxjs';

import {FleetSearchRequest} from '../../../../core/models/search';
import {SEARCH_SERVICE} from '../../../../core/services/search/search_service';
import {getErrorMessage} from '../../../../shared/utils/error_utils';
import {
  DEFAULT_GROUP_EXPAND_PAGE_SIZE,
  GroupAccordionSliceOptions,
  GroupPageState,
  GroupSortOption,
} from '../../models';
import {
  getChipKey,
  getEntityNoun,
  getStoredGroupExpandPageSize,
  saveStoredGroupExpandPageSize,
  toFleetProto,
  toSearchEntityProto,
} from '../../utils';

/** Composable managing grouped view accordion state, group sorting, and lazy inner-table RPC loads. */
export function useGroupAccordion(opts: GroupAccordionSliceOptions) {
  const searchService = inject(SEARCH_SERVICE);
  const destroyRef = inject(DestroyRef);
  const groupLoadSubs = new Map<string, Subscription>();

  /**
   * Sorting mode for grouped view headers.
   * Automatically falls back to 'count:desc' if the active sort key is removed from groupBy.
   */
  const groupSort = linkedSignal<string[], string>({
    source: () => opts.groupByKeys(),
    computation: (keys, previous) => {
      const prev = previous?.value;
      const valid =
        prev &&
        (!prev.startsWith('gb_') ||
          keys.includes(prev.slice(prev.indexOf(':') + 1)));
      return valid ? prev : 'count:desc';
    },
  });

  /** Unique scope key reflecting the active query parameters for invalidating expanded group state. */
  const searchScopeKey = computed(
    () => `${opts.entity()}:${opts.currentUrlParamKey()}:${groupSort()}`,
  );

  /** Set of expanded group header IDs, automatically reset when query scope changes. */
  const openGroupIds = linkedSignal<string, Set<string>>({
    source: () => searchScopeKey(),
    computation: () => new Set<string>(),
  });

  /** Most recently expanded group header ID, automatically reset when query scope changes. */
  const focusedGroupId = linkedSignal<string, string | null>({
    source: () => searchScopeKey(),
    computation: () => null,
  });

  /** Map holding pagination data and loading states for expanded group headers, automatically reset when query scope changes. */
  const expandedGroupPages = linkedSignal<string, Map<string, GroupPageState>>({
    source: () => searchScopeKey(),
    computation: () => new Map(),
  });

  /** Dynamic options list for sorting group header cards based on entity and active group-by chips. */
  const groupSortOptions = computed<GroupSortOption[]>(() => {
    const entityLabel = getEntityNoun(opts.entity(), true);
    const countOptions: GroupSortOption[] = [
      {value: 'count:desc', label: `Most ${entityLabel}`},
      {value: 'count:asc', label: `Fewest ${entityLabel}`},
    ];
    const groupByChips = opts.activeChips().filter((c) => c.isGroupBy);
    if (groupByChips.length === 0) {
      return countOptions;
    }

    const gbOptions: GroupSortOption[] = [];
    for (const chip of groupByChips) {
      const rawKey = getChipKey(chip);
      const meta = opts.getKeyMetadata(rawKey);
      const displayName = chip.pillKey || meta?.keyDisplayName || rawKey;
      gbOptions.push(
        {value: `gb_asc:${rawKey}`, label: `${displayName} (A–Z)`},
        {value: `gb_desc:${rawKey}`, label: `${displayName} (Z–A)`},
      );
    }
    return [...countOptions, ...gbOptions];
  });

  /** Formats user-facing active label for group sorting button (e.g. "Most devices" or "Model (A–Z)"). */
  const groupSortActiveLabel = computed<string>(() => {
    const activeVal = groupSort();
    return (
      groupSortOptions().find((o) => o.value === activeVal)?.label ?? 'Sort'
    );
  });

  /** Merges a partial `GroupPageState` patch into `expandedGroupPages` for the given `groupId`. */
  const updateGroupExpandState = (
    groupId: string,
    patch: GroupPageState,
  ): void => {
    expandedGroupPages.update((map) => {
      const next = new Map(map);
      next.set(groupId, {...next.get(groupId), ...patch});
      return next;
    });
  };

  /**
   * Rows per page to load for a group:
   * 1. A group that has already been loaded keeps the page size it was loaded with (changing one
   *    group never affects the others).
   * 2. A group expanded for the first time starts from the page size saved for this entity.
   * 3. When nothing is saved yet, the default applies.
   */
  const resolveGroupPageSize = (state: GroupPageState): number => {
    if (state.pageSize !== undefined) {
      return state.pageSize;
    }
    const storedPageSize = getStoredGroupExpandPageSize(opts.entity());
    if (storedPageSize !== null) {
      return storedPageSize;
    }
    return DEFAULT_GROUP_EXPAND_PAGE_SIZE;
  };

  /** Loads flat rows for a specific expanded group header, applying any state overrides. */
  const onLoadGroupRows = (
    groupId: string,
    pageToken?: string,
    overrides?: Partial<GroupPageState>,
  ): void => {
    groupLoadSubs.get(groupId)?.unsubscribe();

    const scopeKey = searchScopeKey();
    const visibleCols = opts.visibleColumns();
    const existingState = expandedGroupPages().get(groupId);
    opts.deselectRows(existingState?.data?.rows);
    const state = {...existingState, ...overrides};
    const hasCustomSort =
      Boolean(state.sortColumn) && visibleCols.has(state.sortColumn!);
    const sortColumn = hasCustomSort ? state.sortColumn : opts.sortColumn();
    const sortAsc = hasCustomSort ? (state.sortAsc ?? true) : true;
    const pageSize = resolveGroupPageSize(state);
    const filters = opts.effectiveFilters();

    const req: FleetSearchRequest = {
      entity: toSearchEntityProto(opts.entity()),
      fleet: toFleetProto(opts.fleet()),
      filters: filters.length > 0 ? filters : undefined,
      groupExpand: {
        groupId,
        columns: Array.from(visibleCols),
        ...(sortColumn ? {sort: {key: sortColumn, ascending: sortAsc}} : {}),
        page: {pageSize, pageToken: pageToken || undefined},
      },
    };

    updateGroupExpandState(groupId, {
      ...state,
      loading: true,
      error: undefined,
      sortColumn,
      sortAsc,
      pageSize,
    });

    const sub = searchService
      .searchFleet(req)
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe({
        next: (res) => {
          if (searchScopeKey() !== scopeKey) return;
          updateGroupExpandState(groupId, {
            loading: false,
            error: undefined,
            data: res.flat,
          });
        },
        error: (err) => {
          if (searchScopeKey() !== scopeKey) return;
          const msg = getErrorMessage(err);
          updateGroupExpandState(groupId, {
            loading: false,
            error:
              msg !== 'An unknown error occurred.'
                ? msg
                : 'Failed to load group rows.',
          });
        },
      });

    groupLoadSubs.set(groupId, sub);
  };

  /** Sets the sort order mode for group header cards and resets pagination. */
  const setGroupSort = (value: string): void => {
    opts.resetPagination();
    groupSort.set(value);
  };

  /** Toggles accordion open/collapsed state for a group and loads rows if newly opened and not cached. */
  const toggleGroup = (groupId: string): void => {
    const nextOpenIds = new Set(openGroupIds());
    const cachedData = expandedGroupPages().get(groupId)?.data;
    if (nextOpenIds.delete(groupId)) {
      openGroupIds.set(nextOpenIds);
      groupLoadSubs.get(groupId)?.unsubscribe();
      groupLoadSubs.delete(groupId);
      opts.deselectRows(cachedData?.rows);
      return;
    }
    nextOpenIds.add(groupId);
    openGroupIds.set(nextOpenIds);
    focusedGroupId.set(groupId);
    if (!cachedData) {
      onLoadGroupRows(groupId);
    }
  };

  /** Toggles sort order for a column inside an expanded group card and reloads its first page. */
  const toggleGroupRowSort = (groupId: string, colKey: string): void => {
    const state = expandedGroupPages().get(groupId);
    const currentCol = state?.sortColumn || opts.sortColumn();
    const sortAsc = currentCol === colKey ? !(state?.sortAsc ?? true) : true;
    onLoadGroupRows(groupId, undefined, {sortColumn: colKey, sortAsc});
  };

  /**
   * Updates rows-per-page size for an expanded group card and reloads its first page. The choice is
   * also saved per entity so the next group expanded for the first time starts with it; groups that
   * are already loaded keep their own size.
   */
  const setGroupPageSize = (groupId: string, pageSize: number): void => {
    const currentSize = resolveGroupPageSize(
      expandedGroupPages().get(groupId) ?? {},
    );
    saveStoredGroupExpandPageSize(opts.entity(), pageSize);
    if (currentSize !== pageSize) {
      onLoadGroupRows(groupId, undefined, {pageSize});
    }
  };

  /** Invalidates cached group rows and reloads currently open groups after a visible column change. */
  const reloadOpenGroupsForColumnChange = (): void => {
    expandedGroupPages.update(
      (prev) =>
        new Map(
          Array.from(prev, ([id, state]) => [id, {...state, data: undefined}]),
        ),
    );
    for (const groupId of openGroupIds()) {
      onLoadGroupRows(groupId);
    }
  };

  /** Cancels in-flight group loads and clears expanded group state. */
  const resetExpandedGroups = (): void => {
    for (const sub of groupLoadSubs.values()) {
      sub.unsubscribe();
    }
    groupLoadSubs.clear();
    if (openGroupIds().size > 0) {
      openGroupIds.set(new Set());
    }
    if (expandedGroupPages().size > 0) {
      expandedGroupPages.set(new Map());
    }
  };

  return {
    searchScopeKey,
    openGroupIds,
    focusedGroupId,
    groupSort,
    expandedGroupPages,
    groupSortOptions,
    groupSortActiveLabel,
    setGroupSort,
    toggleGroup,
    toggleGroupRowSort,
    setGroupPageSize,
    onLoadGroupRows,
    reloadOpenGroupsForColumnChange,
    resetExpandedGroups,
  };
}
