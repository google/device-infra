import {computed, DestroyRef, inject, Injectable, signal} from '@angular/core';
import {rxResource, takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Observable, of, timer} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';
import {
  Cell,
  Column,
  ComplexMatch,
  EnumOption,
  FleetFilterChipMetadata,
  TjsEntity,
  TjsFilter,
  TjsPromotedKey,
  TjsSearchConfig,
  TjsSearchConfigRequest,
  TjsSearchRequest,
  TjsSearchResponse,
  TjsSuggestion,
  TjsSuggestionRequest,
} from '../../../core/models/search';
import {SEARCH_SERVICE} from '../../../core/services/search/search_service';
import {ValuePickerApplyEvent} from '../components/value_picker/value_picker';
import {SearchPageStore} from './search_page_store';
import {
  EntityType,
  FilterChip,
  PickerValueItem,
  PromotedFilterKeyItem,
  PromotedGroupByKeyItem,
  SearchBoxSuggestion,
} from './search_utils';

/** State store for TJS search page. */
@Injectable()
export class TjsSearchStore extends SearchPageStore {
  private readonly searchService = inject(SEARCH_SERVICE);
  private readonly destroyRef = inject(DestroyRef);

  // --- Core State Signals ---
  readonly entity = signal<EntityType>('tests');
  readonly fleet = signal<'internal' | 'ats'>('internal');
  readonly searchQuery = signal('');
  readonly activeChips = signal<FilterChip[]>([]);
  readonly browseAll = signal(false);
  readonly isLandingState = signal(false);

  readonly showScopeSwitcher = signal(false);

