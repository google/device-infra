import {Injectable} from '@angular/core';
import {Observable, of, throwError} from 'rxjs';

import {
  CheckRemoteControlEligibilityResponse,
  DeviceProxyType,
  DeviceSummary,
  EligibleDevice,
  HostOverview,
  IneligibleDevice,
  RemoteControlDevicesRequest,
  RemoteControlDevicesResponse,
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

  override checkRemoteControlEligibility(
    hostName: string,
    deviceControlIds: string[],
  ): Observable<CheckRemoteControlEligibilityResponse> {
    const ineligibleDevices: IneligibleDevice[] = [];
    const eligibleDevices: EligibleDevice[] = [];

    deviceControlIds.forEach((id) => {
      if (id.includes('ineligible-busy')) {
        ineligibleDevices.push({
          deviceId: id,
          reasonCode: 'DEVICE_NOT_IDLE',
          message: `Device ${id} is busy.`,
        });
      } else if (id.includes('ineligible-no-acid')) {
        ineligibleDevices.push({
          deviceId: id,
          reasonCode: 'ACID_NOT_SUPPORTED',
          message: `Device ${id} does not support ACID.`,
        });
      } else if (id.includes('ineligible-no-perm')) {
        ineligibleDevices.push({
          deviceId: id,
          reasonCode: 'PERMISSION_DENIED',
          message: `Permission denied for device ${id}.`,
        });
      } else {
        eligibleDevices.push({
          deviceId: id,
          supportedProxyTypes: [
            DeviceProxyType.ADB_AND_VIDEO,
            DeviceProxyType.SSH,
          ],
          runAsCandidates: ['user1', 'mdb/group1', 'mdb/group2'],
        });
      }
    });

    if (ineligibleDevices.length > 0) {
      return of({
        eligibleDevices,
        ineligibleDevices,
      });
    } else {
      return of({
        eligibleDevices,
        ineligibleDevices,
        sessionOptions: {
          commonProxyTypes: [
            DeviceProxyType.ADB_AND_VIDEO,
            DeviceProxyType.SSH,
          ],
          commonRunAsCandidates: ['user1', 'mdb/group1'],
          maxDurationHours: 12,
        },
      });
    }
  }

  override remoteControlDevices(
    hostName: string,
    req: RemoteControlDevicesRequest,
  ): Observable<RemoteControlDevicesResponse> {
    const response: RemoteControlDevicesResponse = {
      sessions: req.deviceConfigs.map((config) => ({
        deviceId: config.deviceId,
        sessionUrl: `http://acid/session/${config.deviceId}-${Math.floor(
          Math.random() * 1000,
        )}`,
      })),
    };
    return of(response);
  }
}
