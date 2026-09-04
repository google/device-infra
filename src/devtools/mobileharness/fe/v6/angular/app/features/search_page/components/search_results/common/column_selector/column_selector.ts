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
import {toFleetProto, toSearchEntityProto} from '../../../../utils';

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

  /** Default columns fallback when resetting. */
  readonly defaultColumns = computed<string[]>(() => {
    return this.data?.defaultColumns ?? [];
  });

  /** Locked identity columns pinned to the beginning. */
  readonly lockedColumns = computed<string[]>(() => {
    return this.data?.lockedColumns ?? [];
  });

  /** Initial selected columns from dialog data. */
  private readonly initialColumns = computed<string[]>(() => {
    const fromData = this.data?.selectedColumns;
    if (fromData && fromData.length > 0) {
      return fromData;
    }
    return this.defaultColumns();
  });

  /** Working draft array of selected column keys. */
  readonly draftColumns = linkedSignal<string[], string[]>({
    source: () => this.initialColumns(),
    computation: (initial) => this.normalizeInitialDraft(initial),
  });

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

  /** Active recent column keys passed explicitly or derived from non-locked selected columns. */
  readonly recentKeys = computed<string[]>(() => {
    const explicit = this.data?.recentKeys;
    if (explicit && explicit.length > 0) {
      return explicit;
    }
    const locked = this.lockedColumns();
    return this.initialColumns().filter((k) => !locked.includes(k));
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
    return this.catalogResource.value()?.sections || [];
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

  /** Map of key -> displayName derived from pre-formatted catalog ViewModels. */
  readonly knownDisplayNameMap = computed<Map<string, string>>(() => {
    const allEntries = this.viewCatalogSections().flatMap(
      (s) => s.entries ?? [],
    );
    const map = new Map<string, string>();
    for (const entry of allEntries) {
      if (entry.displayName) {
        map.set(entry.key, entry.displayName);
      }
    }
    return map;
  });

  /** Normalizes initial column list ensuring locked columns appear first. */
  private normalizeInitialDraft(initial: string[]): string[] {
    const locked = this.lockedColumns();
    const activeLocked = initial.filter((k) => locked.includes(k));
    const nonLocked = initial.filter((k) => !locked.includes(k));
    // If none of the locked columns were in initial, add the primary locked column
    if (activeLocked.length === 0 && locked.length > 0) {
      return [locked[0], ...nonLocked];
    }
    return [...activeLocked, ...nonLocked];
  }

  /** Checks whether a given column key is locked. */
  isLocked(key: string): boolean {
    return this.lockedColumns().includes(key);
  }

  /** Checks whether a column key is currently selected in draft. */
  isSelected(key: string): boolean {
    return this.draftColumns().includes(key);
  }

  /** Gets display name for a column key. */
  getColumnDisplayName(key: string): string {
    const catalogName = this.knownDisplayNameMap().get(key);
    if (catalogName) return catalogName;
    return key;
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

  /** Toggles column selection checkbox. */
  toggleColumn(key: string, checked: boolean) {
    if (this.isLocked(key)) return;
    this.isReset.set(false);

    if (checked) {
      if (!this.draftColumns().includes(key)) {
        this.draftColumns.update((cols) => [...cols, key]);
      }
    } else {
      this.draftColumns.update((cols) => cols.filter((k) => k !== key));
    }
  }

  /** Removes a column from the draft selection. */
  removeColumn(key: string) {
    if (this.isLocked(key)) return;
    this.isReset.set(false);
    this.draftColumns.update((cols) => cols.filter((k) => k !== key));
  }

  /** Handles drag-and-drop reordering. Locked columns cannot be displaced. */
  drop(event: CdkDragDrop<string[]>) {
    if (event.previousIndex === event.currentIndex) return;

    const currentDraft = [...this.draftColumns()];
    const lockedCount = currentDraft.filter((k) => this.isLocked(k)).length;

    // Do not allow moving locked columns or dropping before locked columns
    if (event.previousIndex < lockedCount) return;

    this.isReset.set(false);
    const targetIndex = Math.max(lockedCount, event.currentIndex);
    moveItemInArray(currentDraft, event.previousIndex, targetIndex);
    this.draftColumns.set(currentDraft);
  }

  /** Resets draft columns to default configuration. */
  reset() {
    const defs = this.defaultColumns();
    this.draftColumns.set(this.normalizeInitialDraft(defs));
    this.isReset.set(true);
  }

  /** Closes dialog without saving changes. */
  cancel() {
    this.dialogRef?.close();
  }

  /** Applies draft column selection and returns typed descriptors to dialog caller. */
  apply() {
    const finalCols = this.draftColumns();
    const normalizedDefaults = this.normalizeInitialDraft(
      this.defaultColumns(),
    );
    const isReset =
      this.isReset() ||
      (finalCols.length === normalizedDefaults.length &&
        finalCols.every((k, i) => k === normalizedDefaults[i]));

    const columns: FleetColumnDescriptor[] = finalCols.map((key) => ({
      key,
      displayName: this.getColumnDisplayName(key),
    }));

    this.dialogRef?.close({columns, isReset});
  }
}
