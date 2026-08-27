import {computed, Injectable, signal} from '@angular/core';
import {rxResource, takeUntilDestroyed, toObservable} from '@angular/core/rxjs-interop';
import {Observable, of} from 'rxjs';
import {catchError, map} from 'rxjs/operators';
import {
  Column,
  Row,
  TjsEntity,
  TjsFilter,
  TjsPromotedKey,
  TjsResolveChipsRequest,
  TjsSearchConfig,
  TjsSearchRequest,
  TjsSearchResponse,
  TjsSuggestion,
  TjsSuggestionRequest,
} from '../../../core/models/search';
import {
  EntityType,
  FilterChip,
  FilterKeyMetadata,
  ParsedQueryFilter,
  PickerValueItem,
  PromotedFilterKeyItem,
  SearchBoxSuggestion,
  ValuePickerApplyEvent,
  ValuePickerConfig,
  ValuePickerState,
} from '../models';
import {
  extractFilterChipFromTjsSuggestion,
  mapToSearchBoxSuggestion,
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
  // ===========================================================================
  // 1. Dynamic UI Prompts & Suggestion Signals
  // ===========================================================================

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
      const e = this.entity();
      if (e !== 'tests' && e !== 'jobs' && e !== 'sessions') return undefined;
      const show = this.showSuggestions();
      if (!show) return undefined;

      return {
        entity: e,
        input: this.debouncedSearchQuery(),
        filters: this.getAllEffectiveFilters(),
      };
    },
    stream: ({params: req}) => {
      if (!req) return of([]);
      const tjsReq: TjsSuggestionRequest = {
        entity: this.getTjsEntityProto(req.entity),
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

  /** Exposes current array of raw TJS suggestions. */
  readonly tjsSuggestions = computed<TjsSuggestion[]>(
    () => this.suggestionsResource.value() || [],
  );

  /** Mapped search suggestions for SearchBox popover consumption. */
  override readonly suggestions = computed<SearchBoxSuggestion[]>(() => {
    return this.tjsSuggestions().map(mapToSearchBoxSuggestion);
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
      const e = this.entity();
      if (e !== 'tests' && e !== 'jobs' && e !== 'sessions') return undefined;
      return e;
    },
    stream: ({params: entity}) => {
      if (!entity) return of(null);
      return this.searchService
        .getTjsSearchConfig({entity: this.getTjsEntityProto(entity)})
        .pipe(catchError(() => of(null)));
    },
  });

  /** Exposes current TJS search configuration. */
  override readonly searchConfig = computed<TjsSearchConfig | null>(
    () => this.searchConfigResource.value() || null,
  );

  /** Whether initial search configuration is loading. */
  override readonly isConfigLoading = computed<boolean>(
    () => this.searchConfigResource.isLoading(),
  );

  /** Whether auxiliary promoted keys are currently loading. */
  override readonly isPromotedKeysLoading = computed<boolean>(
    () => this.searchConfigResource.isLoading(),
  );

  constructor() {
    super();

    // When initial search config loads on a clean URL, populate default chips
    toObservable(this.searchConfig)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((cfg) => {
        const queryParams = this.route.snapshot?.queryParams;
        const hasUrlFilters = queryParams?.['f'] || queryParams?.['gb'];
        if (cfg && !hasUrlFilters && this.activeChips().length === 0) {
          const defaults = this.mapDefaultChips(cfg);
          if (defaults.length > 0) {
            this.activeChips.set(defaults);
          }
        }
      });
  }

  /** Returns default chips from active search configuration if available. */
  override getDefaultChips(): FilterChip[] {
    return this.mapDefaultChips(this.searchConfig());
  }

  /** Helper to map TjsSearchConfig default chips to FilterChip array. */
  private mapDefaultChips(cfg: TjsSearchConfig | null): FilterChip[] {
    if (cfg?.defaultChips && cfg.defaultChips.length > 0) {
      return cfg.defaultChips.map((c) => {
        let rawVals: string[] | undefined;
        if (c.filter?.enumValues?.values) {
          rawVals = c.filter.enumValues.values;
        } else if (c.filter?.stringValue?.value) {
          rawVals = [c.filter.stringValue.value];
        } else if (c.filter?.namedValue) {
          rawVals = [c.filter.namedValue.name, c.filter.namedValue.value];
        }
        const key = c.filter?.key || c.pillKey;
        const meta =
          this.keyMetadataMap().get(c.pillKey?.toLowerCase()) ||
          this.keyMetadataMap().get(key);
        return {
          key,
          pillKey: c.pillKey || c.keyDisplayName || meta?.keyDisplayName || key,
          pillCondition: c.pillCondition || c.filter?.stringValue?.value || '',
          rawValues: rawVals,
          metadata: meta,
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
          const meta: FilterKeyMetadata = {
            ...pk,
            key: pk.key,
            keyDisplayName: pk.displayName || pk.key,
          };
          nextMap.set(pk.key, meta);
          if (pk.displayName) {
            nextMap.set(pk.displayName.toLowerCase(), meta);
          }
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
      const e = this.entity();
      if (e !== 'tests' && e !== 'jobs' && e !== 'sessions') return undefined;

      // On clean page entry, wait for searchConfig to load and populate default chips
      const queryParams = this.route.snapshot?.queryParams;
      const hasUrlFilters = queryParams?.['f'] || queryParams?.['gb'];
      if (!hasUrlFilters) {
        const cfg = this.searchConfig();
        if (!cfg) {
          return undefined;
        }
        if (this.activeChips().length === 0 && cfg.defaultChips && cfg.defaultChips.length > 0) {
          return undefined;
        }
      }

      return {
        entity: e,
        pageToken: this.pageToken(),
        filters: this.getAllEffectiveFilters(),
      };
    },
    stream: ({params: req}) => {
      if (!req) return of({columns: [], rows: []} as TjsSearchResponse);

      const tjsReq: TjsSearchRequest = {
        entity: this.getTjsEntityProto(req.entity),
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

  /** Triggers execution of primary TJS search and forces resource reload. */
  override executeSearch() {
    this.pageIndex.set(0);
    this.pageToken.set('');
    this.prevPageTokens.set([]);
    this.tjsResource.reload();
  }

  /** Maps frontend EntityType string enum to backend Protobuf TjsEntity enum. */
  private getTjsEntityProto(e: EntityType): TjsEntity {
    switch (e) {
      case 'tests':
        return TjsEntity.TJS_ENTITY_TEST;
      case 'jobs':
        return TjsEntity.TJS_ENTITY_JOB;
      case 'sessions':
        return TjsEntity.TJS_ENTITY_SESSION;
      default:
        return TjsEntity.TJS_ENTITY_TEST;
    }
  }

  /** Combines active filter chips into backend TjsFilter array. */
  private getAllEffectiveFilters(): TjsFilter[] {
    return this.activeChips()
      .map((chip) => this.buildTjsFilterFromChip(chip))
      .filter(Boolean) as TjsFilter[];
  }

  /** Maps FilterChip to TjsFilter Protobuf payload. */
  private buildTjsFilterFromChip(chip: FilterChip): TjsFilter | null {
    if (!chip.key && !chip.pillKey) return null;
    const key = chip.key || chip.pillKey;

    const meta =
      chip.metadata ||
      this.keyMetadataMap().get(chip.pillKey.toLowerCase()) ||
      this.keyMetadataMap().get(key);
    const tjsMeta = meta as unknown as TjsPromotedKey | undefined;

    if (tjsMeta?.timeRange || key === 'create_time') {
      const cond = chip.pillCondition || '';
      const parts = cond.split('~').map((s) => s.trim());
      return {
        key,
        timeRange: {
          from: this.toIsoString(parts[0] || ''),
          to: this.toIsoString(parts[1] || ''),
        },
      };
    }

    if (
      tjsMeta?.namedPair ||
      key === 'dimension' ||
      (key === 'property' && chip.pillKey.toLowerCase() === 'property')
    ) {
      const cond = chip.pillCondition || '';
      const idx = cond.indexOf(':');
      const name =
        idx !== -1 ? cond.substring(0, idx) : chip.rawValues?.[0] || '';
      const value =
        idx !== -1
          ? cond.substring(idx + 1)
          : chip.rawValues?.[1] || chip.rawValues?.[0] || cond;
      return {
        key,
        namedValue: {
          name,
          value,
        },
      };
    }

    if (tjsMeta?.enumPicker) {
      const vals =
        chip.rawValues && chip.rawValues.length > 0
          ? chip.rawValues
          : chip.pillCondition
              .split(',')
              .map((s) => s.trim())
              .filter(Boolean);
      return {
        key,
        enumValues: {
          values: vals,
        },
      };
    }

    if (chip.rawValues && chip.rawValues.length > 0) {
      return {
        key,
        stringValue: {
          value: chip.rawValues[0] || chip.rawValues.join(','),
        },
      };
    }

    if (chip.pillCondition) {
      return {
        key,
        stringValue: {
          value: chip.pillCondition,
        },
      };
    }

    return null;
  }

  /** Converts ISO or timestamp string to ISO format string. */
  private toIsoString(val: string): string | undefined {
    if (!val) return undefined;
    const d = new Date(val);
    return isNaN(d.getTime()) ? val : d.toISOString();
  }

  // ===========================================================================
  // 4. Pagination Stack & History Controls
  // ===========================================================================

  /** History stack of page tokens used for backward pagination. */
  readonly prevPageTokens = signal<string[]>([]);

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
  // 5. URL Resolution & Fallback Chip Construction
  // ===========================================================================

  /** Overrides base fallback chip builder to exclude polarity, advanced match, and group-by. */
  override buildFallbackChip(pf: ParsedQueryFilter): FilterChip {
    const meta =
      this.keyMetadataMap().get(pf.fallbackPillCondition.toLowerCase()) ||
      this.keyMetadataMap().get(pf.key);
    return {
      key: pf.key,
      pillKey: meta?.keyDisplayName || pf.key,
      pillCondition: pf.fallbackPillCondition,
      rawValues: pf.rawValues,
      metadata: meta,
    };
  }

  /** Resolves parsed URL query filters into FilterChip list using TJS resolve API. */
  protected override resolveChipsFromBackend(
    parsedFilters: ParsedQueryFilter[],
    _groupByKeys: string[],
  ): Observable<FilterChip[]> {
    if (parsedFilters.length === 0) {
      return of([]);
    }

    const req: TjsResolveChipsRequest = {
      filters: parsedFilters.map((pf) => pf.tjsFilter),
    };

    return this.searchService.resolveTjsChips(req).pipe(
      map((res) => {
        const updatedChips: FilterChip[] = [];
        if (res.chips && res.chips.length > 0) {
          res.chips.forEach((c, idx) => {
            const pf = parsedFilters[idx];
            const meta = pf?.key
              ? this.keyMetadataMap().get(c.pillKey?.toLowerCase()) ||
                this.keyMetadataMap().get(pf.key)
              : undefined;
            updatedChips.push({
              key: pf?.key,
              pillKey: c.pillKey,
              pillCondition: c.pillCondition,
              rawValues: pf?.rawValues,
              metadata: meta,
            });
          });
        }
        return updatedChips;
      }),
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
    const meta =
      (metadata as FilterKeyMetadata) ||
      (title ? this.keyMetadataMap().get(title.toLowerCase()) : undefined) ||
      this.keyMetadataMap().get(key);
    const displayTitle = meta?.keyDisplayName || title || key;
    const tjsMeta = meta as unknown as TjsPromotedKey | undefined;

    let layoutType: 'list' | 'range' | 'namedPair' | 'text' = 'list';
    let needsName = false;
    let namePlaceholder = 'Name';
    let valPlaceholder = 'Value';

    if (tjsMeta?.timeRange || key === 'create_time') {
      layoutType = 'range';
    } else if (tjsMeta?.enumPicker) {
      layoutType = 'list';
    } else if (
      tjsMeta?.namedPair ||
      (key === 'property' && displayTitle.toLowerCase() === 'property') ||
      key === 'dimension'
    ) {
      layoutType = 'namedPair';
      needsName = true;
      namePlaceholder = tjsMeta?.namedPair?.namePlaceholder || 'Property name';
      valPlaceholder =
        tjsMeta?.namedPair?.valuePlaceholder || 'Property value';
    } else if (tjsMeta?.textInput) {
      layoutType = 'text';
      valPlaceholder = tjsMeta.textInput.placeholder || 'Value';
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
  ): ValuePickerState {
    const meta =
      (metadata as FilterKeyMetadata) ||
      (activeChip?.pillKey
        ? this.keyMetadataMap().get(activeChip.pillKey.toLowerCase())
        : undefined) ||
      this.keyMetadataMap().get(key);
    const tjsMeta = meta as unknown as TjsPromotedKey | undefined;

    let enumOptions: PickerValueItem[] = [];
    if (tjsMeta?.enumPicker?.options && tjsMeta.enumPicker.options.length > 0) {
      enumOptions = tjsMeta.enumPicker.options.map((o) => ({
        value: o.value,
        displayLabel: o.label || o.value,
      }));
    }

    let selectedVals = new Set<string>();
    if (activeChip) {
      if (activeChip.rawValues && activeChip.rawValues.length > 0) {
        selectedVals = new Set(activeChip.rawValues);
      } else if (activeChip.pillCondition) {
        const existingVals = activeChip.pillCondition
          .split(',')
          .map((s: string) => s.trim())
          .filter(Boolean);
        selectedVals = new Set(existingVals);
      }
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

  /** Handles ValuePicker apply events by resolving chips from API or falling back locally. */
  override applyValuePicker(event: ValuePickerApplyEvent) {
    const key = this.pickerKey();
    const title = this.pickerTitle();
    const meta = key
      ? this.keyMetadataMap().get(title.toLowerCase()) ||
        this.keyMetadataMap().get(key)
      : undefined;
    const displayTitle = meta?.keyDisplayName || title;
    this.closeValuePicker();

    const tjsFilter = this.buildTjsFilter(key, event, meta);
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
          if (res.chips && res.chips.length > 0) {
            const c = res.chips[0];
            this.addFilterChip({
              key,
              pillKey: c.pillKey || displayTitle,
              pillCondition: c.pillCondition,
              rawValues: this.extractRawValues(event),
              metadata: meta,
            });
          } else {
            this.applyTjsChipLocally(event, key, displayTitle, meta);
          }
        },
        error: () => {
          this.applyTjsChipLocally(event, key, displayTitle, meta);
        },
      });
  }

  /** Builds a TjsFilter Protobuf request object from a ValuePickerApplyEvent. */
  private buildTjsFilter(
    key: string,
    event: ValuePickerApplyEvent,
    meta?: FilterKeyMetadata,
  ): TjsFilter | null {
    if (event.rangeFrom || event.rangeTo) {
      const fromVal = event.rangeFrom?.trim() || '';
      const toVal = event.rangeTo?.trim() || '';
      return {
        key,
        timeRange: {
          from: this.toIsoString(fromVal),
          to: this.toIsoString(toVal),
        },
      };
    }

    const tjsMeta = meta as unknown as TjsPromotedKey | undefined;

    if (
      tjsMeta?.namedPair ||
      event.propName ||
      (key === 'property' && meta?.keyDisplayName?.toLowerCase() === 'property') ||
      key === 'dimension'
    ) {
      const propName = event.propName?.trim() || '';
      const propVal =
        event.textVal?.trim() ||
        (event.selected && event.selected[0]) ||
        '';
      if (!propName && !propVal) return null;
      return {
        key,
        namedValue: {
          name: propName,
          value: propVal,
        },
      };
    }

    if (tjsMeta?.enumPicker) {
      if (event.selected && event.selected.length > 0) {
        return {
          key,
          enumValues: {
            values: event.selected,
          },
        };
      }
      return null;
    }

    if (event.textVal?.trim()) {
      return {
        key,
        stringValue: {
          value: event.textVal.trim(),
        },
      };
    }

    if (event.selected && event.selected.length > 0) {
      return {
        key,
        stringValue: {
          value: event.selected[0],
        },
      };
    }

    return null;
  }

  /** Formats fallback pill condition string from ValuePickerApplyEvent. */
  private formatTjsFallbackCondition(event: ValuePickerApplyEvent): string {
    if (event.rangeFrom || event.rangeTo) {
      return `${event.rangeFrom || ''} ~ ${event.rangeTo || ''}`.trim();
    }
    if (event.selected && event.selected.length > 0) {
      return event.selected.join(', ');
    }
    if (event.propName || event.textVal) {
      return `${event.propName || ''}:${event.textVal || ''}`;
    }
    if (event.textVal?.trim()) {
      return event.textVal.trim();
    }
    return '';
  }

  /** Helper method to locally insert a fallback TJS filter chip into state. */
  private applyTjsChipLocally(
    event: ValuePickerApplyEvent,
    key: string,
    displayTitle: string,
    meta?: FilterKeyMetadata,
  ) {
    const condition = this.formatTjsFallbackCondition(event);
    if (!condition) return;

    this.addFilterChip({
      key,
      pillKey: displayTitle,
      pillCondition: condition,
      rawValues: this.extractRawValues(event),
      metadata: meta,
    });
  }

  /** Extracts raw values string array from ValuePickerApplyEvent. */
  private extractRawValues(event: ValuePickerApplyEvent): string[] | undefined {
    if (event.selected && event.selected.length > 0) {
      return event.selected;
    }
    if (event.textVal?.trim()) {
      return [event.textVal.trim()];
    }
    return undefined;
  }

  // ===========================================================================
  // 7. Suggestion Selection, Chip Mutators, & State Reset
  // ===========================================================================

  /** Directly resolves and applies test/job/session suggestion filters to store. */
  override selectSuggestion(item: SearchBoxSuggestion) {
    const raw = item.rawItem as TjsSuggestion;
    if (!raw) return;

    this.showSuggestions.set(false);
    this.searchQuery.set('');

    this.applyTjsSuggestionDirectly(raw);
  }

  /** Directly applies a TjsSuggestion locally without redundant backend resolution. */
  private applyTjsSuggestionDirectly(item: TjsSuggestion) {
    const chip = extractFilterChipFromTjsSuggestion(item);
    if (chip) {
      this.addFilterChip(chip);
    }
  }

  /** Appends or updates a filter chip and clears pagination token history. */
  override addFilterChip(chip: FilterChip) {
    this.prevPageTokens.set([]);
    super.addFilterChip(chip);
  }

  /** Removes a filter chip and clears pagination token history. */
  override removeFilterChip(chip: FilterChip) {
    this.prevPageTokens.set([]);
    super.removeFilterChip(chip);
  }

  /** Fully resets search query, active chips, and pagination history. */
  override resetSearchState(updateUrl = true) {
    super.resetSearchState(updateUrl);
    this.prevPageTokens.set([]);
  }

}
