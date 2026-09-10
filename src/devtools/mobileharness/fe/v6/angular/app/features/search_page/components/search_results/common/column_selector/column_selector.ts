import {
  CdkDrag,
  CdkDragDrop,
  CdkDragHandle,
  CdkDropList,
  moveItemInArray,
} from '@angular/cdk/drag-drop';
import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  linkedSignal,
  signal,
} from '@angular/core';
import {rxResource, toObservable, toSignal} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {of} from 'rxjs';
import {catchError, debounceTime, distinctUntilChanged} from 'rxjs/operators';

import {
  Filter,
  FleetColumnCatalogEntry,
  FleetColumnCatalogRequest,
  FleetColumnCatalogResponse,
  FleetColumnCatalogSection,
  FleetColumnDescriptor,
} from '../../../../../../core/models/search';
import {SEARCH_SERVICE} from '../../../../../../core/services/search/search_service';
import {Dialog} from '../../../../../../shared/components/dialog/dialog';
import {
  ColumnSelectorDialogData,
  ColumnSelectorResult,
  EntityType,
} from '../../../../models';
import {
  toFleetProto,
  toSearchEntityProto,
} from '../../../../utils';

/**
 * Material 3 two-pane Column Selector Dialog for customizing visible table columns.
 *
 * Left pane: Selected columns with drag-and-drop reordering, removal, and locked status.
 * Right pane: Categorized catalog sections (Suggested, Built-in, Dimensions, Host properties) with live search.
 */
@Component({
  selector: 'app-column-selector',
  standalone: true,
  templateUrl: './column_selector.ng.html',
  styleUrl: './column_selector.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    CdkDrag,
    CdkDragHandle,
    CdkDropList,
    FormsModule,
    MatCheckboxModule,
    MatDialogModule,
    MatIconModule,
    Dialog,
  ],
})
export class ColumnSelectorComponent {
  // Dialog Injections (for modal usage)
  private readonly data = inject<ColumnSelectorDialogData>(MAT_DIALOG_DATA, {
    optional: true,
  });
  private readonly dialogRef = inject(
    MatDialogRef<ColumnSelectorComponent, ColumnSelectorResult>,
    {optional: true},
  );
  private readonly searchService = inject(SEARCH_SERVICE);

  /** Effective entity type (devices or hosts). */
  readonly entity = computed<EntityType>(() => this.data?.entity ?? 'devices');

  /** Effective fleet partition (internal or ats). */
  readonly fleet = computed<string>(() => this.data?.fleet ?? 'internal');

  /** Active search filters passed from query context. */
  readonly activeFilters = computed<Filter[]>(
    () => this.data?.activeFilters ?? [],
  );

  /** Default column descriptors fallback when resetting. */
  readonly defaultColumns = computed<FleetColumnDescriptor[]>(() => {
    return this.data?.defaultColumns ?? [];
  });

  /** Locked identity column keys derived from locked descriptors. */
  readonly lockedColumns = computed<string[]>(() => {
    const defaults = this.defaultColumns();
    const lockedFromDefaults = defaults
      .filter((c) => c.locked)
      .map((c) => c.key);
    if (lockedFromDefaults.length > 0) {
      return lockedFromDefaults;
    }
    const current = this.data?.columns ?? [];
    return current.filter((c) => c.locked).map((c) => c.key);
  });

  /** Initial selected column descriptors from dialog data. */
  private readonly initialColumns = computed<FleetColumnDescriptor[]>(() => {
    const fromData = this.data?.columns;
    if (fromData && fromData.length > 0) {
      return fromData;
    }
    return this.defaultColumns();
  });

  /** Working draft array of selected column descriptors. */
  readonly draftColumns = linkedSignal<FleetColumnDescriptor[]>(() =>
    this.initialColumns(),
  );

  /** Whether the user has explicitly requested a reset to default configuration. */
  readonly isReset = signal<boolean>(false);

  /** Search input text. */
  readonly searchQuery = signal<string>('');

  /** Debounced search query passed to catalog RPC (200ms debounce). */
  readonly debouncedQuery = toSignal(
    toObservable(this.searchQuery).pipe(
      debounceTime(200),
      distinctUntilChanged(),
    ),
    {initialValue: ''},
  );

  /** Current non-locked column keys passed to backend for recommendation context. */
  readonly recentKeys = computed<string[]>(() => {
    return this.initialColumns()
      .filter((c) => !c.locked)
      .map((c) => c.key);
  });

