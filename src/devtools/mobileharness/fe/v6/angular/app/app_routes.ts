import {inject} from '@angular/core';
import {CanActivateFn, ParamMap, Router, Routes} from '@angular/router';
import {APP_DATA, type AppData} from './core/models/app_data';
import {AssistantPage} from './features/assistant/assistant_page';
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
 * Checks whether the OmniLab Assistant feature is enabled.
 * True if enabled via query param, localStorage, or dev mode.
 */
export function isAssistantEnabled(
  queryParamMap?: ParamMap | null,
  appData?: AppData | null,
): boolean {
  const queryParam = queryParamMap?.get('enable_assistant');
  if (queryParam === 'false') {
    return false;
  }
  if (queryParam === 'true') {
    return true;
  }
  try {
    if (
      typeof window !== 'undefined' &&
      window.localStorage?.getItem('enable_assistant') === 'true'
    ) {
      return true;
    }
  } catch {
    // Ignore localStorage access errors.
  }
  return appData?.isDevMode ?? false;
}

/**
 * Router Activation Guard for OmniLab Assistant.
 * Redirects to '/home' unless the feature is enabled.
 */
export const assistantFeatureGuard: CanActivateFn = (route, state) => {
  const router = inject(Router);
  const appData = inject(APP_DATA, {optional: true});

  if (isAssistantEnabled(route.queryParamMap, appData)) {
    return true;
  }

  return router.parseUrl('/home');
};

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
 * The application routes.
 */
export const routes: Routes = [
  {
    path: 'home',
    component: HomePage,
    title: 'Home',
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
    path: 'assistant',
    component: AssistantPage,
    title: 'Assistant',
    canActivate: [assistantFeatureGuard],
  },
  {
    path: '',
    redirectTo: 'home',
    pathMatch: 'full',
  },
];
