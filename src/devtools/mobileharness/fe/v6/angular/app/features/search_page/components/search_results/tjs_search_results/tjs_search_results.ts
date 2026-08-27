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
import {SearchCellPipe} from '../../../utils';
import {DensityDropdownComponent} from '../common/density_dropdown/density_dropdown';
import {SearchPaginationComponent} from '../common/search_pagination/search_pagination';
import {SearchCellComponent} from '../common/search_cell/search_cell';

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
    SearchCellPipe,
  ],
})
export class TjsSearchResultsComponent {
  readonly store = inject(TjsSearchStore);

  // Context Delegates
  readonly columns = this.store.displayColumns;
  readonly rows = this.store.rows;
  readonly entity = this.store.entity;
  readonly pageIndex = this.store.pageIndex;
  readonly pageSize = this.store.pageSize;
  readonly density = this.store.density;
  readonly isLoading = this.store.isLoading;

  // Derived Signals
  readonly columnsToDisplay = computed<string[]>(() => {
    return this.columns().map((col: Column) => col.key);
  });

  prevPage() {
    this.store.prevPage();
  }

  nextPage() {
    this.store.nextPage();
  }

  readonly tjsNounPlural = computed<string>(() => {
    const e = this.entity();
    if (e === 'tests') return 'Tests';
    if (e === 'jobs') return 'Jobs';
    if (e === 'sessions') return 'Sessions';
    return e;
  });
}
