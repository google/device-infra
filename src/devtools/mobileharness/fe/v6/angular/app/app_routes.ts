import {inject} from '@angular/core';
import {CanActivateFn, RedirectFunction, Router, Routes} from '@angular/router';
import {AllSiteSearchPage} from './features/all_site_search/all_site_search_page';
import {DevHarnessPage} from './features/dev_harness/dev_harness_page';
import {DeviceDetailPage} from './features/device_detail/device_detail_page';
import {HomePage} from './features/home/home_page';
import {HostDetail} from './features/host_detail/host_detail';
import {JobDetail} from './features/job_detail/job_detail';
import {SearchPage} from './features/search_page/search_page';
import {FleetSearchStore} from './features/search_page/services/fleet_search_store';
import {SearchPageStore} from './features/search_page/services/search_page_store';
import {TjsSearchStore} from './features/search_page/services/tjs_search_store';
import {SessionDetailPage} from './features/session_detail/session_detail';
import {TestDetail} from './features/test_detail/test_detail';

/**
 * Router Activation Guard (Sanitizes the browser address bar on initialization or external entrance).
 * If a user enters a Test/Job detail page from outside the guest app (e.g. bookmarks or direct links)
 * with '?universe=xxx' query param, this guard intercepts the navigation, strips universe from UrlTree,
 * and performs a clean redirection.
 * Note: This gates external/initial entries, while url_utils manages internal nav link transitions,
 * and universeInterceptor shields the backend HTTP request layer.
 */
const stripUniverseGuard: CanActivateFn = (route, state) => {
  const router = inject(Router);
  if (route.queryParamMap.has('universe')) {
    const urlTree = router.parseUrl(state.url);
    delete urlTree.queryParams['universe'];
    return urlTree;
  }
  return true;
};

/**
 * Redirect function for unmatched routes ('**').
 * - Unmatched URLs with no subpaths (single segment, e.g. '/abcd') are treated
 *   as query strings for all-site search: redirects to '/search?q=abcd'.
 * - Unmatched URLs with subpaths (e.g. '/foo/bar') fall back to '/home'.
 * Existing query parameters (e.g. 'is_embedded_mode', 'universe') are preserved.
 */
export const fallbackRedirect: RedirectFunction = (redirectData) => {
  const router = inject(Router);
  const segments = redirectData.url;

  if (segments.length === 1 && segments[0].path) {
    return router.createUrlTree(['/search'], {
      queryParams: {
        ...redirectData.queryParams,
        'q': segments[0].path,
      },
    });
  }

  return router.createUrlTree(['/home'], {
    queryParams: redirectData.queryParams,
  });
};

/**
 * The application routes.
 */
export const routes: Routes = [
  {
    path: 'home',
    component: HomePage,
    title: 'Home',
  },
  {
    path: 'search',
    component: AllSiteSearchPage,
    title: 'Search',
  },
  {
    path: 'dev/device-harness',
    component: DevHarnessPage,
  },
  {
    path: 'devices',
    component: SearchPage,
    providers: [
      FleetSearchStore,
      {provide: SearchPageStore, useExisting: FleetSearchStore},
    ],
  },
  {
    path: 'hosts',
    component: SearchPage,
    providers: [
      FleetSearchStore,
      {provide: SearchPageStore, useExisting: FleetSearchStore},
    ],
  },
  {
    path: 'tests',
    component: SearchPage,
    providers: [
      TjsSearchStore,
      {provide: SearchPageStore, useExisting: TjsSearchStore},
    ],
  },
  {
    path: 'jobs',
    component: SearchPage,
    providers: [
      TjsSearchStore,
      {provide: SearchPageStore, useExisting: TjsSearchStore},
    ],
  },
  {
    path: 'sessions',
    component: SearchPage,
    providers: [
      TjsSearchStore,
      {provide: SearchPageStore, useExisting: TjsSearchStore},
    ],
  },
  {
    path: 'devices/:id',
    component: DeviceDetailPage,
  },
  {
    path: 'hosts/:hostName',
    component: HostDetail,
  },
  {
    path: 'jobs/:jobId/tests/:id',
    component: TestDetail,
    canActivate: [stripUniverseGuard],
  },
  {
    path: 'jobs/:id',
    component: JobDetail,
    canActivate: [stripUniverseGuard],
  },
  {
    path: 'sessions/:id',
    component: SessionDetailPage,
    canActivate: [stripUniverseGuard],
  },
  {
    path: '',
    redirectTo: 'home',
    pathMatch: 'full',
  },
  // Aliases for common typos of the search pages.
  {
    path: 'device',
    redirectTo: 'devices',
    pathMatch: 'full',
  },
  {
    path: 'host',
    redirectTo: 'hosts',
    pathMatch: 'full',
  },
  {
    path: 'test',
    redirectTo: 'tests',
    pathMatch: 'full',
  },
  {
    path: 'job',
    redirectTo: 'jobs',
    pathMatch: 'full',
  },
  {
    path: 'session',
    redirectTo: 'sessions',
    pathMatch: 'full',
  },
  // Fallback: any unknown URL is redirected. Single segments without subpaths
  // redirect to all-site search (/search?q=...), while subpaths fall back to home.
  // This must be the last route.
  {
    path: '**',
    redirectTo: fallbackRedirect,
  },
];
