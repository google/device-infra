import {Injectable} from '@angular/core';
import {Observable, of, throwError} from 'rxjs';

import {
  CheckRemoteControlEligibilityResponse,
  DeviceEligibilityResult,
  DeviceProxyType,
  DeviceSummary,
  EligibilityStatus,
  HostOverview,
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
    const results: DeviceEligibilityResult[] = [];

    deviceControlIds.forEach((id) => {
      const result: DeviceEligibilityResult = {
        deviceId: id,
        isEligible: true,
        supportedProxyTypes: [],
        runAsCandidates: [],
      };

      if (id.includes('ineligible-busy')) {
        result.isEligible = false;
        result.ineligibilityReason = {
          code: 'DEVICE_NOT_IDLE',
          message: `Device ${id} is busy.`,
        };
      } else if (id.includes('ineligible-no-acid')) {
        result.isEligible = false;
        result.ineligibilityReason = {
          code: 'ACID_NOT_SUPPORTED',
          message: `Device ${id} does not support ACID.`,
        };
      } else if (id.includes('ineligible-no-perm')) {
        result.isEligible = false;
        result.ineligibilityReason = {
          code: 'PERMISSION_DENIED',
          message: `Permission denied for device ${id}.`,
        };
      } else {
        result.supportedProxyTypes = [
          DeviceProxyType.ADB_AND_VIDEO,
          DeviceProxyType.SSH,
        ];
        result.runAsCandidates = ['user1', 'mdb/group1', 'mdb/group2'];

        if (id.includes('no-common-proxy-1')) {
          result.supportedProxyTypes = [DeviceProxyType.ADB_ONLY];
        } else if (id.includes('no-common-proxy-2')) {
          result.supportedProxyTypes = [DeviceProxyType.SSH];
        }
      }
      results.push(result);
    });

    // 1. Check for global permission denied (mock logic: if all devices are no-perm)
    const allDenied =
      results.length > 0 &&
      results.every(
        (r) =>
          !r.isEligible && r.ineligibilityReason?.code === 'PERMISSION_DENIED',
      );
    if (allDenied) {
      return of({
        status: EligibilityStatus.BLOCK_ALL_PERMISSION_DENIED,
        results,
      });
    }

    // 2. Check for ineligible devices
    if (results.some((r) => !r.isEligible)) {
      return of({
        status: EligibilityStatus.BLOCK_DEVICES_INELIGIBLE,
        results,
      });
    }

    // 3. Check for common proxy
    if (results.length > 0) {
      let commonProxies = results[0].supportedProxyTypes;
      for (let i = 1; i < results.length; i++) {
        commonProxies = commonProxies.filter((p) =>
          results[i].supportedProxyTypes.includes(p),
        );
      }

      if (commonProxies.length === 0) {
        return of({
          status: EligibilityStatus.BLOCK_NO_COMMON_PROXY,
          results,
        });
      }

      return of({
        status: EligibilityStatus.READY,
        results,
        sessionOptions: {
          commonProxyTypes: commonProxies,
          commonRunAsCandidates: ['user1', 'mdb/group1'],
          maxDurationHours: 12,
        },
      });
    }

    // Default empty case (should not happen if deviceControlIds is not empty)
    return of({
      status: EligibilityStatus.READY,
      results,
      sessionOptions: {
        commonProxyTypes: [],
        commonRunAsCandidates: [],
        maxDurationHours: 12,
      },
    });
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
