import {inject} from '@angular/core';
import {CanActivateFn, Router, Routes} from '@angular/router';
import {DevHarnessPage} from './features/dev_harness/dev_harness_page';
import {DeviceDetailPage} from './features/device_detail/device_detail_page';
import {HostDetail} from './features/host_detail/host_detail';
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
 * The application routes.
 */
export const routes: Routes = [
  {
    path: 'dev/device-harness',
    component: DevHarnessPage,
  },
  {
    path: 'devices/:id',
    component: DeviceDetailPage, // Changed from loadComponent
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
    path: '',
    redirectTo: 'dev/device-harness',
    pathMatch: 'full',
  },
];
