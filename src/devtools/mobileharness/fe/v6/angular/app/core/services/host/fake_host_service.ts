import {Injectable} from '@angular/core';
import {Observable, of, throwError} from 'rxjs';
import {HostOverview} from '../../models/host_overview';
import {MOCK_HOST_SCENARIOS} from '../mock_data';
import {HostService} from './host_service';

/**
 * A fake implementation of the HostService for development and testing.
 * It uses the mock data defined in the central mock_data registry.
 */
@Injectable({
  providedIn: 'root',
})
export class FakeHostService extends HostService {
  constructor() {
    super();
  }

  /**
   * Retrieves the detailed overview data for a specific host by its name
   * from the mock dataset.
   * @param hostName The name of the host.
   * @returns An Observable emitting the HostOverview data if found,
   *          or an error Observable if not found.
   */
  override getHostOverview(hostName: string): Observable<HostOverview> {
    const scenario = MOCK_HOST_SCENARIOS.find((s) => s.hostName === hostName);
    if (scenario && scenario.overview) {
      return of(scenario.overview);
    } else {
      return throwError(
        () =>
          new Error(
            `Host with '${hostName}' not found or has no overview in mock data.`,
          ),
      );
    }
  }
}