  readonly searchPlaceholder = computed(() => {
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

  readonly suggestions = computed<SearchBoxSuggestion[]>(() => {
    return this.tjsSuggestions().map((item) => ({
      label: item.label,
      mainText: item.mainText,
      rawItem: item,
    }));
  });

  readonly pageIndex = signal(0);
  readonly pageSize = signal(25);
  readonly pageToken = signal<string>('');
  readonly prevPageTokens = signal<string[]>([]);
  readonly density = signal<'compact' | 'default' | 'comfortable'>('default');
  readonly isTjs = computed(() => true);

  readonly tjsSearchConfig = signal<TjsSearchConfig | null>(null);
  readonly searchConfig = computed(() => this.tjsSearchConfig());

  readonly showSuggestions = signal(false);

  private readonly suggestionsResource = rxResource<
    TjsSuggestion[],
    | {
        entity: EntityType;
        input: string;
      }
    | undefined
  >({
    params: () => {
      const e = this.entity();
      if (e !== 'tests' && e !== 'jobs' && e !== 'sessions') return undefined;
      const show = this.showSuggestions();
      if (!show) return undefined;

      return {
        entity: e,
        input: this.searchQuery(),
      };
    },
    stream: ({params: req}) => {
      if (!req) return of([]);
      const delayTime = req.input.trim() ? 200 : 0;
      return timer(delayTime).pipe(
        switchMap(() => {
          const tjsReq: TjsSuggestionRequest = {
            entity: this.getTjsEntityProto(req.entity),
            input: req.input,
            limit: 10,
          };
          return this.searchService.getTjsSuggestions(tjsReq).pipe(
            map((res) => res.items || []),
            catchError(() => of([])),
          );
        }),
      );
    },
  });

  readonly tjsSuggestions = computed<TjsSuggestion[]>(
    () => this.suggestionsResource.value() || [],
  );

  fetchSuggestions(val: string) {
    // rxResource manages reactive debouncing automatically when searchQuery signal updates.
  }
  readonly promotedFilterKeys = signal<PromotedFilterKeyItem[]>([]);
  readonly promotedGroupByKeys = signal<PromotedGroupByKeyItem[]>([]);
  readonly groupByKeys = signal<string[]>([]);

  private readonly valueListResource = rxResource<
    PickerValueItem[],
    | {
        entity: EntityType;
        key: string;
        show: boolean;
        config: TjsSearchConfig | null;
      }
    | undefined
  >({
    params: () => {
      const e = this.entity();
      if (e !== 'tests' && e !== 'jobs' && e !== 'sessions') return undefined;
      const key = this.pickerKey();
      const show = this.showValuePicker();
      if (!show || !key) return undefined;

      return {
        entity: e,
        key,
        show,
        config: this.tjsSearchConfig(),
      };
    },
    stream: ({params: req}) => {
      if (!req) return of([]);
      const pk = req.config?.promotedKeys?.find(
        (p: TjsPromotedKey) => p.key === req.key,
      );
      if (!pk || !pk.enumPicker) return of([]);

      const list: PickerValueItem[] = (pk.enumPicker.options || []).map(
        (v: EnumOption) => ({
          value: v.value,
          displayLabel: v.label || v.value,
        }),
      );
      return of(list);
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
      this.browseAll()
    );
  });

  readonly isLoading = computed(() => this.tjsResource.isLoading());

  // --- Resource for Tjs Search ---
  readonly tjsResource = rxResource<
    TjsSearchResponse | null,
    TjsSearchRequest | undefined
  >({
    params: () => {
      const e = this.entity();
      if (e !== 'tests' && e !== 'jobs' && e !== 'sessions') return undefined;

      const pageToken = this.pageToken();
      const filters = this.getTjsQueryFilters();

      if (this.pageIndex() === 0) {
        setTimeout(() => this.prevPageTokens.set([]), 0);
      }

      const req: TjsSearchRequest = {
        entity: this.getTjsEntityProto(e),
        filters: filters.length > 0 ? filters : undefined,
        pageToken: pageToken || undefined,
      };
      return req;
    },
    stream: ({params: req}): Observable<TjsSearchResponse | null> => {
      if (!req || !req.filters || req.filters.length === 0) {
        return of({rows: []});
      }
      return this.searchService.searchTjs(req).pipe(catchError(() => of(null)));
    },
  });

  readonly displayColumns = computed<Column[]>(() => {
    return this.tjsResource.value()?.columns || [];
  });

  readonly rows = computed<Array<Record<string, Cell | string | string[]>>>(
    () => {
      return (
        (this.tjsResource.value()?.rows as unknown as Array<
          Record<string, Cell | string | string[]>
        >) || []
      );
    },
  );

  // Flat Search results signature for SearchResults generic layout compatibility
  readonly searchResults = computed(() => {
    return {
      flat: {
        columns: this.displayColumns(),
        rows: this.rows(),
        nextPageToken: this.tjsResource.value()?.nextPageToken,
        prevPageToken: undefined,
      },
    };
  });

  readonly effectiveTotalCount = computed(() => {
    return 0; // Unused for TJS search tables
  });

  readonly effectiveRangeStart = computed(() => {
    return 0; // Unused for TJS search tables
  });

  readonly effectiveRangeEnd = computed(() => {
    return 0; // Unused for TJS search tables
  });

  readonly totalPages = computed(() => {
    return 1; // Unused for TJS search tables
  });

  resetSearchState() {
    this.searchQuery.set('');
    this.activeChips.set([]);
    this.browseAll.set(false);
    this.pageIndex.set(0);
    this.showSuggestions.set(false);
    this.showValuePicker.set(false);
  }

  loadSearchConfig() {
    const e = this.entity();
    if (e !== 'tests' && e !== 'jobs' && e !== 'sessions') return;

    const req: TjsSearchConfigRequest = {
      entity: this.getTjsEntityProto(e),
    };

    this.searchService
      .getTjsSearchConfig(req)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((cfg) => {
        this.tjsSearchConfig.set(cfg);

        if (cfg.defaultChips && cfg.defaultChips.length > 0) {
          const chips: FilterChip[] = cfg.defaultChips.map((c) => ({
            key: c.filter?.key || c.pillKey,
            pillKey: c.pillKey || c.keyDisplayName,
            pillCondition:
              c.pillCondition || c.filter?.stringValue?.value || '',
            label: c.pillKey || c.keyDisplayName,
            value: c.pillCondition || c.filter?.stringValue?.value || '',
          }));
          this.activeChips.set(chips);
        }

        this.browseAll.set(true);
        this.loadPromotedKeys();
      });
  }

  loadPromotedKeys() {
    const cfg = this.tjsSearchConfig();
    if (!cfg || !cfg.promotedKeys) {
      this.promotedFilterKeys.set([]);
      this.keyMetadataMap.clear();
      return;
    }
    this.keyMetadataMap.clear();
    const keys = cfg.promotedKeys.map((pk: TjsPromotedKey) => {
      const metadata = {
        ...pk,
        key: pk.key,
        keyDisplayName: pk.displayName || pk.key,
      };
      this.keyMetadataMap.set(pk.key, metadata);
      return {
        key: pk.key,
        metadata,
      };
    });
    this.promotedFilterKeys.set(keys);
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
        const val = event.advText || '';
        if (!val.trim()) {
          this.removeChipForKey(key);
          return;
        }
      }

      let modeLabel = '';
      if (mode === 'exactly') modeLabel = 'exactly';
      else if (mode === 'at_least') modeLabel = 'at least';
      else if (mode === 'starts_with') modeLabel = 'starts with';
      else if (mode === 'contains') modeLabel = 'contains';
      else if (mode === 'regex') modeLabel = 'regex';

      const conditionText =
        event.advValues && isMulti
          ? `${modeLabel} "${event.advValues.join(', ')}"`
          : `${modeLabel} "${event.advText || ''}"`;

      this.addFilterChip(
        displayTitle,
        conditionText,
        key,
        false,
        meta,
        event.advValues && isMulti ? event.advValues : [event.advText || ''],
      );
    } else if (event.tjsRangeFrom || event.tjsRangeTo) {
      const fromVal = event.tjsRangeFrom ? event.tjsRangeFrom : '';
      const toVal = event.tjsRangeTo ? event.tjsRangeTo : '';
      const labels: string[] = [];
      if (fromVal) labels.push(`From: ${fromVal.replace('T', ' ')}`);
      if (toVal) labels.push(`To: ${toVal.replace('T', ' ')}`);
      this.addFilterChip(displayTitle, labels.join(' '), key, false, meta, [
        fromVal,
        toVal,
      ]);
    } else if (event.tjsPropName || event.tjsTextVal) {
      const pName = event.tjsPropName ? event.tjsPropName.trim() : '';
      const pVal = event.tjsTextVal ? event.tjsTextVal.trim() : '';
      if (!pName && !pVal) {
        this.removeChipForKey(key);
        return;
      }
      this.addFilterChip(
        displayTitle,
        pName ? `${pName}:${pVal}` : pVal,
        key,
        false,
        meta,
        [pName, pVal],
      );
    } else if (event.selected) {
      const vals = event.selected;
      if (vals.length === 0) {
        this.removeChipForKey(key);
        return;
      }

      const formatted = vals
        .map((v) => (v === '<empty>' ? '(no value)' : v))
        .join(', ');
      this.addFilterChip(
        displayTitle,
        event.negate ? `!${formatted}` : formatted,
        key,
        false,
        meta,
        vals,
        event.negate,
      );
    } else {
      this.removeChipForKey(key);
    }
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
    this.tjsResource.reload();
  }

