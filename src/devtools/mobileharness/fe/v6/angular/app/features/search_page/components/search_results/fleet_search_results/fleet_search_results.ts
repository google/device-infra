import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';
import {MatCardModule} from '@angular/material/card';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatDialog, MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSelectModule} from '@angular/material/select';
import {MatTableModule} from '@angular/material/table';

import {Column} from '../../../../../core/models/search';
import {FleetSearchStore} from '../../../services/fleet_search_store';

import {ColumnSelectorResult} from '../../../models';
import {ColumnSelectorComponent} from '../common/column_selector/column_selector';
import {DensityDropdownComponent} from '../common/density_dropdown/density_dropdown';
import {SearchCellComponent} from '../common/search_cell/search_cell';
import {SearchPaginationComponent} from '../common/search_pagination/search_pagination';
import {FleetGroupCardComponent} from '../fleet_group_card/fleet_group_card';

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
    MatIconModule,
    MatMenuModule,
    MatProgressBarModule,
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

  /** Derived table columns array including the selection checkbox column. */
  readonly columnsToDisplay = computed<string[]>(() => {
    const cols = this.store.displayColumns().map((col: Column) => col.key);
    if (cols.length === 0) return [];
    return ['checkbox', ...cols];
  });

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
        selectedColumns: Array.from(this.store.visibleColumns()),
        lockedColumns: this.store.lockedColumns(),
        defaultColumns: this.store.defaultColumns(),
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
}
