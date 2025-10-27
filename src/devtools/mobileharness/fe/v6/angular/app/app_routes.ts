import {Routes} from '@angular/router';
import {DevHarnessPage} from './features/dev_harness/dev_harness_page';
import {DeviceDetailPage} from './features/device_detail/device_detail_page';

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
    path: '',
    redirectTo: 'dev/device-harness',
    pathMatch: 'full',
  },
];
