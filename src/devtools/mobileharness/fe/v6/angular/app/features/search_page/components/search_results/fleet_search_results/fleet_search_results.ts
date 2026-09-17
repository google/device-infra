import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import {MatCardModule} from '@angular/material/card';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatDialog, MatDialogModule} from '@angular/material/dialog';
import {MatDividerModule} from '@angular/material/divider';
import {MatIconModule} from '@angular/material/icon';
import {MatMenu, MatMenuModule, MatMenuTrigger} from '@angular/material/menu';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatSelectModule} from '@angular/material/select';
import {MatTableModule} from '@angular/material/table';

import {ConfigurableDimension} from '../../../../../core/models/device_config_models';
import {Column} from '../../../../../core/models/search';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {FleetSearchStore} from '../../../services/fleet_search_store';

import {ColumnSelectorResult} from '../../../models';
import {ColumnSelectorComponent} from '../common/column_selector/column_selector';
import {DensityDropdownComponent} from '../common/density_dropdown/density_dropdown';
import {SearchCellComponent} from '../common/search_cell/search_cell';
import {SearchPaginationComponent} from '../common/search_pagination/search_pagination';
import {FleetGroupCardComponent} from '../fleet_group_card/fleet_group_card';
import {
  BulkConfigDimensionDialog,
  BulkConfigDimensionDialogResult,
} from './bulk_config_dimension_dialog/bulk_config_dimension_dialog';
import {
  BulkConfigWifiDialog,
  BulkConfigWifiDialogResult,
} from './bulk_config_wifi_dialog/bulk_config_wifi_dialog';
import {MoreDimensionsDialog} from './more_dimensions_dialog/more_dimensions_dialog';

/** Component representing search results for lab fleet (devices/hosts). */
@Component({
  selector: 'app-fleet-search-results',
  standalone: true,
  templateUrl: './fleet_search_results.ng.html',
  styleUrl: './fleet_search_results.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatCardModule,
    MatCheckboxModule,
    MatDialogModule,
    MatDividerModule,
    MatIconModule,
    MatMenuModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
    DensityDropdownComponent,
    FleetGroupCardComponent,
    SearchPaginationComponent,
    SearchCellComponent,
  ],
})
export class FleetSearchResultsComponent {
  readonly store = inject(FleetSearchStore);
  private readonly dialog = inject(MatDialog);

  /** Label representing the current fleet partition. */
  readonly fleetLabel = computed<string>(() =>
    this.store.fleet() === 'internal' ? 'Google Internal' : 'ATS Labs',
  );

  /** Derived table columns array including the selection checkbox column. */
  readonly columnsToDisplay = computed<string[]>(() => {
    const cols = this.store.displayColumns().map((col: Column) => col.key);
    if (cols.length === 0) return [];
    return ['checkbox', ...cols];
  });

  /** Display mode for the grouped results accordion. */
  readonly groupedViewMode = computed<
    'groups' | 'empty_filtered' | 'empty_all' | 'loading'
  >(() => {
    if (this.store.groups().length > 0) return 'groups';
    if (this.store.isLoading()) return 'loading';
    return this.store.hasActiveFilters() ? 'empty_filtered' : 'empty_all';
  });

  /** Display mode for the flat table results view. */
  readonly flatTableViewMode = computed<
    'table' | 'empty_filtered' | 'empty_all'
  >(() => {
    if (this.store.rows().length > 0 || this.store.isLoading()) {
      return 'table';
    }
    return this.store.hasActiveFilters() ? 'empty_filtered' : 'empty_all';
  });

  /** Display mode for the select-all-matching banner. */
  readonly selectedBannerMode = computed<
    'all_matching' | 'page_only' | 'hidden'
  >(() => {
    if (!this.store.showSelectAllMatchingBanner()) return 'hidden';
    return this.store.selectAllMatching() ? 'all_matching' : 'page_only';
  });

  /** Text label displayed in the toolbar when rows are selected in flat table mode. */
  readonly flatToolbarSelectedText = computed<string>(() => {
    const count = this.store.selectedCount().toLocaleString();
    const entity = this.store.entity();
    const label = this.fleetLabel();
    if (this.store.selectAllMatching()) {
      return `All ${count} matching ${entity} selected · ${label}`;
    }
    return `${count} selected · ${label}`;
  });

  /** Whether general batch actions can be performed on selected items. */
  readonly canShowBatchActions = computed<boolean>(
    () => !this.store.selectAllMatching(),
  );

  /** Whether device-specific batch actions can be performed. */
  readonly canShowDeviceBatchActions = computed<boolean>(
    () => !this.store.selectAllMatching() && this.store.entity() === 'devices',
  );

