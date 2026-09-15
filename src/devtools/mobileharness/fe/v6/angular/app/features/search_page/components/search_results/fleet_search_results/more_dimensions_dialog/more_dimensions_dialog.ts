import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {Subject} from 'rxjs';
import {debounceTime, distinctUntilChanged} from 'rxjs/operators';
import {
  ConfigurableDimension,
  DimensionScope,
} from '../../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../../core/services/config/config_service';
import {Dialog} from '../../../../../../shared/components/dialog/dialog';

/** Reserved system property keys that cannot be manually configured as dimensions. */
export const RESERVED_SYSTEM_DIMENSION_KEYS = new Set([
  'model',
  'sdk_version',
  'battery_level',
  'device_form',
  'cpu_architecture',
  'hardware',
  'wifi',
  'sim_state',
  'oem_unlock',
  'host_name',
  'status',
  'run_target',
  'uuid',
  'id',
  'ip',
  'port',
  'driver',
  'type',
]);

/** Input data passed to MoreDimensionsDialog. */
export interface MoreDimensionsDialogData {
  allowCustomDimensions?: boolean;
}

/**
 * Searchable dialog allowing operators to browse, filter, and pick a dimension
 * to configure, or specify a custom dimension when permitted.
 */
@Component({
  selector: 'app-more-dimensions-dialog',
  standalone: true,
  templateUrl: './more_dimensions_dialog.ng.html',
  styleUrl: './more_dimensions_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    Dialog,
    FormsModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
})
export class MoreDimensionsDialog implements OnInit {
  readonly dialogData: MoreDimensionsDialogData =
    inject(MAT_DIALOG_DATA, {optional: true}) || {};
  readonly dialogRef = inject(
    MatDialogRef<MoreDimensionsDialog, ConfigurableDimension | null>,
  );
  private readonly configService = inject(CONFIG_SERVICE);
  private readonly destroyRef = inject(DestroyRef);

  /** Subject to debounce search queries. */
  private readonly searchSubject = new Subject<string>();

  /** Current query text entered in the search field. */
  readonly searchQuery = signal<string>('');

  /** Configurable dimensions loaded from the backend. */
  readonly dimensions = signal<ConfigurableDimension[]>([]);

  /** Whether the backend allows custom dimensions. */
  readonly allowCustomDimensions = signal<boolean>(
    this.dialogData.allowCustomDimensions ?? true,
  );

  /** Loading state for fetching dimensions. */
  readonly isLoading = signal<boolean>(false);

  /** Error message if loading dimensions fails. */
  readonly loadError = signal<string | null>(null);

  /** Normalized query string. */
  readonly normalizedQuery = computed<string>(() =>
    this.searchQuery().trim().toLowerCase(),
  );

  /** Whether the current query is a reserved system property key. */
  readonly isReservedSystemKey = computed<boolean>(() =>
    RESERVED_SYSTEM_DIMENSION_KEYS.has(this.normalizedQuery()),
  );

  /** Whether the current query exactly matches any returned dimension key. */
  readonly isExactMatch = computed<boolean>(() => {
    const q = this.normalizedQuery();
    if (!q) return false;
    return this.dimensions().some((d) => d.key.toLowerCase() === q);
  });

  /** Whether to show the affordance to use the query as a new custom dimension. */
  readonly showCustomAffordance = computed<boolean>(() => {
    const q = this.normalizedQuery();
    return (
      q.length > 0 &&
      !this.isReservedSystemKey() &&
      !this.isExactMatch() &&
      this.allowCustomDimensions()
    );
  });

  ngOnInit() {
    this.searchSubject
      .pipe(debounceTime(180), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((query) => {
        this.fetchDimensions(query);
      });

    this.fetchDimensions('');
  }

  /** Handles user typing in the search input box. */
  onSearchInput(value: string) {
    this.searchQuery.set(value);
    this.searchSubject.next(value);
  }

  /** Clears search input. */
  clearSearch() {
    this.searchQuery.set('');
    this.searchSubject.next('');
  }

  /** Fetches configurable dimensions matching query from the backend. */
  fetchDimensions(query: string) {
    const q = query.trim();
    if (RESERVED_SYSTEM_DIMENSION_KEYS.has(q.toLowerCase())) {
      this.dimensions.set([]);
      this.isLoading.set(false);
      return;
    }

    this.isLoading.set(true);
    this.loadError.set(null);

    this.configService.getConfigurableDimensions({query: q}).subscribe({
      next: (res) => {
        this.dimensions.set(res.dimensions || []);
        if (res.allowCustomDimensions !== undefined) {
          this.allowCustomDimensions.set(res.allowCustomDimensions);
        }
        this.isLoading.set(false);
      },
      error: (err) => {
        this.loadError.set(err?.message || 'Failed to load dimensions');
        this.isLoading.set(false);
      },
    });
  }

  /** Selects an existing dimension from the list. */
  selectDimension(dimension: ConfigurableDimension) {
    this.dialogRef.close(dimension);
  }

  /** Selects the typed search query as a custom dimension. */
  selectCustomDimension() {
    const key = this.searchQuery().trim();
    if (!key || this.isReservedSystemKey()) return;

    this.dialogRef.close({
      key,
      displayName: key,
      scope: DimensionScope.SUPPORTED,
    });
  }

  /** Closes the dialog without picking a dimension. */
  close() {
    this.dialogRef.close(null);
  }
}
