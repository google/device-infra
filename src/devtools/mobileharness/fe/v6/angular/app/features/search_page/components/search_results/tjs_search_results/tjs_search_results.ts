import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTableModule} from '@angular/material/table';

import {Column} from '../../../../../core/models/search';
import {TjsSearchStore} from '../../../services/tjs_search_store';
import {DensityDropdownComponent} from '../common/density_dropdown/density_dropdown';
import {SearchCellComponent} from '../common/search_cell/search_cell';
import {SearchPaginationComponent} from '../common/search_pagination/search_pagination';

/** Component representing search results for TJS search entity types. */
@Component({
  selector: 'app-tjs-search-results',
  standalone: true,
  templateUrl: './tjs_search_results.ng.html',
  styleUrl: './tjs_search_results.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    DensityDropdownComponent,
    SearchPaginationComponent,
    SearchCellComponent,
  ],
})
export class TjsSearchResultsComponent {
  readonly store = inject(TjsSearchStore);

  /** Derived table columns array for mat-table header and row definitions. */
  readonly columnsToDisplay = computed<string[]>(() => {
    return this.store.displayColumns().map((col: Column) => col.key);
  });
}
