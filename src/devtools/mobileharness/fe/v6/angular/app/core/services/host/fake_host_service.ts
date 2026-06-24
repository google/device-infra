import {Injectable} from '@angular/core';
import {Observable, of, throwError, timer} from 'rxjs';
import {delay, switchMap} from 'rxjs/operators';

import {
  DecommissionHostResponse,
  GetHostDebugInfoResponse,
  GetPopularFlagsResponse,
  HostHeaderInfo,
  LifecycleActionType,
  ListTroubleshootScriptsResponse,
  PreflightLabServerLifecycleResponse,
  PreflightLabServerReleaseResponse,
  ReleaseLabServerRequest,
  ReleaseLabServerResponse,
  RestartLabServerResponse,
  RunTroubleshootScriptResponse,
  StartLabServerResponse,
  StopLabServerResponse,
  UpdatePassThroughFlagsResponse,
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
import {
  createDefaultReleaseResponse,
  createHostActions,
} from '../mock_data/hosts/ui_status_utils';
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
    const actions = scenario?.actions || createHostActions('RUNNING', false);

    return of({
      hostName,
      actions,
    }).pipe(delay(1000));
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
      const actions = scenario.actions || createHostActions('RUNNING', false);

      return of({
        headerInfo: {
          hostName,
          actions,
        },
        overviewContent: scenario.overview,
      }).pipe(delay(1000));
    } else {
      return timer(1000).pipe(
        switchMap(() =>
          throwError(
            () =>
              new Error(
                `Host with '${hostName}' not found or has no overview in mock data.`,
              ),
          ),
        ),
      );
    }
  }

  override getHostDeviceSummaries(
    hostName: string,
  ): Observable<GetHostDeviceSummariesResponse> {
    const scenario = MOCK_HOST_SCENARIOS.find((s) => s.hostName === hostName);
    if (scenario && scenario.deviceSummaries) {
      return of({deviceSummaries: scenario.deviceSummaries}).pipe(delay(1000));
    }
    return of({deviceSummaries: []}).pipe(delay(1000));
  }

  override getHostDebugInfo(
    hostName: string,
  ): Observable<GetHostDebugInfoResponse> {
    if (Math.random() < 0.25) {
      return timer(1000).pipe(
        switchMap(() =>
          throwError(() => new Error('Random simulated failure')),
        ),
      );
    }
    return of({
      results: [
        {
          command: 'lsusb',
          stdout: `Bus 002 Device 001: ID 1d6b:0003 Linux Foundation 3.0 root hub
Bus 001 Device 015: ID 05c6:90db Qualcomm, Inc. Xiaomi 14 Pro
Bus 001 Device 001: ID 1d6b:0002 Linux Foundation 2.0 root hub`,
          stderr: '',
        },
        {
          command: 'grep -E mobileharness|udcluster',
          stdout: `628461 628459 408306 grep -E mobileharness|udcluster`,
          stderr: '',
        },
        {
          command: 'adb devices -l',
          stdout: `List of devices attached
8070cdc device usb:1-5 product:shennong_global model:23116PN5BG device:shennong transport_id:1`,
          stderr: '',
        },
        {
          command: 'fastboot devices',
          stdout: ``,
          stderr: '',
        },
        {
          command: 'ndm devices',
          stdout: '',
          stderr: `MobileHarnessException: Failed to get binary path of ndm [MH|UNDETERMINED|COMMAND_EXEC_FAIL|39998]`,
        },
        {
          command: 'ndm -v',
          stdout: '',
          stderr: `MobileHarnessException: Failed to get binary path of ndm [MH|UNDETERMINED|COMMAND_EXEC_FAIL|39998]`,
        },
        {
          command: 'some_command_with_stdout_and_stderr',
          stdout: `This is line 1 of stdout.
This is line 2 of stdout.`,
          stderr: `This is line 1 of stderr - a warning message.`,
        },
        {
          command: 'usb_device_detector --gid= --uid= --minloglevel=2',
          stdout: `Serial idVendor:idProduct:product usb
8070cdc 05c6:90db:Xiaomi_14_Pro usb:1-5`,
          stderr: '',
        },
        {
          command: 'ip link show',
          stdout: `1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN mode DEFAULT group default qlen 1000
link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00
2: eno1: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc fq_codel state UP mode DEFAULT group default qlen 1000
link/ether dc:4a:3e:83:25:c9 brd ff:ff:ff:ff:ff:ff
altname enp0s31f6
3: docker0: <NO-CARRIER,BROADCAST,MULTICAST,UP> mtu 1500 qdisc noqueue state DOWN mode DEFAULT group default
link/ether 62:56:d1:31:48:41 brd ff:ff:ff:ff:ff:ff
15: wwan0: <POINTOPOINT,MULTICAST,NOARP> mtu 1500 qdisc noop state DOWN mode DEFAULT group default qlen 1000
link/none`,
          stderr: '',
        },
        {
          command: 'arp -a',
          stdout: `? (100.87.74.61) at 08:81:f4:8b:f8:e0 [ether] on eno1
? (100.87.74.43) at 00:e0:20:ac:90:9c [ether] on eno1
_gateway (100.87.74.62) at 00:00:5e:00:01:aa [ether] on eno1
? (100.87.74.60) at d8:b1:22:ec:1a:70 [ether] on eno1
? (100.87.74.17) at 00:0e:c6:84:97:65 [ether] on eno1`,
          stderr: '',
        },
        {
          command: 'which adb',
          stdout: `/usr/bin/adb`,
          stderr: '',
        },
        {
          command: 'ifconfig',
          stdout: `docker0: flags=4099<UP,BROADCAST,MULTICAST> mtu 1500
inet 172.17.0.1 netmask 255.255.0.0 broadcast 172.17.255.255
ether 62:56:d1:31:48:41 txqueuelen 0 (Ethernet)
RX packets 0 bytes 0 (0.0 B)
RX errors 0 dropped 0 overruns 0 frame 0
TX packets 0 bytes 0 (0.0 B)
TX errors 0 dropped 0 overruns 0 carrier 0 collisions 0

eno1: flags=4163<UP,BROADCAST,RUNNING,MULTICAST> mtu 1500
inet 100.87.74.47 netmask 255.255.255.192 broadcast 100.87.74.63
inet6 2401:fa00:44:81c:79a:6479:2b10:4fd7 prefixlen 64 scopeid 0x0<global>
inet6 2401:fa00:44:81c:57ab:c8f3:976c:8bbb prefixlen 64 scopeid 0x0<global>
inet6 fe80::8599:e591:d289:896a prefixlen 64 scopeid 0x20<link>
inet6 2401:fa00:44:81c:299c:a1b0:38e0:81b1 prefixlen 64 scopeid 0x0<global>
inet6 2401:fa00:44:81c:c1ac:6fbb:b5ee:cc45 prefixlen 64 scopeid 0x0<global>
inet6 2401:fa00:44:81c:19e2:5387:fc74:854b prefixlen 64 scopeid 0x0<global>
inet6 2401:fa00:44:81c:e7cc:cbf:8f32:c0dc prefixlen 64 scopeid 0x0<global>
inet6 2401:fa00:44:81c:e5c6:c384:7003:2df8 prefixlen 64 scopeid 0x0<global>
inet6 2401:fa00:44:81c:8cca:ef61:3163:6886 prefixlen 64 scopeid 0x0<global>
ether dc:4a:3e:83:25:c9 txqueuelen 1000 (Ethernet)
RX packets 55466625 bytes 57965553569 (57.9 GB)
RX errors 0 dropped 0 overruns 0 frame 0
TX packets 16160631 bytes 5638170896 (5.6 GB)
TX errors 0 dropped 0 overruns 0 carrier 0 collisions 0
device interrupt 16 memory 0xd3100000-d3120000

lo: flags=73<UP,LOOPBACK,RUNNING> mtu 65536
inet 127.0.0.1 netmask 255.0.0.0
inet6 ::1 prefixlen 128 scopeid 0x10<host>
loop txqueuelen 1000 (Local Loopback)
RX packets 197019517 bytes 70929305047 (70.9 GB)
RX errors 0 dropped 0 overruns 0 frame 0
TX packets 197019517 bytes 70929305047 (70.9 GB)
TX errors 0 dropped 0 overruns 0 carrier 0 collisions 0`,
          stderr: '',
        },
        /* Mock commands added to match all available mock data in the UX Design */
        {
          command: 'idevicedevmodectl list',
          stdout: '',
          stderr: `CommandStartException: Failed to start command, command=[/idevicedevmodectl list] [MH|UNDETERMINED|COMMAND_START_ERROR|39997]`,
        },
        {
          command: 'UHUBCTL_LIST',
          stdout: '',
          stderr: `Unimplemented Command: UHUBCTL_LIST`,
        },
        {
          command: 'CFGUTIL_LIST',
          stdout: '',
          stderr: `Unimplemented Command: CFGUTIL_LIST`,
        },
        {
          command: 'DEVICECTL_LIST',
          stdout: '',
          stderr: `Unimplemented Command: DEVICECTL_LIST`,
        },
      ],
      timestamp: new Date().toISOString(),
    }).pipe(delay(1000));
  }

  override getPopularFlags(
    hostName: string,
  ): Observable<GetPopularFlagsResponse> {
    return of({
      flags: [
        {
          name: 'Standard Satellite',
          cmd: '--nomute_android --noandroid_device_daemon',
          description: 'Default configuration for Android Satellite Labs',
        },
        {
          name: 'Linux Support',
          cmd: '--enable_linux_device',
          description: 'Enables detection of Linux devices',
        },
        {
          name: 'Debug Mode',
          cmd: '--debug_mode=true --verbose',
          description: 'Enables verbose logging for debugging',
        },
        {
          name: 'Flashstation Cache',
          cmd: '--flashstation_cache_dir=/tmp/fs_cache',
          description: 'Custom cache directory for Flashstation',
        },
        {
          name: 'No Binary Log',
          cmd: '--nobinarylog',
          description: 'Disables binary logging to save space',
        },
        {
          name: 'Custom Flag',
          cmd: `--my_message=":text: field_a: 'test' field_b: 123"`,
          description: 'Custom flag for testing',
        },
        {
          name: 'Custom Flag 2',
          cmd: `--flagD="some value"`,
          description: 'Custom flag for testing 2',
        },
      ],
    }).pipe(delay(1000));
  }

  override updatePassThroughFlags(
    hostName: string,
    flags: string,
  ): Observable<UpdatePassThroughFlagsResponse> {
    const scenario = MOCK_HOST_SCENARIOS.find((s) => s.hostName === hostName);
    if (scenario && scenario.overview) {
      scenario.overview.labServer.passThroughFlags = flags;
      return of({success: true}).pipe(delay(1000));
    } else {
      return timer(1000).pipe(
        switchMap(() =>
          throwError(
            () =>
              new Error(
                `Host with '${hostName}' not found or has no overview in mock data.`,
              ),
          ),
        ),
      );
    }
  }

  override preflightLabServerRelease(
    hostName: string,
  ): Observable<PreflightLabServerReleaseResponse> {
    const scenario = MOCK_HOST_SCENARIOS.find((s) => s.hostName === hostName);
    const preflightLabServerReleaseResponse =
      scenario?.releaseResponse || createDefaultReleaseResponse();
    return of(preflightLabServerReleaseResponse).pipe(delay(1000));
  }

  override preflightLabServerLifecycle(
    hostName: string,
    action: LifecycleActionType,
    expectedActivity: string,
  ): Observable<PreflightLabServerLifecycleResponse> {
    // Default fake: always return ready with the expected activity.
    return of({
      ready: {
        actualActivity: expectedActivity,
      },
    }).pipe(delay(1000));
  }

  override decommissionMissingDevices(
    hostName: string,
    deviceIds: string[],
  ): Observable<void> {
    const scenario = MOCK_HOST_SCENARIOS.find((s) => s.hostName === hostName);
    if (scenario && scenario.deviceSummaries) {
      scenario.deviceSummaries = scenario.deviceSummaries.filter(
        (d) => !deviceIds.includes(d.id),
      );
      return of(undefined).pipe(delay(1000));
    } else {
      return timer(1000).pipe(
        switchMap(() =>
          throwError(
            () =>
              new Error(
                `Host with '${hostName}' not found or has no devices in mock data.`,
              ),
          ),
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

    for (const target of targets) {
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
    }

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
      }).pipe(delay(1000));
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
      }).pipe(delay(1000));
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
        }).pipe(delay(1000));
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
      if (eligibleResults.some((r) => r.deviceId.includes('pool-shared'))) {
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
      }).pipe(delay(1000));
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
    }).pipe(delay(1000));
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
    return of(response).pipe(delay(1000));
  }

  override decommissionHost(
    hostName: string,
  ): Observable<DecommissionHostResponse> {
    const scenario = MOCK_HOST_SCENARIOS.find((s) => s.hostName === hostName);
    if (scenario) {
      // Simulate success if host is found in mock data.
      return of({}).pipe(delay(1000));
    } else {
      return timer(1000).pipe(
        switchMap(() =>
          throwError(
            () => new Error(`Host with '${hostName}' not found in mock data.`),
          ),
        ),
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
    }).pipe(delay(1000));
  }

  override startLabServer(
    hostName: string,
  ): Observable<StartLabServerResponse> {
    return of({
      trackingUrl:
        'https://rollouts.corp.example.com/rollouts/prodchange-rollout/start%2F123',
    }).pipe(delay(1000));
  }

  override restartLabServer(
    hostName: string,
  ): Observable<RestartLabServerResponse> {
    return of({
      trackingUrl:
        'https://rollouts.corp.example.com/rollouts/prodchange-rollout/restart%2F123',
    }).pipe(delay(1000));
  }

  override stopLabServer(hostName: string): Observable<StopLabServerResponse> {
    return of({
      trackingUrl:
        'https://rollouts.corp.example.com/rollouts/prodchange-rollout/stop%2F123',
    }).pipe(delay(1000));
  }

  override runTroubleshootScript(
    hostName: string,
    script: string,
    argumentsMap: {[key: string]: string},
    universe: string,
  ): Observable<RunTroubleshootScriptResponse> {
    return of({
      exitCode: 0,
      stdout: `[Fake Host Service] Successfully executed ${script} on ${hostName}.`,
      stderr: '',
    }).pipe(delay(1500));
  }

  override listTroubleshootScripts(
    hostName: string,
    universe: string,
  ): Observable<ListTroubleshootScriptsResponse> {
    return of({
      actions: [
        {
          script: 'RESET_USB_HUB' as const,
          displayName: 'Reset USB Hub',
          description:
            'Power cycle smart USB hub ports to recover missing devices.',
          enabled: true,
          constraintTooltip:
            'Power cycle smart USB hub ports to recover missing devices.',
        },
      ],
    }).pipe(delay(1000));
  }
}
