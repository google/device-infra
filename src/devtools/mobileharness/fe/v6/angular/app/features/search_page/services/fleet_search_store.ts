import {computed, DestroyRef, inject, Injectable, signal} from '@angular/core';
import {rxResource, takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {of, timer} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';

import {
  Column,
  ComplexMatch,
  Filter,
  FilterValue,
  Fleet,
  FleetChipResolverRequest,
  FleetFilterChipMetadata,
  FleetFlatResults,
  FleetGroupedResults,
  FleetPromotedGroupByKey,
  FleetPromotedKeysRequest,
  FleetSearchConfig,
  FleetSearchConfigRequest,
  FleetSearchRequest,
  FleetSearchResults,
  FleetSuggestion,
  FleetSuggestionRequest,
  FleetValueListRequest,
  SearchEntity,
} from '../../../core/models/search';
import {SEARCH_SERVICE} from '../../../core/services/search/search_service';
import {
  ADV_MODES_LIST,
  ValuePickerApplyEvent,
} from '../components/value_picker/value_picker';
import {SearchPageStore} from './search_page_store';
import {
  EntityType,
  FilterChip,
  PickerValueItem,
  SearchBoxSuggestion,
} from './search_utils';

/** Store component holding state and actions for the fleet search page. */
@Injectable()
export class FleetSearchStore extends SearchPageStore {
  private readonly searchService = inject(SEARCH_SERVICE);
  private readonly destroyRef = inject(DestroyRef);

  // --- Core State Signals ---
  readonly entity = signal<EntityType>('devices');
  readonly fleet = signal<'internal' | 'ats'>('internal');
  readonly searchQuery = signal('');
  readonly activeChips = signal<FilterChip[]>([]);

  readonly showScopeSwitcher = computed(() => {
    const e = this.entity();
    return e === 'devices' || e === 'hosts';
  });

  readonly searchPlaceholder = computed(() => {
    switch (this.entity()) {
      case 'devices':
        return 'Type "pixel 10", or "model is", or a device ID';
      case 'hosts':
        return 'Type a host name, or "status is", or a host ID';
      default:
        return 'Type search query…';
    }
  });

  readonly suggestions = computed<SearchBoxSuggestion[]>(() => {
    return this.fleetSuggestions().map((item) => ({
      label: item.label,
      mainText: item.mainText,
      count: item.count,
      countPrefix: item.countPrefix,
      countUnit: item.countUnit,
      overMax: item.overMax,
      rawItem: item,
    }));
  });
  readonly selectedStatus = signal<string>('ALL');
  readonly selectedType = signal<string>('ALL');
  readonly selectedModel = signal<string>('ALL');

  readonly selectedDevices = signal<Set<string>>(new Set());
  readonly sortColumn = signal<string>('id');
  readonly sortAsc = signal<boolean>(true);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(25);
  readonly pageToken = signal<string>('');
  readonly prevPageTokens = signal<string[]>([]);

  readonly density = signal<'compact' | 'default' | 'comfortable'>('default');

  readonly visibleColumns = signal<Set<string>>(
    new Set(['id', 'hostName', 'status', 'type', 'owner', 'model']),
  );

  readonly availableColumns = [
    {key: 'id', label: 'Device ID / UUID'},
    {key: 'hostName', label: 'Host Name'},
    {key: 'status', label: 'Status'},
    {key: 'type', label: 'Device Type'},
    {key: 'owner', label: 'Owner'},
    {key: 'model', label: 'Model'},
    {key: 'battery', label: 'Battery'},
    {key: 'sdk', label: 'SDK Version'},
  ];

  readonly openGroupIds = signal<Set<string>>(new Set());
  readonly groupSort = signal<string>('count:desc');
  readonly expandedGroupPages = signal<
    Map<string, {loading?: boolean; data?: FleetFlatResults; error?: string}>
  >(new Map());

  readonly searchConfig = signal<FleetSearchConfig | null>(null);
  readonly browseAll = signal(false);

  readonly showSuggestions = signal(false);

  private readonly suggestionsResource = rxResource<
    FleetSuggestion[],
    | {
        entity: EntityType;
        input: string;
        fleet: string;
        activeFilters: Filter[];
        groupBy: string[];
        show: boolean;
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
        input: this.searchQuery(),
        fleet: this.fleet(),
        activeFilters: this.getQueryFilters(true),
        groupBy: this.groupByKeys(),
        show,
      };
    },
    stream: ({params: req}) => {
      if (!req) return of([]);
      const delayTime = req.input.trim() ? 200 : 0;
      return timer(delayTime).pipe(
        switchMap(() => {
          const fleetReq: FleetSuggestionRequest = {
            entity: this.getSearchEntityProto(req.entity),
            input: req.input,
            filters:
              req.activeFilters.length > 0 ? req.activeFilters : undefined,
            groupBy: req.groupBy.length > 0 ? req.groupBy : undefined,
            fleet: req.fleet === 'ats' ? Fleet.FLEET_ATS : Fleet.FLEET_SELF,
            limit: 10,
          };
          return this.searchService.getFleetSuggestions(fleetReq).pipe(
            map((res) => res.items || []),
            catchError(() => of([])),
          );
        }),
      );
    },
  });

  readonly fleetSuggestions = computed<FleetSuggestion[]>(
    () => this.suggestionsResource.value() || [],
  );

  fetchSuggestions(val: string) {
    // rxResource automatically updates suggestions when searchQuery signal changes.
  }
  readonly promotedFilterKeys = signal<
    Array<{key: string; metadata?: FleetFilterChipMetadata}>
  >([]);
  readonly promotedGroupByKeys = signal<
    Array<{key: string; displayName: string; groupCount?: number}>
  >([]);

  private readonly valueListResource = rxResource<
    PickerValueItem[],
    | {
        entity: EntityType;
        key: string;
        activeFilters: Filter[];
        fleet: string;
        show: boolean;
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
        activeFilters: this.getQueryFilters(true),
        fleet: this.fleet(),
        show,
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
          } else if (res.plain) {
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

  readonly showValuePicker = signal(false);
  readonly pickerKey = signal<string>('');
  readonly pickerTitle = signal<string>('');
  readonly pickerPos = signal<{top: number; left: number}>({top: 0, left: 0});
  readonly pickerLoading = computed(() => this.valueListResource.isLoading());
  readonly pickerCanUseAdvanced = signal<boolean>(false);
  readonly pickerIsAdvanced = signal<boolean>(false);
  readonly pickerAdvMode = signal<string>('prefix');
  readonly pickerAdvText = signal<string>('');
  readonly pickerAdvValues = signal<string[]>([]);
  readonly pickerValues = computed<PickerValueItem[]>(
    () => this.valueListResource.value() || [],
  );
  readonly selectedPickerValues = signal<Set<string>>(new Set());
  readonly pickerNegated = signal<boolean>(false);

  readonly keyMetadataMap = new Map<string, FleetFilterChipMetadata>();

  readonly showSearchClear = computed(() => {
    return (
      this.searchQuery().trim().length > 0 ||
      this.activeChips().length > 0 ||
      this.selectedStatus() !== 'ALL' ||
      this.selectedType() !== 'ALL' ||
      this.selectedModel() !== 'ALL' ||
      this.browseAll()
    );
  });

  readonly isTjs = computed(() => false);

  // --- Derived Signals (computed) ---
  readonly groupByKeys = computed<string[]>(() => {
    return this.activeChips()
      .filter((c) => c.isGroupBy)
      .map((c) => (c.key ? c.key.replace(/^group_by_/, '') : c.pillKey));
  });

  readonly isLandingState = computed(() => {
    const e = this.entity();
    if (e !== 'devices' && e !== 'hosts') {
      return false;
    }
    const landing = this.searchConfig()?.landing;
    if (landing && landing.enabled === false) {
      return false;
    }
    return (
      !this.browseAll() &&
      !this.searchQuery().trim() &&
      this.activeChips().length === 0 &&
      this.selectedStatus() === 'ALL' &&
      this.selectedType() === 'ALL' &&
      this.selectedModel() === 'ALL' &&
      this.groupByKeys().length === 0
    );
  });

  readonly groupKeysText = computed(() => {
    const keys = this.searchResults()?.grouped?.groupByKeys || [];
    return keys
      .map((k: FleetPromotedGroupByKey) => k.displayName || k.key)
      .join(' × ');
  });

  readonly groupSortOptions = [
    {value: 'count:desc', labelTemplate: 'Most {entity}'},
    {value: 'count:asc', labelTemplate: 'Fewest {entity}'},
    {value: 'name:asc', labelTemplate: 'Name (A–Z)'},
    {value: 'name:desc', labelTemplate: 'Name (Z–A)'},
    {value: 'util:desc', labelTemplate: 'Highest utilization'},
    {value: 'util:asc', labelTemplate: 'Lowest utilization'},
  ];

  // --- Resource for Fleet Search ---
  readonly searchResource = rxResource<
    FleetSearchResults | null,
    FleetSearchRequest | undefined
  >({
    params: () => {
      const e = this.entity();
      if (e !== 'devices' && e !== 'hosts') return undefined;
      if (this.isLandingState()) return undefined;

      const fleet = this.fleet();
      const selectedStatus = this.selectedStatus();
      const selectedType = this.selectedType();
      const selectedModel = this.selectedModel();
      const visibleColumns = Array.from(this.visibleColumns());
      const sortColumn = this.sortColumn();
      const sortAsc = this.sortAsc();
      const pageSize = this.pageSize();
      const pageToken = this.pageToken();

      const filters = this.getQueryFilters(false);
      if (selectedStatus !== 'ALL') {
        filters.push({
          key: 'field::status',
          simple: {values: [{value: selectedStatus}]},
        });
      }
      if (selectedType !== 'ALL') {
        filters.push({
          key: 'field::type',
          simple: {values: [{value: selectedType}]},
        });
      }
      if (selectedModel !== 'ALL') {
        filters.push({
          key: 'dim::model',
          simple: {values: [{value: selectedModel}]},
        });
      }

      const groupByKeys = this.groupByKeys();

      const req: FleetSearchRequest = {
        entity: this.getSearchEntityProto(e),
        fleet: fleet === 'ats' ? Fleet.FLEET_ATS : Fleet.FLEET_SELF,
        filters: filters.length > 0 ? filters : undefined,
      };

      if (groupByKeys.length > 0) {
        req.groupHeader = {
          groupBy: groupByKeys,
          page: {
            pageSize,
            pageToken: pageToken || undefined,
          },
        };
      } else {
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

  readonly searchResults = computed<FleetSearchResults | null>(() => {
    return this.searchResource.value() || null;
  });

  readonly isLoading = computed(() => this.searchResource.isLoading());

  readonly groupedResults = computed<FleetGroupedResults | null>(() => {
    return this.searchResults()?.grouped || null;
  });

  readonly displayColumns = computed<Column[]>(() => {
    const cols = this.searchResults()?.flat?.columns;
    if (cols && cols.length > 0) {
      return cols;
    }
    const cfgCols = this.searchConfig()?.columns?.defaults;
    if (cfgCols && cfgCols.length > 0) {
      return cfgCols.map((c) => ({key: c.key, displayName: c.displayName}));
    }
    return [];
  });

  readonly rows = computed<unknown[]>(() => {
    return this.searchResults()?.flat?.rows || [];
  });

  readonly effectiveTotalCount = computed(() => {
    return (
      (this.groupByKeys().length > 0
        ? this.groupedResults()?.totalItems
        : this.searchResults()?.flat?.total) || 0
    );
  });

  readonly effectiveRangeStart = computed(() => {
    const list =
      this.groupByKeys().length > 0
        ? this.groupedResults()?.groups
        : this.rows();
    if (!list || list.length === 0) return 0;
    return this.pageIndex() * this.pageSize() + 1;
  });

  readonly effectiveRangeEnd = computed(() => {
    const list =
      this.groupByKeys().length > 0
        ? this.groupedResults()?.groups
        : this.rows();
    if (!list || list.length === 0) return 0;
    return this.pageIndex() * this.pageSize() + list.length;
  });

  readonly totalPages = computed(() => {
    return Math.ceil(this.effectiveTotalCount() / this.pageSize()) || 1;
  });

  resetSearchState() {
    this.searchQuery.set('');
    this.activeChips.set([]);
    this.selectedStatus.set('ALL');
    this.selectedType.set('ALL');
    this.selectedModel.set('ALL');
    this.selectedDevices.set(new Set());
    this.browseAll.set(false);
    this.pageIndex.set(0);
    this.showSuggestions.set(false);
    this.showValuePicker.set(false);
    this.expandedGroupPages.set(new Map());
    this.loadPromotedKeys();
  }

  loadSearchConfig() {
    const e = this.entity();
    if (e !== 'devices' && e !== 'hosts') return;

    const req: FleetSearchConfigRequest = {
      entity: this.getSearchEntityProto(e),
      fleet: this.fleet() === 'ats' ? Fleet.FLEET_ATS : Fleet.FLEET_SELF,
    };

    this.searchService
      .getFleetSearchConfig(req)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((cfg) => {
        this.searchConfig.set(cfg);
        if (cfg.columns?.defaults && cfg.columns.defaults.length > 0) {
          const keys = cfg.columns.defaults.map((c) => c.key);
          this.visibleColumns.set(new Set(keys));
        }
      });
  }

  loadPromotedKeys() {
    const e = this.entity();
    if (e !== 'devices' && e !== 'hosts') return;

    const groupByKeys = this.groupByKeys();

    const req: FleetPromotedKeysRequest = {
      entity: this.getSearchEntityProto(e),
      fleet: this.fleet() === 'ats' ? Fleet.FLEET_ATS : Fleet.FLEET_SELF,
      groupBy: groupByKeys.length > 0 ? groupByKeys : undefined,
    };

    this.searchService
      .getFleetPromotedKeys(req)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((res) => {
        this.promotedFilterKeys.set(res.filterKeys || []);

        const mappedGroupByKeys = (res.groupByKeys || []).map((gk) => {
          const matchingFilterKey = res.filterKeys?.find(
            (fk) => fk.key === gk.key,
          );
          const keyDisplayName =
            matchingFilterKey?.metadata?.keyDisplayName || gk.displayName;
          return {
            ...gk,
            displayName: keyDisplayName,
          };
        });
        this.promotedGroupByKeys.set(mappedGroupByKeys);

        this.keyMetadataMap.clear();
        if (res.filterKeys) {
          for (const pk of res.filterKeys) {
            if (pk.metadata) {
              this.keyMetadataMap.set(pk.key, pk.metadata);
            }
          }
        }
        if (res.groupByKeys) {
          for (const gk of res.groupByKeys) {
            const matchingFilterKey = res.filterKeys?.find(
              (fk) => fk.key === gk.key,
            );
            const keyDisplayName =
              matchingFilterKey?.metadata?.keyDisplayName || gk.displayName;
            if (!this.keyMetadataMap.has(gk.key)) {
              this.keyMetadataMap.set(gk.key, {
                keyDisplayName,
              });
            }
          }
        }
      });
  }

  closeValuePicker() {
    this.showValuePicker.set(false);
  }

  applyValuePicker(event: ValuePickerApplyEvent) {
    const key = this.pickerKey();
    const title = this.pickerTitle();
    const meta = key ? this.keyMetadataMap.get(key) : undefined;
    const displayTitle = meta?.keyDisplayName || title;
    this.closeValuePicker();

    if (event.isAdvanced) {
      const mode = event.advMode || 'prefix';
      const isMulti = mode === 'exactly' || mode === 'at_least';
      if (isMulti) {
        const vals = event.advValues || [];
        if (vals.length === 0) {
          this.removeChipForKey(key);
          return;
        }
      } else {
        const txt = (event.advText || '').trim();
        if (!txt) {
          this.removeChipForKey(key);
          return;
        }
      }
    } else {
      const selected = event.selected;
      if (selected && selected.length === 0) {
        this.removeChipForKey(key);
        return;
      }
    }

    let fallbackPillCondition = '';
    if (event.isAdvanced) {
      const mode = event.advMode || 'prefix';
      const modeObj = ADV_MODES_LIST.find((m) => m.id === mode);
      const isMulti = mode === 'exactly' || mode === 'at_least';
      if (isMulti) {
        fallbackPillCondition = `${modeObj?.label || mode} "${(event.advValues || []).join(', ')}"`;
      } else {
        fallbackPillCondition = `${modeObj?.label || mode} "${(event.advText || '').trim()}"`;
      }
    } else if (event.selected) {
      const formatted = event.selected
        .map((v) => (v === '<empty>' ? '(no value)' : v))
        .join(', ');
      fallbackPillCondition = event.negate ? `!${formatted}` : formatted;
    }

    const rawValues = event.isAdvanced
      ? event.advValues && event.advValues.length > 0
        ? event.advValues
        : event.advText
          ? [event.advText.trim()]
          : undefined
      : event.selected;

    const filter: Filter = {key};
    let complex: ComplexMatch | undefined = undefined;
    if (event.isAdvanced) {
      const mode = event.advMode || 'prefix';
      const txt = (event.advText || '').trim();
      const vals = event.advValues || [];
      complex = {};
      if (mode === 'prefix') {
        complex.startsWith = {value: txt};
      } else if (mode === 'substring') {
        complex.containsSubstring = {value: txt};
      } else if (mode === 'not_substring') {
        complex.containsSubstring = {value: txt, negated: true};
      } else if (mode === 'regex' || mode === 'matches_regex') {
        complex.matchesRegex = {value: txt};
      } else if (
        mode === 'not_regex' ||
        mode === 'does_not_match_regex' ||
        mode === 'does not match regex'
      ) {
        complex.matchesRegex = {value: txt, negated: true};
      } else if (mode === 'exactly') {
        complex.matchesExactly = {values: vals};
      } else if (mode === 'at_least') {
        complex.matchesAtLeast = {values: vals};
      }
      filter.complex = complex;
    } else if (event.selected) {
      filter.simple = {
        values: event.selected.map((v) =>
          v === '<empty>' ? {noValue: {}} : {value: v},
        ),
        negated: event.negate ? true : undefined,
      };
    }

    const req: FleetChipResolverRequest = {
      filters: [filter],
    };

    this.searchService
      .resolveFleetChips(req)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          if (res.filterChips && res.filterChips.length > 0) {
            for (const fc of res.filterChips) {
              this.addFilterChip(
                fc.pillKey,
                fc.pillCondition,
                key,
                false,
                fc.metadata || meta,
                rawValues,
                event.negate,
                event.isAdvanced ? complex : undefined,
              );
            }
          } else {
            this.addFilterChip(
              displayTitle,
              fallbackPillCondition,
              key,
              false,
              meta,
              rawValues,
              event.negate,
              event.isAdvanced ? complex : undefined,
            );
          }
        },
        error: () => {
          this.addFilterChip(
            displayTitle,
            fallbackPillCondition,
            key,
            false,
            meta,
            rawValues,
            event.negate,
            event.isAdvanced ? complex : undefined,
          );
        },
      });
  }

  private removeChipForKey(key: string) {
    const cleanKey = key.toLowerCase().replace(/^(field::|dim::|config::)/, '');
    const existing = this.activeChips().find(
      (c) =>
        !c.isGroupBy &&
        ((c.key && c.key.toLowerCase() === key.toLowerCase()) ||
          c.pillKey.toLowerCase() === cleanKey),
    );
    if (existing) {
      this.removeFilterChip(existing);
    }
  }

  addFilterChip(
    pillKey: string,
    pillCondition: string,
    key?: string,
    isGroupBy = false,
    metadata?: FleetFilterChipMetadata,
    rawValues?: string[],
    negated?: boolean,
    complex?: ComplexMatch,
  ) {
    const nextChips = [...this.activeChips()];
    if (key || pillKey) {
      const targetKey = (key || pillKey).toLowerCase();
      const cleanKey = targetKey.replace(/^(field::|dim::|config::)/, '');
      const idx = nextChips.findIndex(
        (c) =>
          !c.isGroupBy &&
          ((c.key && c.key.toLowerCase() === targetKey) ||
            (c.key &&
              c.key.toLowerCase().replace(/^(field::|dim::|config::)/, '') ===
                cleanKey) ||
            c.pillKey.toLowerCase() === cleanKey),
      );
      if (idx !== -1) {
        const existing = nextChips[idx];
        const formattedCond =
          negated && !pillCondition.startsWith('!')
            ? `!${pillCondition}`
            : pillCondition;
        nextChips[idx] = {
          key: key || existing.key,
          pillKey,
          pillCondition: formattedCond,
          rawValues,
          metadata: metadata || existing.metadata,
          isGroupBy,
          negated: negated !== undefined ? negated : existing.negated,
          complex,
        };
        this.activeChips.set(nextChips);
        this.pageIndex.set(0);
        this.pageToken.set('');
        this.loadPromotedKeys();
        return;
      }
    }
    const formattedCond =
      negated && !pillCondition.startsWith('!')
        ? `!${pillCondition}`
        : pillCondition;
    nextChips.push({
      key,
      pillKey,
      pillCondition: formattedCond,
      rawValues,
      metadata,
      isGroupBy,
      negated,
      complex,
    });
    this.activeChips.set(nextChips);
    this.pageIndex.set(0);
    this.pageToken.set('');
    this.loadPromotedKeys();
  }

  removeFilterChip(chip: FilterChip) {
    const nextChips = this.activeChips().filter((c) => c !== chip);
    this.activeChips.set(nextChips);
    if (nextChips.length === 0 && !this.searchQuery().trim()) {
      this.browseAll.set(false);
    }
    this.pageIndex.set(0);
    this.pageToken.set('');
    this.loadPromotedKeys();
  }

  fetchValueList(key: string) {
    // rxResource valueListResource automatically loads values when pickerKey/showValuePicker updates.
  }

  executeFleetSearch() {
    this.searchResource.reload();
  }

  getSearchEntityProto(entity: EntityType): SearchEntity {
    return entity === 'hosts'
      ? SearchEntity.SEARCH_ENTITY_HOST
      : SearchEntity.SEARCH_ENTITY_DEVICE;
  }

  toggleColumn(colKey: string) {
    const cols = new Set(this.visibleColumns());
    if (cols.has(colKey)) {
      if (cols.size > 1) cols.delete(colKey);
    } else {
      cols.add(colKey);
    }
    this.visibleColumns.set(cols);
  }

  onLoadGroupRows(groupId: string, pageToken?: string) {
    const e = this.entity();
    if (e !== 'devices' && e !== 'hosts') return;

    const filters = this.getQueryFilters(false);
    if (this.selectedStatus() !== 'ALL') {
      filters.push({
        key: 'field::status',
        simple: {values: [{value: this.selectedStatus()}]},
      });
    }
    if (this.selectedType() !== 'ALL') {
      filters.push({
        key: 'field::type',
        simple: {values: [{value: this.selectedType()}]},
      });
    }
    if (this.selectedModel() !== 'ALL') {
      filters.push({
        key: 'dim::model',
        simple: {values: [{value: this.selectedModel()}]},
      });
    }

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

    this.expandedGroupPages.update((map) => {
      const next = new Map(map);
      const existing = next.get(groupId);
      next.set(groupId, {...existing, loading: true});
      return next;
    });

    this.searchService
      .searchFleet(req)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.expandedGroupPages.update((map) => {
            const next = new Map(map);
            next.set(groupId, {loading: false, data: res.flat});
            return next;
          });
        },
        error: (err) => {
          this.expandedGroupPages.update((map) => {
            const next = new Map(map);
            next.set(groupId, {
              loading: false,
              error: err?.message || 'Failed to load group rows.',
            });
            return next;
          });
        },
      });
  }

  prevPage() {
    const res = this.searchResults();
    const token =
      this.groupByKeys().length > 0
        ? res?.grouped?.prevPageToken
        : res?.flat?.prevPageToken;
    if (token) {
      this.pageToken.set(token);
      this.pageIndex.update((p) => p - 1);
      this.executeFleetSearch();
    }
  }

  nextPage() {
    const res = this.searchResults();
    const token =
      this.groupByKeys().length > 0
        ? res?.grouped?.nextPageToken
        : res?.flat?.nextPageToken;
    if (token) {
      this.pageToken.set(token);
      this.pageIndex.update((p) => p + 1);
      this.executeFleetSearch();
    }
  }

  getQueryFilters(onlyWithKeys = false): Filter[] {
    return this.activeChips()
      .filter((c) => !c.isGroupBy && (!onlyWithKeys || c.key))
      .map((c) => {
        const k = c.key || c.pillKey.toLowerCase();
        if (c.complex && Object.keys(c.complex).length > 0) {
          return {
            key: k,
            complex: c.complex,
          };
        }
        let cond = c.pillCondition.trim();
        let isNegatedFromPillPrefix = false;
        if (cond.startsWith('!')) {
          isNegatedFromPillPrefix = true;
          cond = cond.slice(1).trim();
        }

        const match = cond.match(
          /^(starts with|starts|prefix|startswith|starts_with|does not contain|not contain|doesnt contain|not_substring|not substring|contains substring|contains_substring|contains|substring|does not match regex|doesnt match regex|not_regex|not regex|matches regex|matches_regex|regex|matches exactly|matches_exactly|is exactly|exactly|matches at least|matches_at_least|is at least|at least|atleast)\b[\s:]*"?([^"]*)"?$/i,
        );
        if (match) {
          let rawMode = match[1].toLowerCase().trim().replace(/_/g, ' ');
          const val = match[2].replace(/^"|"$/g, '').trim();
          const valToUse = val || (c.rawValues && c.rawValues[0]) || '';
          const rawVals =
            c.rawValues && c.rawValues.length > 0
              ? c.rawValues
              : valToUse
                  .split(',')
                  .map((v) => v.trim())
                  .filter(Boolean);

          if (isNegatedFromPillPrefix) {
            if (rawMode === 'contains' || rawMode === 'substring') {
              rawMode = 'does not contain';
            } else if (rawMode === 'regex' || rawMode === 'matches regex') {
              rawMode = 'does not match regex';
            }
          }

          const complex: ComplexMatch = {};
          if (
            rawMode === 'starts with' ||
            rawMode === 'starts' ||
            rawMode === 'prefix' ||
            rawMode === 'startswith'
          ) {
            complex.startsWith = {value: valToUse};
          } else if (
            rawMode === 'does not contain' ||
            rawMode === 'not contain' ||
            rawMode === 'doesnt contain' ||
            rawMode === 'not substring'
          ) {
            complex.containsSubstring = {value: valToUse, negated: true};
          } else if (
            rawMode === 'contains' ||
            rawMode === 'substring' ||
            rawMode === 'contains substring'
          ) {
            complex.containsSubstring = {
              value: valToUse,
              negated: c.negated ? true : undefined,
            };
          } else if (
            rawMode === 'does not match regex' ||
            rawMode === 'doesnt match regex' ||
            rawMode === 'not regex'
          ) {
            complex.matchesRegex = {value: valToUse, negated: true};
          } else if (rawMode === 'regex' || rawMode === 'matches regex') {
            complex.matchesRegex = {
              value: valToUse,
              negated: c.negated ? true : undefined,
            };
          } else if (
            rawMode === 'exactly' ||
            rawMode === 'is exactly' ||
            rawMode === 'matches exactly'
          ) {
            complex.matchesExactly = {values: rawVals};
          } else if (
            rawMode === 'at least' ||
            rawMode === 'is at least' ||
            rawMode === 'atleast' ||
            rawMode === 'matches at least'
          ) {
            complex.matchesAtLeast = {values: rawVals};
          }
          return {
            key: k,
            complex,
          };
        }

        let values: FilterValue[] = [];
        if (c.rawValues && c.rawValues.length > 0) {
          values = c.rawValues.map((v) => {
            const val = v.trim();
            return val === '<empty>' || val === '(no value)'
              ? {noValue: {}}
              : {value: val};
          });
        } else {
          values = c.pillCondition.split(',').map((v) => {
            const val = v.trim();
            return val === '(no value)' ? {noValue: {}} : {value: val};
          });
        }
        return {
          key: k,
          simple: {
            values,
            negated: c.negated ? true : undefined,
          },
        };
      });
  }
}
