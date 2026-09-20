import {CommonModule} from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  Injector,
  OnInit,
  viewChild,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';

import {EnvUniverseService} from '../../core/services/env_universe_service';
import {UrlService} from '../../core/services/url_service';
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
  private readonly urlService = inject(UrlService);
  private readonly envUniverseService = inject(EnvUniverseService);

  private readonly searchBox = viewChild(SearchBox);

  /** Active view mode for the search page (landing loading, launcher, or results). */
  readonly pageViewMode = computed<
    'landing_loading' | 'landing_launcher' | 'fleet_results' | 'tjs_results'
  >(() => {
    if (this.store.isLandingState()) {
      return this.store.isConfigLoading() && !this.store.searchConfig()
        ? 'landing_loading'
        : 'landing_launcher';
    }
    return this.store.isTjs() ? 'tjs_results' : 'fleet_results';
  });

  ngOnInit() {
    // Only register the search state synchronization effect in embedded mode
    // within the ATS/OSS environment (e.g. running inside MTT UI2 iframe).
    // In standalone or internal mode, this avoids unnecessary effect scheduling,
    // reactive signal subscriptions, and postMessage overhead.
    if (this.envUniverseService.isAts() && this.urlService.isInEmbeddedMode()) {
      effect(
        () => {
          const filters = this.store.serializedActiveFilters();
          const groupBys = this.store.groupByKeys();

          // const q = this.store.searchQuery();
          // const fleet = this.store.fleet();
          // if (q) params['q'] = q;  // no parent window support q yet.
          // no parent window support fleet yet.
          // if (fleet !== 'internal') params['fleet'] = fleet;

          const params: Record<string, string | string[]> = {};
          if (filters.length > 0) params['f'] = filters;
          if (groupBys.length > 0) params['gb'] = groupBys.join(',');

          this.urlService.notifySearchStateChanged(params);
        },
        {injector: this.injector},
      );
    }

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