  /** Opens the column selector dialog to configure visible table columns. */
  openColumnSelector() {
    const dialogRef = this.dialog.open<
      ColumnSelectorComponent,
      unknown,
      ColumnSelectorResult
    >(ColumnSelectorComponent, {
      panelClass: 'column-selector-dialog-panel',
      data: {
        entity: this.store.entity(),
        fleet: this.store.fleet(),
        columns: this.store.visibleColumnDescriptors(),
        defaultColumns: this.store.searchConfig()?.columns?.defaults ?? [],
        activeFilters: this.store.effectiveFilters(),
      },
    });

    dialogRef.afterClosed().subscribe((res?: ColumnSelectorResult) => {
      if (!res) return;
      if (res.isReset) {
        this.store.resetVisibleColumns();
      } else if (res.columns && res.columns.length > 0) {
        this.store.setVisibleColumns(res.columns);
      }
    });
  }

  /** Opens the Bulk Wi-Fi configuration dialog for currently selected devices. */
  openBulkConfigWifiDialog() {
    const selected = Array.from(this.store.selectedItems());
    if (selected.length === 0) return;

    const dialogRef = this.dialog.open<
      BulkConfigWifiDialog,
      unknown,
      BulkConfigWifiDialogResult
    >(BulkConfigWifiDialog, {
      panelClass: 'bulk-config-wifi-dialog-panel',
      autoFocus: false,
      data: {
        deviceIds: selected,
      },
    });

    dialogRef.afterClosed().subscribe((res?: BulkConfigWifiDialogResult) => {
      if (res?.updated) {
        this.store.clearSelection();
        this.store.executeSearch();
      }
    });
  }

  private readonly configService = inject(CONFIG_SERVICE);

  /** Configurable dimensions loaded from backend for the Configuration dropdown. */
  readonly configurableDimensions = signal<ConfigurableDimension[]>([]);

  /** Whether custom dimensions are allowed by backend. */
  readonly allowCustomDimensions = signal<boolean>(false);

  /** Loading state for configurable dimensions button. */
  readonly isLoadingDimensions = signal<boolean>(false);

  /** Whether configurable dimensions have already been loaded. */
  readonly hasLoadedDimensions = signal<boolean>(false);

  /** Loads configurable dimensions when clicking the Configuration button. */
  loadConfigurableDimensions(trigger?: MatMenuTrigger, menu?: MatMenu) {
    if (this.isLoadingDimensions()) return;
    if (this.hasLoadedDimensions()) {
      return;
    }

    this.isLoadingDimensions.set(true);
    this.configService.getConfigurableDimensions().subscribe({
      next: (res) => {
        this.configurableDimensions.set(res.dimensions || []);
        this.allowCustomDimensions.set(!!res.allowCustomDimensions);
        this.hasLoadedDimensions.set(true);
        this.isLoadingDimensions.set(false);
        if (trigger && menu) {
          trigger.menu = menu;
          setTimeout(() => {
            trigger.openMenu();
          });
        }
      },
      error: () => {
        this.isLoadingDimensions.set(false);
      },
    });
  }

  /** Opens the Bulk Dimension configuration dialog for the selected dimension. */
  openBulkConfigDimensionDialog(dimension: ConfigurableDimension) {
    const selected = Array.from(this.store.selectedItems());
    if (selected.length === 0) return;

    const dialogRef = this.dialog.open<
      BulkConfigDimensionDialog,
      unknown,
      BulkConfigDimensionDialogResult
    >(BulkConfigDimensionDialog, {
      panelClass: 'bulk-config-dimension-dialog-panel',
      autoFocus: false,
      data: {
        deviceIds: selected,
        dimension,
      },
    });

    dialogRef.afterClosed().subscribe((res?: BulkConfigDimensionDialogResult) => {
      if (res?.updated) {
        this.store.clearSelection();
        this.store.executeSearch();
      }
    });
  }

  /** Opens the More Dimensions dialog to search, browse, or specify custom dimension. */
  openMoreDimensionsDialog() {
    const dialogRef = this.dialog.open<
      MoreDimensionsDialog,
      unknown,
      ConfigurableDimension | null
    >(MoreDimensionsDialog, {
      panelClass: 'more-dimensions-dialog-panel',
      autoFocus: false,
      data: {
        allowCustomDimensions: this.allowCustomDimensions(),
      },
    });

    dialogRef.afterClosed().subscribe((dimension?: ConfigurableDimension | null) => {
      if (dimension) {
        this.openBulkConfigDimensionDialog(dimension);
      }
    });
  }
}
