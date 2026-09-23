import {CommonModule, DecimalPipe} from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {Subject} from 'rxjs';
import {debounceTime, distinctUntilChanged} from 'rxjs/operators';

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

/** Section with entries for rendering either filter-key or group-by-key catalogs generically. */
export interface GenericKeySection {
  heading: string;
  entries: Array<FleetFilterKeyEntry | FleetGroupByKeyEntry>;
  totalAvailable?: number;
}

/**
 * Key Picker modal dialog for adding filters or group-by dimensions.
 * Corresponds to prototype `search_devices/key_picker.js` and `site.html`.
 */
@Component({
  selector: 'app-key-picker',
  standalone: true,
  templateUrl: './key_picker.ng.html',
  styleUrl: './key_picker.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    DecimalPipe,
    FormsModule,
    MatDialogModule,
    MatIconModule,
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
  private readonly destroyRef = inject(DestroyRef);

  readonly searchInput =
    viewChild<ElementRef<HTMLInputElement>>('searchInput');

  readonly mode = computed<KeyPickerMode>(() => this.data?.mode ?? 'filter');

  readonly dialogTitle = computed<string>(() =>
    this.mode() === 'groupby' ? 'Add a group-by' : 'Add a filter',
  );

  readonly query = signal<string>('');
  readonly isLoading = signal<boolean>(false);
  readonly filterCatalog = signal<FleetFilterKeyCatalogResponse | null>(null);
  readonly groupByCatalog = signal<FleetGroupByKeyCatalogResponse | null>(null);

  private readonly searchSubject = new Subject<string>();

  /** Current catalog sections formatted generically for template rendering. */
  readonly currentSections = computed<GenericKeySection[]>(() => {
    if (this.mode() === 'groupby') {
      const cat = this.groupByCatalog();
      if (!cat?.sections) return [];
      return cat.sections.map((s: FleetGroupByKeySection) => ({
        heading: s.heading,
        entries: s.entries || [],
        totalAvailable: s.totalAvailable,
      }));
    }

    const cat = this.filterCatalog();
    if (!cat?.sections) return [];
    return cat.sections.map((s: FleetFilterKeySection) => ({
      heading: s.heading,
      entries: s.entries || [],
      totalAvailable: s.totalAvailable,
    }));
  });

  constructor() {
    afterNextRender(() => {
      this.searchInput()?.nativeElement.focus();
    });

    this.searchSubject
      .pipe(debounceTime(150), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.fetchCatalog();
      });

    this.fetchCatalog();
  }

  onSearch(v: string) {
    this.query.set(v);
    this.searchSubject.next(v.trim());
  }

  close() {
    this.dialogRef?.close();
  }

  isInert(entry: FleetFilterKeyEntry | FleetGroupByKeyEntry): boolean {
    return Boolean(entry.applied && this.mode() === 'groupby');
  }

  getEntryName(entry: FleetFilterKeyEntry | FleetGroupByKeyEntry): string {
    const filter = entry as FleetFilterKeyEntry;
    return (
      entry.displayName ||
      filter.metadata?.keyDisplayName ||
      entry.key
    );
  }

  getEntryMeta(entry: FleetFilterKeyEntry | FleetGroupByKeyEntry): string {
    if (entry.applied) return 'applied';
    if (this.mode() === 'groupby') {
      const gb = entry as FleetGroupByKeyEntry;
      const count = gb.groupCount?.shown?.count;
      return count != null ? `${count.toLocaleString()} groups` : '';
    }
    const filter = entry as FleetFilterKeyEntry;
    const count = filter.coverage?.shown?.count;
    if (count != null) {
      const entity = this.store.entity?.() || 'devices';
      const noun = entity === 'hosts' ? 'hosts' : 'devices';
      return `Used by ${count.toLocaleString()} ${noun}`;
    }
    return '';
  }

  onPick(entry: FleetFilterKeyEntry | FleetGroupByKeyEntry) {
    if (this.isInert(entry)) return;
    this.close();

    if (this.mode() === 'groupby') {
      const gb = entry as FleetGroupByKeyEntry;
      const currentGb = this.store.groupByKeys?.() || [];
      if (!currentGb.includes(gb.key) && currentGb.length < 3) {
        this.store.openQuickGroupBy(gb.key, gb.displayName);
      }
      return;
    }

    const filter = entry as FleetFilterKeyEntry;
    const displayName = this.getEntryName(filter);
    this.store.openQuickFilter(filter.key, displayName, filter.metadata);
  }

  private fetchCatalog() {
    if (!this.searchService) {
      this.isLoading.set(false);
      return;
    }
    this.isLoading.set(true);
    const q = this.query().trim();

    const activeFilters =
      this.data?.activeFilters ||
      (this.store as unknown as {effectiveFilters?: () => Filter[]})
        .effectiveFilters?.() ||
      [];

    const entity =
      this.data?.entity ??
      (this.store.entity?.() === 'hosts'
        ? SearchEntity.SEARCH_ENTITY_HOST
        : SearchEntity.SEARCH_ENTITY_DEVICE);
    const fleet =
      this.data?.fleet ??
      (this.store.fleet?.() === 'ats' ? Fleet.FLEET_ATS : Fleet.FLEET_SELF);

    if (this.mode() === 'groupby') {
      this.searchService
        .getFleetGroupByKeyCatalog({
          entity,
          fleet,
          query: q,
          filters: activeFilters,
          groupBy: this.data?.activeGroupBy || this.store.groupByKeys?.() || [],
        })
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (res: FleetGroupByKeyCatalogResponse) => {
            this.groupByCatalog.set(res);
            this.isLoading.set(false);
          },
          error: () => {
            this.groupByCatalog.set(null);
            this.isLoading.set(false);
          },
        });
      return;
    }

    this.searchService
      .getFleetFilterKeyCatalog({
        entity,
        fleet,
        query: q,
        filters: activeFilters,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res: FleetFilterKeyCatalogResponse) => {
          this.filterCatalog.set(res);
          this.isLoading.set(false);
        },
        error: () => {
          this.filterCatalog.set(null);
          this.isLoading.set(false);
        },
      });
  }
}
