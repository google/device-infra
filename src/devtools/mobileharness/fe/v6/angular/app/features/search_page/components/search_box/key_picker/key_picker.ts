import {DecimalPipe} from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import {rxResource, toObservable, toSignal} from '@angular/core/rxjs-interop';
import {MatButtonModule} from '@angular/material/button';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {of, timer} from 'rxjs';
import {debounce, distinctUntilChanged} from 'rxjs/operators';

import {
  Filter,
  Fleet,
  FleetFilterKeyCatalogResponse,
  FleetFilterKeyEntry,
  FleetFilterKeySection,
  FleetGroupByKeyCatalogResponse,
  FleetGroupByKeyEntry,
  FleetGroupByKeySection,
  SearchEntity,
} from '../../../../../core/models/search';
import {SEARCH_SERVICE} from '../../../../../core/services/search/search_service';
import {Dialog} from '../../../../../shared/components/dialog/dialog';
import {KeyDescriptionTooltipDirective} from '../../../../../shared/directives/key_description_tooltip/key_description_tooltip';
import {KeyPickerDialogData, KeyPickerMode} from '../../../models';
import {SearchPageStore} from '../../../services/search_page_store';
import {toFleetProto, toSearchEntityProto} from '../../../utils';

/**
 * Key Picker modal dialog for adding filters or group-by dimensions.
 * Directly renders catalog response from BFF without redundant client-side calculations.
 */
@Component({
  selector: 'app-key-picker',
  standalone: true,
  templateUrl: './key_picker.ng.html',
  styleUrl: './key_picker.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    'class': 'key-picker-dialog-host',
  },
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    Dialog,
    KeyDescriptionTooltipDirective,
  ],
})
export class KeyPickerComponent {
  private readonly data = inject<KeyPickerDialogData>(MAT_DIALOG_DATA, {
    optional: true,
  });
  private readonly dialogRef = inject(MatDialogRef<KeyPickerComponent>, {
    optional: true,
  });
  private readonly searchService = inject(SEARCH_SERVICE, {optional: true});
  readonly store = inject(SearchPageStore);

  readonly searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  readonly mode = computed<KeyPickerMode>(() => this.data?.mode ?? 'filter');

  readonly dialogTitle = computed<string>(() =>
    this.mode() === 'groupby' ? 'Add a group-by' : 'Add a filter',
  );

  readonly query = signal<string>('');

  /**
   * Debounced search query passed to catalog RPC.
   * Immediately clears when input is blank; debounces by 300ms when typing.
   */
  readonly debouncedQuery = toSignal(
    toObservable(this.query).pipe(
      debounce((val) => timer(val.trim() === '' ? 0 : 300)),
      distinctUntilChanged(),
    ),
    {initialValue: ''},
  );

  /** Whether the user input is currently debouncing before triggering the RPC. */
  readonly isDebouncing = computed<boolean>(
    () => this.query().trim() !== this.debouncedQuery().trim(),
  );

  readonly entity = computed<SearchEntity>(
    () => this.data?.entity ?? toSearchEntityProto(this.store.entity()),
  );

  readonly fleet = computed<Fleet>(
    () => this.data?.fleet ?? toFleetProto(this.store.fleet()),
  );

  /** Reactive resource fetching catalog directly from backend BFF. */
  readonly catalogResource = rxResource<
    FleetFilterKeyCatalogResponse | FleetGroupByKeyCatalogResponse | null,
    {
      mode: KeyPickerMode;
      query: string;
      entity: SearchEntity;
      fleet: Fleet;
      filters: Filter[];
      groupBy: string[];
    }
  >({
    params: () => ({
      mode: this.mode(),
      query: this.debouncedQuery().trim(),
      entity: this.entity(),
      fleet: this.fleet(),
      filters: this.data?.activeFilters ?? [],
      groupBy: this.data?.activeGroupBy ?? this.store.groupByKeys(),
    }),
    stream: ({params: req}) => {
      if (!this.searchService) return of(null);
      const query = req.query || undefined;
      const filters = req.filters.length > 0 ? req.filters : undefined;
      if (req.mode === 'groupby') {
        const groupBy = req.groupBy.length > 0 ? req.groupBy : undefined;
        return this.searchService.getFleetGroupByKeyCatalog({
          entity: req.entity,
          fleet: req.fleet,
          query,
          filters,
          groupBy,
        });
      }
      return this.searchService.getFleetFilterKeyCatalog({
        entity: req.entity,
        fleet: req.fleet,
        query,
        filters,
      });
    },
  });

