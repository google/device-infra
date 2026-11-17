/**
 * @fileoverview Reactive store managing Test, Job, and Session (TJS) BFF search resources, default chips, and cursor pagination.
 */

import {
  computed,
  inject,
  Injectable,
  linkedSignal,
  signal,
} from '@angular/core';
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
  TjsSearchConfig,
  TjsSearchRequest,
  TjsSearchResponse,
  TjsSuggestion,
  TjsSuggestionRequest,
} from '../../../core/models/search';
import {SnackBarService} from '../../../shared/services/snackbar_service';
import {getErrorMessage} from '../../../shared/utils/error_utils';
import {
  EntityType,
  FilterChip,
  FilterKeyMetadata,
  PromotedFilterKeyItem,
  SearchBoxSuggestion,
  ValuePickerApplyEvent,
  ValuePickerConfig,
  ValuePickerState,
} from '../models';
import {
  areFilterArraysEqual,
  buildTjsFilterFromChip,
  buildTjsInitialPickerState,
  buildTjsPickerConfig,
  buildTjsPromotedKeys,
  computeTjsActiveChips,
  extractFilterChipFromTjsSuggestion,
  extractTjsRawValues,
  mapTjsDefaultChips,
  mapTjsResolvedChipsResponse,
  prepareTjsChipResolution,
  toTjsEntityProto,
} from '../utils';
import {SearchPageStore} from './search_page_store';

/** State store for Test / Job / Session (TJS) search pages. */
@Injectable()
export class TjsSearchStore extends SearchPageStore<TjsSuggestion, TjsFilter> {
  private static readonly SUPPORTED_ENTITIES = new Set<EntityType>([
    'tests',
    'jobs',
    'sessions',
  ]);

  private readonly snackBarService = inject(SnackBarService);
  override readonly isTjs = signal(true).asReadonly();

  /** Returns `'tests'` as the default TJS entity type. */
  override getDefaultEntity(): EntityType {
    return 'tests';
  }

  /** Returns the supported TJS entity types (`'tests'`, `'jobs'`, and `'sessions'`). */
  override getSupportedEntities(): Set<EntityType> {
    return TjsSearchStore.SUPPORTED_ENTITIES;
  }

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