  /** Reactive resource fetching column catalog from backend. */
  readonly catalogResource = rxResource<
    FleetColumnCatalogResponse | null,
    {
      entity: EntityType;
      fleet: string;
      query: string;
      filters: Filter[];
      recentKeys: string[];
    }
  >({
    params: () => ({
      entity: this.entity(),
      fleet: this.fleet(),
      query: this.debouncedQuery(),
      filters: this.activeFilters(),
      recentKeys: this.recentKeys(),
    }),
    stream: ({params: req}) => {
      if (!req) return of(null);
      const fleetReq: FleetColumnCatalogRequest = {
        entity: toSearchEntityProto(req.entity),
        fleet: toFleetProto(req.fleet),
        query: req.query || undefined,
        filters: req.filters.length > 0 ? req.filters : undefined,
        recentKeys: req.recentKeys.length > 0 ? req.recentKeys : undefined,
      };

      return this.searchService
        .getFleetColumnCatalog(fleetReq)
        .pipe(catchError(() => of(null)));
    },
  });

  /** Pre-formatted catalog sections using FleetColumnCatalogSection model directly from protobuf response. */
  readonly viewCatalogSections = computed<FleetColumnCatalogSection[]>(() => {
    return (this.catalogResource.value()?.sections || []).filter(
      (s) => (s.entries?.length ?? 0) > 0,
    );
  });

  /** Total count of matching entries across all visible catalog sections. */
  readonly matchedColumnsCount = computed<number>(() => {
    return this.viewCatalogSections().reduce(
      (sum, s) => sum + (s.entries?.length || 0),
      0,
    );
  });

  /** Whether the catalog is loading. */
  readonly isCatalogLoading = computed<boolean>(() => {
    return this.catalogResource.isLoading();
  });

  /** Unit label for device/host counts. */
  readonly entityUnit = computed<string>(() => {
    return this.entity() === 'hosts' ? 'hosts' : 'devices';
  });

  /** Selected column keys set for O(1) template lookup. */
  readonly selectedKeySet = computed<Set<string>>(() => {
    return new Set(this.draftColumns().map((col) => col.key));
  });

  /** Locked column keys set for O(1) template lookup. */
  readonly lockedKeySet = computed<Set<string>>(() => {
    return new Set(this.lockedColumns());
  });

  /** Checks whether a given column key is locked. */
  isLocked(key: string): boolean {
    return this.lockedKeySet().has(key);
  }

  /** Checks whether a column key is currently selected in draft (O(1)). */
  isSelected(key: string): boolean {
    return this.selectedKeySet().has(key);
  }

  /** Handles input in search box. */
  onSearchInput(event: Event) {
    const val = (event.target as HTMLInputElement).value;
    this.searchQuery.set(val);
  }

  /** Clears search query. */
  clearSearch() {
    this.searchQuery.set('');
  }

  /** Toggles column selection checkbox from catalog. */
  toggleColumn(entry: FleetColumnCatalogEntry, checked: boolean) {
    if (this.isLocked(entry.key)) return;
    this.isReset.set(false);

    if (checked && !this.isSelected(entry.key)) {
      this.draftColumns.update((cols) => [
        ...cols,
        {key: entry.key, displayName: entry.displayName},
      ]);
    } else if (!checked) {
      this.draftColumns.update((cols) =>
        cols.filter((col) => col.key !== entry.key),
      );
    }
  }

  /** Removes a column from the draft selection. */
  removeColumn(key: string) {
    if (this.isLocked(key)) return;
    this.isReset.set(false);
    this.draftColumns.update((cols) => cols.filter((col) => col.key !== key));
  }

  /** Handles drag-and-drop reordering. Locked columns cannot be displaced. */
  drop(event: CdkDragDrop<FleetColumnDescriptor[]>) {
    if (event.previousIndex === event.currentIndex) return;

    const currentDraft = [...this.draftColumns()];
    const lockedCount = currentDraft.filter((col) =>
      this.isLocked(col.key),
    ).length;

    // Do not allow moving locked columns
    if (event.previousIndex < lockedCount) return;

    this.isReset.set(false);
    const targetIndex = Math.max(lockedCount, event.currentIndex);
    moveItemInArray(currentDraft, event.previousIndex, targetIndex);
    this.draftColumns.set(currentDraft);
  }

  /** Resets draft columns to default configuration. */
  reset() {
    this.draftColumns.set(this.defaultColumns());
    this.isReset.set(true);
  }

  /** Closes dialog without saving changes. */
  cancel() {
    this.dialogRef?.close();
  }

  /** Applies draft column selection and returns typed descriptors to dialog caller. */
  apply() {
    this.dialogRef?.close({
      columns: this.draftColumns(),
      isReset: this.isReset(),
    });
  }
}
