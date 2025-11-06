import {InjectionToken} from '@angular/core';
import {Observable} from 'rxjs';

import {HostOverview} from '../../models/host_overview';

/**
 * Injection token for the HostService.
 */
export const HOST_SERVICE = new InjectionToken<HostService>('HostService');

/**
 * Abstract class defining the contract for host data operations.
 */
export abstract class HostService {
  /**
   * Retrieves the detailed overview data for a specific host by its name.
   */
  abstract getHostOverview(hostName: string): Observable<HostOverview>;
}
