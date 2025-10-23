import {Routes} from '@angular/router';
import {DevHarnessPage} from './features/dev_harness/dev_harness_page';
import {DeviceDetailPage} from './features/device_detail/device_detail_page';
import {HostDetail} from './features/host_detail/host_detail';

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
    path: '',
    redirectTo: 'dev/device-harness',
    pathMatch: 'full',
  },
];
