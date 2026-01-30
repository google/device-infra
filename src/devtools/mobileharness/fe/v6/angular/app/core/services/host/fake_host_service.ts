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

    const isTestbed = (id: string) =>
      id.includes('TESTBED') && deviceControlIds.length > 1;

    deviceControlIds.forEach((id) => {
      const result: DeviceEligibilityResult = {
        deviceId: id,
        isEligible: true,
        supportedProxyTypes: [
          DeviceProxyType.ADB_AND_VIDEO,
          DeviceProxyType.SSH,
        ],
        runAsCandidates: [],
      };

      if (id.includes('TESTBED-SINGLE-ELIGIBLE-SUB')) {
        result.subDeviceResults = [
          {
            deviceId: id + '_sub_1',
            isEligible: true,
          },
        ];
      } else if (id.includes('TESTBED-MIXED-ELIGIBILITY')) {
        result.subDeviceResults = [
          {
            deviceId: id + '_sub_1',
            isEligible: true,
          },
          {
            deviceId: id + '_sub_2',
            isEligible: false,
            ineligibilityReason: {
              code: 'DEVICE_NOT_IDLE',
              message: 'Device is busy',
            },
          },
          {
            deviceId: id + '_sub_3',
            isEligible: false,
            ineligibilityReason: {
              code: 'DEVICE_TYPE_NOT_SUPPORTED',
              message: 'Not supported type',
            },
          },
          {
            deviceId: id + '_sub_4',
            isEligible: true,
          },
        ];
      } else if (id.includes('RC-TESTBED-VALID')) {
        result.subDeviceResults = [
          {
            deviceId: 'sub-device-tb-valid-1',
            isEligible: true,
          },
          {
            deviceId: 'sub-device-tb-valid-2',
            isEligible: true,
          },
          {
            deviceId: 'sub-device-tb-valid-3',
            isEligible: false,
            ineligibilityReason: {
              code: 'DEVICE_NOT_IDLE',
              message: 'Status: BUSY',
            },
          },
          {
            deviceId: 'sub-device-tb-valid-4',
            isEligible: true,
          },
          {
            deviceId: 'sub-device-tb-valid-5',
            isEligible: false,
            ineligibilityReason: {
              code: 'DEVICE_TYPE_NOT_SUPPORTED',
              message: 'Not supported',
            },
          },
        ];
      }

      if (id.includes('ineligible-busy') || id.includes('ineligible-failed')) {
        result.isEligible = false;
        result.ineligibilityReason = {
          code: 'DEVICE_NOT_IDLE',
          message: `Status is not IDLE.`,
        };
      } else if (id.includes('ineligible-no-acid')) {
        result.isEligible = false;
        result.ineligibilityReason = {
          code: 'ACID_NOT_SUPPORTED',
          message: `No AcidRemoteDriver`,
        };
      } else if (isTestbed(id)) {
        result.isEligible = false;
        result.ineligibilityReason = {
          code: 'DEVICE_TYPE_NOT_SUPPORTED',
          message: `Not AndroidRealDevice`,
        };
      } else if (id.includes('NO-PERM')) {
        result.isEligible = false;
        result.ineligibilityReason = {
          code: 'PERMISSION_DENIED',
          message: `Permission Denied`,
        };
      } else {
        result.runAsCandidates = ['user1', 'mdb/group1', 'mdb/group2'];

        if (id.includes('VALID-GROUP')) {
          result.runAsCandidates = ['mdb/group1', 'mdb/group2'];
        } else if (id.includes('VALID-USER')) {
          result.runAsCandidates = ['user1'];
        }

        if (id.includes('PROXY-MISMATCH-1')) {
          result.supportedProxyTypes = [DeviceProxyType.ADB_ONLY];
        } else if (id.includes('PROXY-MISMATCH-2')) {
          result.supportedProxyTypes = [DeviceProxyType.SSH];
        } else if (id.includes('NO-PROXY')) {
          result.supportedProxyTypes = [];
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
    if (
      results.some(
        (r) =>
          !r.isEligible && r.ineligibilityReason?.code !== 'PERMISSION_DENIED',
      )
    ) {
      return of({
        status: EligibilityStatus.BLOCK_DEVICES_INELIGIBLE,
        results,
      });
    }

    // 3. Check for common proxy
    if (results.length > 0) {
      const eligibleResults = results.filter((r) => r.isEligible);
      let commonProxies = eligibleResults[0].supportedProxyTypes;
      for (let i = 1; i < eligibleResults.length; i++) {
        commonProxies = commonProxies.filter((p) =>
          eligibleResults[i].supportedProxyTypes.includes(p),
        );
      }

      if (commonProxies.length === 0) {
        return of({
          status: EligibilityStatus.BLOCK_NO_COMMON_PROXY,
          results,
        });
      }

      // Calculate common runAs candidates
      let commonRunAs = eligibleResults[0].runAsCandidates || [];
      for (let i = 1; i < eligibleResults.length; i++) {
        commonRunAs = commonRunAs.filter((c) =>
          eligibleResults[i].runAsCandidates?.includes(c),
        );
      }

      // Calculate max duration hours
      let maxDurationHours = 12;
      if (eligibleResults.some((r) => r.deviceId.includes('RC-VALID-USER-1'))) {
        maxDurationHours = 3;
      }

      return of({
        status: EligibilityStatus.READY,
        results,
        sessionOptions: {
          commonProxyTypes: commonProxies,
          commonRunAsCandidates: commonRunAs,
          maxDurationHours,
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

  /**
   * Starts remote control sessions for multiple devices.
   * @param hostName The name of the host.
   * @param req The request containing device IDs and configuration for the sessions.
   * @return An observable emitting the session results.
   */
  override remoteControlDevices(
    hostName: string,
    req: RemoteControlDevicesRequest,
  ): Observable<RemoteControlDevicesResponse> {
    const response: RemoteControlDevicesResponse = {
      sessions: req.deviceConfigs.map((config) => ({
        deviceId: config.deviceId,
        sessionUrl: `https://xcid.google.example.com/provider/mh/create/?device_id=${config.deviceId}`,
      })),
    };
    return of(response);
  }
}
