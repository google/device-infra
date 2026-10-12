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
import {of} from 'rxjs';
import {debounceTime, distinctUntilChanged} from 'rxjs/operators';

import {
  Filter,
  Fleet,
  FleetFilterKeyCatalogResponse,
  FleetGroupByKeyCatalogResponse,
  SearchEntity,
} from '../../../../../core/models/search';
import {SEARCH_SERVICE} from '../../../../../core/services/search/search_service';
import {Dialog} from '../../../../../shared/components/dialog/dialog';
import {
  KeyPickerDialogData,
  KeyPickerEntryView,
  KeyPickerMode,
  KeyPickerSectionView,
} from '../../../models';
import {SearchPageStore} from '../../../services/search_page_store';
import {
  adaptCatalogSections,
  toFleetProto,
  toSearchEntityProto,
} from '../../../utils';

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
  imports: [DecimalPipe, MatButtonModule, MatIconModule, Dialog],
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

  /** Debounced search query passed to catalog RPC. */
  readonly debouncedQuery = toSignal(
    toObservable(this.query).pipe(debounceTime(150), distinctUntilChanged()),
    {initialValue: ''},
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

  /** Pre-formatted view sections ready for direct template rendering. */
  readonly currentSections = computed<KeyPickerSectionView[]>(() =>
    adaptCatalogSections(
      this.catalogResource.value()?.sections,
      this.mode(),
      this.entity(),
    ),
  );

  constructor() {
    afterNextRender(() => {
      this.searchInput()?.nativeElement.focus();
    });
  }

  onSearch(v: string) {
    this.query.set(v);
  }

  close() {
    this.dialogRef?.close();
  }

  /** Retries reloading the catalog resource. */
  retry() {
    this.catalogResource.reload();
  }

  /**
   * Applies the picked entry directly according to BFF response:
   * For group-by: delegates directly to store.openQuickGroupBy.
   * For filter: delegates directly to store.openQuickFilter with backend metadata.
   */
  onPick(entry: KeyPickerEntryView) {
    if (entry.inert) return;
    this.close();

    if (this.mode() === 'groupby') {
      this.store.openQuickGroupBy(entry.key, entry.name);
      return;
    }

    this.store.openQuickFilter(entry.key, entry.name, entry.filterMetadata);
  }
}
