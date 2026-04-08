import {Injectable} from '@angular/core';
import {Observable, of, throwError} from 'rxjs';
import {delay} from 'rxjs/operators';

import {
  CanRolloutResult,
  DecommissionHostResponse,
  GetHostDebugInfoResponse,
  HostHeaderInfo,
  HostReleaseConfig,
  HostRolloutAction,
  PopularFlag,
  ReleaseLabServerRequest,
  ReleaseLabServerResponse,
  RestartLabServerResponse,
  StartLabServerResponse,
  StopLabServerResponse,
} from '../../models/host_action';
import {
  CheckRemoteControlEligibilityResponse,
  DeviceEligibilityResult,
  DeviceProxyType,
  DeviceTarget,
  EligibilityStatus,
  GetHostDeviceSummariesResponse,
  HostOverviewPageData,
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

  override getHostHeaderInfo(hostName: string): Observable<HostHeaderInfo> {
    const scenario = MOCK_HOST_SCENARIOS.find((s) => s.hostName === hostName);
    const actions = scenario?.actions || {
      configuration: {enabled: true, visible: true, tooltip: ''},
      debug: {enabled: true, visible: true, tooltip: ''},
      deploy: {enabled: true, visible: true, tooltip: ''},
      start: {enabled: true, visible: true, tooltip: ''},
      restart: {enabled: true, visible: true, tooltip: ''},
      stop: {enabled: true, visible: true, tooltip: ''},
      decommission: {enabled: false, visible: true, tooltip: ''},
      updatePassThroughFlags: {enabled: true, visible: true, tooltip: ''},
      release: {enabled: true, visible: true, tooltip: ''},
    };

    return of({
      hostName,
      actions,
    });
  }

  /**
   * Retrieves the detailed overview data for a specific host by its name
   * from the mock dataset.
   * @param hostName The name of the host.
   * @return An Observable emitting the HostOverviewPageData if found,
   *          or an error Observable if not found.
   */
  override getHostOverview(hostName: string): Observable<HostOverviewPageData> {
    const scenario = MOCK_HOST_SCENARIOS.find((s) => s.hostName === hostName);
    if (scenario && scenario.overview) {
      const actions = scenario.actions || {
        configuration: {enabled: true, visible: true, tooltip: ''},
        debug: {enabled: true, visible: true, tooltip: ''},
        deploy: {enabled: true, visible: true, tooltip: ''},
        start: {enabled: true, visible: true, tooltip: ''},
        restart: {enabled: true, visible: true, tooltip: ''},
        stop: {enabled: true, visible: true, tooltip: ''},
        decommission: {enabled: false, visible: true, tooltip: ''},
        updatePassThroughFlags: {enabled: true, visible: true, tooltip: ''},
        release: {enabled: true, visible: true, tooltip: ''},
      };

      return of({
        headerInfo: {
          hostName,
          actions,
        },
        overviewContent: scenario.overview,
      });
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
  ): Observable<GetHostDeviceSummariesResponse> {
    const scenario = MOCK_HOST_SCENARIOS.find((s) => s.hostName === hostName);
    if (scenario && scenario.deviceSummaries) {
      return of({deviceSummaries: scenario.deviceSummaries});
    }
    return of({deviceSummaries: []});
  }

  override getHostDebugInfo(
    hostName: string,
  ): Observable<GetHostDebugInfoResponse> {
    return of({
      results: [
        {
          command: 'adb devices -l',
          stdout: 'List of devices attached\n8070cdc device usb:1-5',
          stderr: '',
        },
        {
          command: 'lsusb',
          stdout:
            'Bus 001 Device 002: ID 18d1:4ee2 Google Inc. Nexus/Pixel Device (MTP + debug)',
          stderr: '',
        },
      ],
      timestamp: new Date().toISOString(),
    });
  }

  override getPopularFlags(hostName: string): Observable<PopularFlag[]> {
    return of([
      {
        name: 'No Mute Android',
        description: 'Disables muting of Android devices',
        cmd: '--nomute_android',
      },
      {
        name: 'No Binary Log',
        description: 'Disables binary logging to save space',
        cmd: '--nobinarylog',
      },
      {
        name: 'Enable Linux Device',
        description: 'Enables support for Linux devices',
        cmd: '--enable_linux_device',
      },
    ]);
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

  override getReleaseConfigs(
    hostName: string,
  ): Observable<HostReleaseConfig[]> {
    return of([
      {
        name: 'MH_SATELLITE_LAB',
        version: '4.349.0',
        port: {protocol: 'grpc', portNumber: 9994},
        syncCMD: [
          'sudo systemctl stop mobileharness-lab',
          'sudo /usr/bin/mh_lab_installer --version 4.349.0',
        ],
        asyncCMD: ['sudo systemctl start mobileharness-lab'],
      },
      {
        name: 'MH_SATELLITE_LAB',
        version: '4.348.0',
        port: {protocol: 'grpc', portNumber: 9994},
        syncCMD: [
          'sudo systemctl stop mobileharness-lab',
          'sudo /usr/bin/mh_lab_installer --version 4.348.0',
        ],
        asyncCMD: ['sudo systemctl start mobileharness-lab'],
      },
    ]);
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
    targets: DeviceTarget[],
  ): Observable<CheckRemoteControlEligibilityResponse> {
    const results: DeviceEligibilityResult[] = [];

    const isTestbed = (id: string) =>
      id.includes('TESTBED') && targets.length > 1;

    targets.forEach((target) => {
      const id = target.subDeviceId || target.deviceId;
      const result: DeviceEligibilityResult = {
        deviceId: target.subDeviceId || id,
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

  override decommissionHost(
    hostName: string,
  ): Observable<DecommissionHostResponse> {
    const scenario = MOCK_HOST_SCENARIOS.find((s) => s.hostName === hostName);
    if (scenario) {
      // Simulate success if host is found in mock data.
      return of({});
    } else {
      return throwError(
        () => new Error(`Host with '${hostName}' not found in mock data.`),
      );
    }
  }

  override releaseLabServer(
    hostName: string,
    req: ReleaseLabServerRequest,
  ): Observable<ReleaseLabServerResponse> {
    return of({
      trackingUrl:
        'https://rollouts.corp.example.com/rollouts/prodchange-rollout/abc%2F123%2Frrui',
    });
  }

  override startLabServer(
    hostName: string,
  ): Observable<StartLabServerResponse> {
    return of({
      trackingUrl:
        'https://rollouts.corp.example.com/rollouts/prodchange-rollout/start%2F123',
    });
  }

  override restartLabServer(
    hostName: string,
  ): Observable<RestartLabServerResponse> {
    return of({
      trackingUrl:
        'https://rollouts.corp.example.com/rollouts/prodchange-rollout/restart%2F123',
    });
  }

  override stopLabServer(hostName: string): Observable<StopLabServerResponse> {
    return of({
      trackingUrl:
        'https://rollouts.corp.example.com/rollouts/prodchange-rollout/stop%2F123',
    });
  }

  override canRollout(
    hostName: string,
    action: HostRolloutAction,
  ): Observable<CanRolloutResult> {
    const scenario = MOCK_HOST_SCENARIOS.find((s) => s.hostName === hostName);
    if (scenario && scenario.canRollout) {
      return of(scenario.canRollout).pipe(delay(1000));
    }
    return of({
      canRollout: true,
      needUpgrade: false,
      message: '',
    }).pipe(delay(1000));
  }
}
