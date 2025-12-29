import {Injectable} from '@angular/core';
import {Observable, of, throwError} from 'rxjs';

import {
  CheckProxyResponse,
  DeviceProxyType,
  DeviceSummary,
  HostOverview,
} from '../../models/host_overview';
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

  override getHostDeviceSummaries(
    hostName: string,
  ): Observable<DeviceSummary[]> {
    const scenario = MOCK_HOST_SCENARIOS.find((s) => s.hostName === hostName);
    if (scenario && scenario.deviceSummaries) {
      return of(scenario.deviceSummaries);
    }
    return of([]);
  }

  override updatePassThroughFlags(
    hostName: string,
    flags: string,
  ): Observable<void> {
    const scenario = MOCK_HOST_SCENARIOS.find((s) => s.hostName === hostName);
    if (scenario && scenario.overview) {
      scenario.overview.labServer.passThroughFlags = flags;
      return of(undefined);
    } else {
      return throwError(
        () =>
          new Error(
            `Host with '${hostName}' not found or has no overview in mock data.`,
          ),
      );
    }
  }

  override decommissionMissingDevices(
    hostName: string,
    deviceControlIds: string[],
  ): Observable<void> {
    const scenario = MOCK_HOST_SCENARIOS.find((s) => s.hostName === hostName);
    if (scenario && scenario.deviceSummaries) {
      scenario.deviceSummaries = scenario.deviceSummaries.filter(
        (d) => !deviceControlIds.includes(d.id),
      );
      return of(undefined);
    } else {
      return throwError(
        () =>
          new Error(
            `Host with '${hostName}' not found or has no devices in mock data.`,
          ),
      );
    }
  }

  override checkCommonRemoteControlProxy(
    hostName: string,
    deviceControlIds: string[],
  ): Observable<CheckProxyResponse> {
    if (
      hostName === 'host-rc-proxy-mismatch.example.com' &&
      deviceControlIds.length > 1
    ) {
      const capabilities: {[deviceId: string]: DeviceProxyType[]} = {};
      deviceControlIds.forEach((id) => {
        // Mock mismatch: First device supports ADB, others support nothing (simplification)
        capabilities[id] =
          id === deviceControlIds[0]
            ? [DeviceProxyType.ADB_AND_VIDEO, DeviceProxyType.USB_IP]
            : [DeviceProxyType.SSH];
      });
      return of({
        commonProxyTypes: [],
        deviceCapabilities: capabilities,
      });
    }
    return of({
      commonProxyTypes: [DeviceProxyType.ADB_AND_VIDEO],
      deviceCapabilities: {}, // Can be empty if common types exist, or populated
    });
  }
}
