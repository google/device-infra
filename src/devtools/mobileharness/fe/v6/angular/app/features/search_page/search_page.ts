import {DecimalPipe} from '@angular/common';
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
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {ActivatedRoute, Router} from '@angular/router';

import {ArsenalSavedQuery} from '../../core/models/search_fleet';
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
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
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
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

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

          const params: Record<string, string | string[]> = {};
          if (filters.length > 0) params['f'] = filters;
          if (groupBys.length > 0) params['gb'] = groupBys.join(',');

          this.urlService.notifySearchStateChanged(params);
        },
        {injector: this.injector},
      );
    }

    // Automatically focus the search input when arriving on the search page with an initial `q` query parameter.
    effect(
      () => {
        if (this.store.focusInputTrigger?.()) {
          this.focusSearchBox();
        }
      },
      {injector: this.injector},
    );

    this.loadingService.hide();
  }

  fillSearch(text: string) {
    this.store.searchQuery.set(text);
    this.store.showSuggestions.set(true);
    this.focusSearchBox();
  }

  private focusSearchBox() {
    const box = this.searchBox();
    if (box) {
      box.focusInput();
    } else {
      afterNextRender(
        () => {
          this.searchBox()?.focusInput();
        },
        {injector: this.injector},
      );
    }
  }

  onBrowseAll() {
    this.store.browseAll.set(true);
    this.store.executeSearch();
  }

  /**
   * Opens an Alkali Arsenal saved query. The search store translates the
   * `arsenal_query` parameter into FE v6 search state and runs the search.
   */
  openArsenalSavedQuery(query: ArsenalSavedQuery) {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {'arsenal_query': query.arsenalQuery},
    });
  }
}
