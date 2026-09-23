import {CommonModule, DecimalPipe} from '@angular/common';
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
import {FormsModule} from '@angular/forms';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {of} from 'rxjs';
import {catchError, debounceTime, distinctUntilChanged} from 'rxjs/operators';

import {
  Filter,
  Fleet,
  FleetFilterChipMetadata,
  FleetFilterKeyCatalogRequest,
  FleetFilterKeyCatalogResponse,
  FleetFilterKeyEntry,
  FleetGroupByKeyCatalogRequest,
  FleetGroupByKeyCatalogResponse,
  FleetGroupByKeyEntry,
  SearchEntity,
} from '../../../../../core/models/search';
import {SEARCH_SERVICE} from '../../../../../core/services/search/search_service';
import {Dialog} from '../../../../../shared/components/dialog/dialog';
import {SearchPageStore} from '../../../services/search_page_store';

/** Mode of key picker: selecting a filter key or a group-by key. */
export type KeyPickerMode = 'filter' | 'groupby';

/** Configuration payload for KeyPickerComponent dialog. */
export interface KeyPickerDialogData {
  mode: KeyPickerMode;
  entity?: SearchEntity;
  fleet?: Fleet;
  activeFilters?: Filter[];
  activeGroupBy?: string[];
}

/** View model representation of a single entry in the key picker catalog. */
export interface KeyPickerEntryView {
  key: string;
  name: string;
  meta: string;
  applied: boolean;
  inert: boolean;
  filterMetadata?: FleetFilterChipMetadata;
}

/** Section with entries for rendering either filter-key or group-by-key catalogs generically. */
export interface KeyPickerSectionView {
  heading: string;
  entries: KeyPickerEntryView[];
  totalAvailable?: number;
}

/** Alias for backwards compatibility. */
export type GenericKeySection = KeyPickerSectionView;

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
    CommonModule,
    DecimalPipe,
    FormsModule,
    MatDialogModule,
    MatIconModule,
    Dialog,
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

  readonly searchInput =
    viewChild<ElementRef<HTMLInputElement>>('searchInput');

  readonly mode = computed<KeyPickerMode>(() => this.data?.mode ?? 'filter');

  readonly dialogTitle = computed<string>(() =>
    this.mode() === 'groupby' ? 'Add a group-by' : 'Add a filter',
  );

  readonly query = signal<string>('');

  /** Debounced search query passed to catalog RPC. */
  readonly debouncedQuery = toSignal(
    toObservable(this.query).pipe(
      debounceTime(150),
      distinctUntilChanged(),
    ),
    {initialValue: ''},
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
      entity:
        this.data?.entity ??
        (this.store.entity?.() === 'hosts'
          ? SearchEntity.SEARCH_ENTITY_HOST
          : SearchEntity.SEARCH_ENTITY_DEVICE),
      fleet:
        this.data?.fleet ??
        (this.store.fleet?.() === 'ats' ? Fleet.FLEET_ATS : Fleet.FLEET_SELF),
      filters:
        this.data?.activeFilters ||
        (this.store as unknown as {effectiveFilters?: () => Filter[]})
          .effectiveFilters?.() ||
        [],
      groupBy: this.data?.activeGroupBy || this.store.groupByKeys?.() || [],
    }),
    stream: ({params: req}) => {
      if (!this.searchService) return of(null);
      if (req.mode === 'groupby') {
        const fleetReq: FleetGroupByKeyCatalogRequest = {
          entity: req.entity,
          fleet: req.fleet,
          query: req.query || undefined,
          filters: req.filters.length > 0 ? req.filters : undefined,
          groupBy: req.groupBy.length > 0 ? req.groupBy : undefined,
        };
        return this.searchService
          .getFleetGroupByKeyCatalog(fleetReq)
          .pipe(catchError(() => of(null)));
      }
      const fleetReq: FleetFilterKeyCatalogRequest = {
        entity: req.entity,
        fleet: req.fleet,
        query: req.query || undefined,
        filters: req.filters.length > 0 ? req.filters : undefined,
      };
      return this.searchService
        .getFleetFilterKeyCatalog(fleetReq)
        .pipe(catchError(() => of(null)));
    },
  });

  /** Loading state driven directly by the reactive catalog resource. */
  readonly isLoading = computed<boolean>(() => this.catalogResource.isLoading());

  /** Pre-formatted view sections ready for direct template rendering. */
  readonly currentSections = computed<KeyPickerSectionView[]>(() => {
    const cat = this.catalogResource.value();
    if (!cat?.sections) return [];
    const isGroupBy = this.mode() === 'groupby';
    const entity = this.store.entity?.() || 'devices';
    const noun = entity === 'hosts' ? 'hosts' : 'devices';

    return cat.sections.map((s) => ({
      heading: s.heading,
      totalAvailable: s.totalAvailable,
      entries: (s.entries || []).map((e) => {
        const isApplied = Boolean(e.applied);
        const inert = isApplied && isGroupBy;
        let name = '';
        let meta = '';

        if (isGroupBy) {
          const gb = e as FleetGroupByKeyEntry;
          name = gb.displayName || gb.key;
          const count = gb.groupCount?.shown?.count;
          meta = isApplied
            ? 'applied'
            : count != null
              ? `${count.toLocaleString()} groups`
              : '';
        } else {
          const f = e as FleetFilterKeyEntry;
          name = f.metadata?.keyDisplayName || f.key;
          const count = f.coverage?.shown?.count;
          meta = isApplied
            ? 'applied'
            : count != null
              ? `Used by ${count.toLocaleString()} ${noun}`
              : '';
        }

        return {
          key: e.key,
          name: name || e.key,
          meta,
          applied: isApplied,
          inert,
          filterMetadata: (e as FleetFilterKeyEntry).metadata,
        };
      }),
    }));
  });

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