  /** Loading state driven directly by the reactive catalog resource. */
  readonly isLoading = this.catalogResource.isLoading;

  /** Error message displayed in the error banner, matching the home page error display format. */
  readonly errorMessage = computed<string | null>(() => {
    const err = this.catalogResource.error();
    if (!err) return null;
    return (err as {message?: string})?.message || 'Failed to load keys.';
  });

  /** Catalog sections directly from the BFF response with non-empty entries. */
  readonly currentSections = computed<
    Array<FleetFilterKeySection | FleetGroupByKeySection>
  >(() =>
    (this.catalogResource.value()?.sections || []).filter(
      (s) => (s.entries?.length ?? 0) > 0,
    ),
  );

  /** Entity noun ('devices' or 'hosts') for coverage display. */
  readonly entityNoun = computed<string>(() =>
    this.entity() === SearchEntity.SEARCH_ENTITY_HOST ? 'hosts' : 'devices',
  );

  /** Total count of matching entries across all visible catalog sections. */
  readonly totalMatchedKeysCount = computed<number>(() =>
    this.currentSections().reduce(
      (sum, s) => sum + (s.entries?.length || 0),
      0,
    ),
  );

  /** Whether the initial catalog is loading before any sections are present. */
  readonly isInitialLoading = computed<boolean>(
    () =>
      this.catalogResource.isLoading() &&
      this.currentSections().length === 0 &&
      !this.errorMessage(),
  );

  /** Whether a search operation is active (either debouncing user keystrokes or fetching from backend). */
  readonly isSearching = computed<boolean>(
    () => this.isDebouncing() || this.catalogResource.isLoading(),
  );

  constructor() {
    afterNextRender(() => {
      this.searchInput()?.nativeElement.focus();
    });
  }

  onSearch(v: string) {
    this.query.set(v);
  }

  clearSearch() {
    this.query.set('');
    this.searchInput()?.nativeElement.focus();
  }

  close() {
    this.dialogRef?.close();
  }

  /** Retries reloading the catalog resource. */
  retry() {
    this.catalogResource.reload();
  }

  /** Resolves the display name from either a filter-key or group-by-key catalog entry. */
  getEntryName(entry: FleetFilterKeyEntry | FleetGroupByKeyEntry): string {
    if ('displayName' in entry && entry.displayName) {
      return entry.displayName;
    }
    if ('metadata' in entry && entry.metadata?.keyDisplayName) {
      return entry.metadata.keyDisplayName;
    }
    return entry.key;
  }

  /** Returns what a catalog entry's key means, or '' when the backend sends no description. */
  getEntryDescription(
    entry: FleetFilterKeyEntry | FleetGroupByKeyEntry,
  ): string {
    if ('description' in entry && entry.description) {
      return entry.description;
    }
    if ('metadata' in entry && entry.metadata?.description) {
      return entry.metadata.description;
    }
    return '';
  }

  /** Returns the group count for a group-by catalog entry when shown by BFF. */
  getGroupCount(
    entry: FleetFilterKeyEntry | FleetGroupByKeyEntry,
  ): number | undefined {
    if (this.mode() !== 'groupby' || !('groupCount' in entry)) {
      return undefined;
    }
    return entry.groupCount?.shown?.count;
  }

  /** Whether a row is inert (an already-applied group-by key cannot be picked again). */
  isInert(entry: FleetFilterKeyEntry | FleetGroupByKeyEntry): boolean {
    return Boolean(entry.applied) && this.mode() === 'groupby';
  }

  /**
   * Applies the picked entry directly according to BFF response:
   * For group-by: delegates directly to store.openQuickGroupBy.
   * For filter: delegates directly to store.openQuickFilter with backend metadata.
   */
  onPick(entry: FleetFilterKeyEntry | FleetGroupByKeyEntry) {
    if (this.isInert(entry)) return;
    this.close();

    const name = this.getEntryName(entry);
    if (this.mode() === 'groupby') {
      this.store.openQuickGroupBy(entry.key, name);
      return;
    }

    const metadata = 'metadata' in entry ? entry.metadata : undefined;
    this.store.openQuickFilter(entry.key, name, metadata);
  }
}