  prevPage() {
    const tokens = this.prevPageTokens();
    if (this.pageIndex() > 0 && tokens.length > 0) {
      const prevToken = tokens[this.pageIndex() - 2] || '';
      this.pageToken.set(prevToken);
      this.pageIndex.update((p) => p - 1);
    }
  }

  nextPage() {
    const nextToken = this.tjsResource.value()?.nextPageToken || '';
    if (nextToken) {
      this.prevPageTokens.update((t) => [...t, this.pageToken()]);
      this.pageToken.set(nextToken);
      this.pageIndex.update((p) => p + 1);
    }
  }

  getTjsEntityProto(entity: EntityType): TjsEntity {
    switch (entity) {
      case 'tests':
        return TjsEntity.TJS_ENTITY_TEST;
      case 'jobs':
        return TjsEntity.TJS_ENTITY_JOB;
      default:
        return TjsEntity.TJS_ENTITY_SESSION;
    }
  }

  private getTjsQueryFilters(): TjsFilter[] {
    const filters: TjsFilter[] = this.activeChips()
      .filter((c) => !c.isGroupBy)
      .map((c) => {
        const k = c.key || c.pillKey.toLowerCase();
        const filter: TjsFilter = {key: k};
        const isAdvanced =
          c.pillCondition.includes('"') ||
          /^(exactly|at least|starts with|ends with|contains|regex)\b/i.test(
            c.pillCondition,
          );

        if (isAdvanced) {
          filter.stringValue = {value: c.rawValues?.[0] || c.pillCondition};
        } else if (c.rawValues && c.rawValues.length > 0) {
          if (k === 'status' || k === 'result') {
            filter.enumValues = {values: c.rawValues};
          } else {
            filter.stringValue = {value: c.rawValues.join(', ')};
          }
        } else {
          const vals = c.pillCondition.split(',').map((v) => v.trim());
          if (k === 'status' || k === 'result') {
            filter.enumValues = {values: vals};
          } else {
            filter.stringValue = {value: c.pillCondition};
          }
        }
        return filter;
      });

    if (
      filters.length === 0 &&
      (this.browseAll() || this.searchQuery().trim())
    ) {
      const q = this.searchQuery().trim();
      const entity = this.entity();
      const defaultKey =
        entity === 'tests' ? 'name' : entity === 'jobs' ? 'name' : 'sessionId';
      filters.push({
        key: defaultKey,
        stringValue: {value: q || '*'},
      });
    }

    return filters;
  }
}
