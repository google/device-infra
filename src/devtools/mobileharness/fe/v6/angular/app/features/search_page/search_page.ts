import {CommonModule} from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  inject,
  Injector,
  OnInit,
  viewChild,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';

import {LoadingService} from '../../shared/services/loading_service';
import {SearchBox} from './components/search_box/search_box';
import {FleetSearchResultsComponent} from './components/search_results/fleet_search_results/fleet_search_results';
import {TjsSearchResultsComponent} from './components/search_results/tjs_search_results/tjs_search_results';
import {SearchPageStore} from './services/search_page_store';

/** Search page containing search input, filters, presets, and result views. */
@Component({
  selector: 'app-search-page',
  standalone: true,
  templateUrl: './search_page.ng.html',
  styleUrl: './search_page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    FleetSearchResultsComponent,
    TjsSearchResultsComponent,
    SearchBox,
  ],
})
export class SearchPage implements OnInit {
  readonly store = inject(SearchPageStore);
  private readonly loadingService = inject(LoadingService);
  private readonly injector = inject(Injector);

  readonly searchBox = viewChild(SearchBox);

  ngOnInit() {
    this.loadingService.hide();
  }

  fillSearch(text: string) {
    this.store.searchQuery.set(text);
    this.store.showSuggestions.set(true);
    afterNextRender(
      () => {
        this.searchBox()?.focusInput();
      },
      {injector: this.injector},
    );
  }

  onBrowseAll() {
    this.store.browseAll.set(true);
    this.store.executeSearch();
  }
}
