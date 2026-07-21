import {Routes} from '@angular/router';
import {DevHarnessPage} from './features/dev_harness/dev_harness_page';
import {DeviceDetailPage} from './features/device_detail/device_detail_page';
import {HostDetail} from './features/host_detail/host_detail';
import {JobDetail} from './features/job_detail/job_detail';
import {TestDetail} from './features/test_detail/test_detail';

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
  },
  {
    path: 'jobs/:id',
    component: JobDetail,
  },
  {
    path: '',
    redirectTo: 'dev/device-harness',
    pathMatch: 'full',
  },
];