  private readonly suggestionsResource = rxResource<
    TjsSuggestion[],
    TjsSuggestionRequest | undefined
  >({
    params: () => {
      const input = this.getActiveSuggestionInput();
      if (input === null) return undefined;
      const filters = this.effectiveFilters();
      return {
        entity: toTjsEntityProto(this.entity()),
        input,
        filters: filters.length > 0 ? filters : undefined,
        limit: 10,
      };
    },
    stream: ({params: req}) =>
      req
        ? this.searchService.getTjsSuggestions(req).pipe(
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

  private readonly searchConfigResource = rxResource<
    TjsSearchConfig | null,
    EntityType | undefined
  >({
    params: () => this.entity(),
    stream: ({params: entity}) =>
      entity
        ? this.searchService
            .getTjsSearchConfig({entity: toTjsEntityProto(entity)})
            .pipe(catchError(() => of(null)))
        : of(null),
  });

  override readonly searchConfig = computed<TjsSearchConfig | null>(
    () => this.searchConfigResource.value() || null,
  );
  private readonly searchConfig$ = toObservable(this.searchConfig);

  readonly entityLabel = computed<string>(
    () => this.searchConfig()?.entityLabel || this.entity(),
  );
  override readonly isConfigLoading = this.searchConfigResource.isLoading;
  override readonly isPromotedKeysLoading = this.searchConfigResource.isLoading;

  override readonly activeChips = linkedSignal<
    TjsSearchConfig | null,
    FilterChip[]
  >({
    source: () => this.searchConfig(),
    computation: (config, previous) =>
      computeTjsActiveChips(config, previous, this.route, (k) =>
        this.getKeyMetadata(k),
      ),
  });

  /** Maps the current `TjsSearchConfig.defaultChips` into active `FilterChip` items. */
  override getDefaultChips(): FilterChip[] {
    return mapTjsDefaultChips(this.searchConfig(), (k) =>
      this.getKeyMetadata(k),
    );
  }

  override readonly promotedFilterKeys = computed<PromotedFilterKeyItem[]>(() =>
    buildTjsPromotedKeys(this.searchConfig()),
  );
  override readonly keyMetadataMap = computed<Map<string, FilterKeyMetadata>>(
    () => new Map(this.promotedFilterKeys().map((k) => [k.key, k.metadata!])),
  );

  private readonly tjsResource = rxResource<
    TjsSearchResponse,
    TjsSearchRequest | undefined
  >({
    params: () => {
      if (!this.isCurrentRouteActive()) return undefined;
      const filters = this.effectiveFilters();
      return filters.length === 0
        ? undefined
        : {
            entity: toTjsEntityProto(this.entity()),
            pageToken: this.pageToken() || undefined,
            filters,
          };
    },
    stream: ({params: req}) =>
      req
        ? this.searchService
            .searchTjs(req)
            .pipe(
              catchError(() =>
                of({columns: [], rows: []} as TjsSearchResponse),
              ),
            )
        : of({columns: [], rows: []} as TjsSearchResponse),
  });

  override readonly isLoading = computed<boolean>(
    () =>
      this.tjsResource.isLoading() ||
      (this.isConfigLoading() && !this.searchConfig()) ||
      (this.isResolvingChips() && this.rows().length === 0),
  );
  override readonly displayColumns = computed<Column[]>(
    () => this.tjsResource.value()?.columns || [],
  );
  override readonly rows = computed<Row[]>(
    () => this.tjsResource.value()?.rows || [],
  );
  override readonly hasNextPage = computed<boolean>(() =>
    Boolean(this.tjsResource.value()?.nextPageToken),
  );

  override readonly rangeText = computed<string>(() => {
    const count = this.rows().length;
    if (count === 0) return '';
    const start = this.pageIndex() * this.pageSize() + 1;
    const end = this.pageIndex() * this.pageSize() + count;
    return `showing ${start.toLocaleString()}–${end.toLocaleString()}`;
  });

  /** Resets pagination and page-token history, then reloads the TJS search resource. */
  override executeSearch(): void {
    this.resetPagination();
    this.prevPageTokens.set([]);
    this.tjsResource.reload();
  }

  readonly effectiveFilters = computed<TjsFilter[]>(
    () =>
      this.activeChips()
        .map((chip) => this.toTjsFilter(chip))
        .filter(Boolean) as TjsFilter[],
    {equal: areFilterArraysEqual},
  );

  /** Converts a `FilterChip` into a `TjsFilter` proto payload using the store's key metadata. */
  private toTjsFilter(chip: Partial<FilterChip>): TjsFilter | null {
    return buildTjsFilterFromChip(chip, Boolean(this.searchConfig()), (k) =>
      this.getKeyMetadata(k),
    );
  }

  readonly prevPageTokens = linkedSignal<
    {chips: FilterChip[]; query: string},
    string[]
  >({
    source: () => ({chips: this.activeChips(), query: this.searchQuery()}),
    computation: () => [],
  });

  /** Pops the previous cursor token from `prevPageTokens` and navigates back one page. */
  override prevPage(): void {
    const tokens = this.prevPageTokens();
    if (tokens.length === 0) return;
    const prev = tokens[tokens.length - 1];
    this.prevPageTokens.set(tokens.slice(0, tokens.length - 1));
    this.pageToken.set(prev);
    this.pageIndex.update((i) => Math.max(0, i - 1));
  }

  /** Pushes the current cursor token onto `prevPageTokens` and advances to `nextPageToken`. */
  override nextPage(): void {
    const nextToken = this.tjsResource.value()?.nextPageToken;
    if (!nextToken) return;
    this.prevPageTokens.update((tokens) => [...tokens, this.pageToken()]);
    this.pageToken.set(nextToken);
    this.pageIndex.update((i) => i + 1);
  }

  /** Waits for `searchConfig` if needed, then resolves parsed URL filter chips via `ResolveTjsChips`. */
  override resolveChipsFromBackend(
    parsedFilters: FilterChip[],
  ): Observable<FilterChip[]> {
    if (parsedFilters.length === 0) return of([]);
    if (!this.searchConfig()) {
      return this.searchConfig$.pipe(
        filter((cfg): cfg is TjsSearchConfig => cfg !== null),
        take(1),
        switchMap(() => this.executeTjsResolve(parsedFilters)),
      );
    }
    return this.executeTjsResolve(parsedFilters);
  }

  /** Validates parsed TJS filter keys against metadata and resolves display pills via `ResolveTjsChips`. */
  private executeTjsResolve(
    parsedFilters: FilterChip[],
  ): Observable<FilterChip[]> {
    const {filterPairs, warnings} = prepareTjsChipResolution(
      parsedFilters,
      this.entity(),
      (k) => this.getKeyMetadata(k),
      (chip) => this.toTjsFilter(chip),
    );
    for (const w of warnings) this.snackBarService.showWarning(w);
    if (filterPairs.length === 0) return of([]);
    return this.searchService
      .resolveTjsChips({filters: filterPairs.map((p) => p.filter)})
      .pipe(
        map((res) =>
          mapTjsResolvedChipsResponse(filterPairs, res, (k) =>
            this.getKeyMetadata(k),
          ),
        ),
        catchError(() => of(filterPairs.map((p) => p.chip))),
      );
  }

  /** Builds TJS ValuePicker configuration based on key metadata (`range`, `list`, `namedPair`, or `text`). */
  override buildPickerConfig(
    key: string,
    title?: string,
    metadata?: FilterKeyMetadata,
  ): ValuePickerConfig {
    return buildTjsPickerConfig(
      key,
      title,
      metadata || this.getKeyMetadata(key),
    );
  }

  /** Builds initial TJS ValuePicker state from key metadata, active chip, and staged values. */
  override buildInitialPickerState(
    key: string,
    activeChip?: FilterChip,
    metadata?: FilterKeyMetadata,
    stagedValues: string[] = [],
  ): ValuePickerState {
    return buildTjsInitialPickerState(
      metadata || this.getKeyMetadata(key),
      activeChip,
      stagedValues,
    );
  }

  /** Resolves the applied TJS ValuePicker selection via `ResolveTjsChips` and updates active filter chips. */
  override applyValuePicker(event: ValuePickerApplyEvent): void {
    const {key, meta, displayTitle} = this.consumeActivePicker();
    const rawValues = extractTjsRawValues(event, meta);
    const tjsFilter = this.toTjsFilter({
      key,
      pillKey: displayTitle,
      rawValues,
      metadata: meta,
    });
    if (!tjsFilter) {
      this.removeChipForKey(key);
      return;
    }

    this.searchService
      .resolveTjsChips({filters: [tjsFilter]})
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          for (const c of res.chips ?? []) {
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
        error: (err: unknown) => {
          this.snackBarService.showWarning?.(getErrorMessage(err));
        },
      });
  }

  /** Applies a selected TJS suggestion by opening the ValuePicker or adding the resolved filter chip. */
  protected override applySuggestion(raw: TjsSuggestion): void {
    if (!raw) return;
    if (raw.openPicker) {
      const op = raw.openPicker;
      const meta = this.getKeyMetadata(op.key);
      this.openQuickFilter(
        op.key,
        op.keyDisplayName || meta?.keyDisplayName || op.key,
        meta,
      );
      return;
    }
    const chip = extractFilterChipFromTjsSuggestion(raw);
    if (chip) this.addFilterChip(chip);
  }
}
