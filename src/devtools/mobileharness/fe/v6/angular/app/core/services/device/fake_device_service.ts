import {Injectable} from '@angular/core';
import {Observable, of, throwError} from 'rxjs';
import {delay} from 'rxjs/operators';

import {GoogleDate} from '../../../shared/utils/date_utils';
import {
  DeviceHeaderInfo,
  GetLogcatResponse,
  QuarantineDeviceRequest,
  QuarantineDeviceResponse,
  QuarantineInfo,
  TakeScreenshotResponse,
} from '../../models/device_action';
import {
  DeviceOverview,
  DeviceOverviewPageData,
  GetDeviceOverviewRequest,
  TestbedConfig,
} from '../../models/device_overview';
import {
  HealthinessStats,
  RecoveryTaskStats,
  TestResultStats,
} from '../../models/device_stats';
import {MOCK_DEVICE_SCENARIOS} from '../mock_data';
import {MockDeviceScenario} from '../mock_data/models';
import {DeviceService} from './device_service';
import {
  generateHealthinessStats,
  generateRecoveryTaskStats,
  generateTestResultStats,
} from './fake_stats_utils';

/**
 * A fake implementation of the DeviceService for development and testing.
 * It uses the mock data defined in the central mock_data registry.
 */
@Injectable({
  providedIn: 'root',
})
export class FakeDeviceService extends DeviceService {
  private getDeviceOverviewCallCount = 0;

  constructor() {
    super();
  }

  /**
   * Retrieves the detailed overview data for a specific device by its ID
   * from the mock dataset.
   * @param request The request containing the unique identifier of the device.
   * @return An Observable emitting the DeviceOverview data if found,
   *          or an error Observable if not found.
   */
  override getDeviceOverview(
    request: GetDeviceOverviewRequest,
  ): Observable<DeviceOverviewPageData> {
    const scenario = MOCK_DEVICE_SCENARIOS.find((s) => s.id === request.id);
    if (scenario) {
      if (request.id === 'refresh-device-id') {
        this.getDeviceOverviewCallCount++;

        // Simulate failure on the 5th call
        if (this.getDeviceOverviewCallCount % 5 === 0) {
          return throwError(
            () =>
              new Error(
                `Simulated failure on 5th refresh call for device: ${request.id}.`,
              ),
          ).pipe(delay(1000));
        }

        const isEven = this.getDeviceOverviewCallCount % 2 === 0;

        // Deep clone overview to avoid mutating global mock data
        const overview = structuredClone(scenario.overview) as DeviceOverview;

        // 🎭 Simulate dynamic changes on refresh across all sections to verify UI reactivity.

        // 1. Health & Activity Section
        overview.healthAndActivity.state = isEven
          ? 'IN_SERVICE_IDLE'
          : 'OUT_OF_SERVICE_NEEDS_FIXING';
        overview.healthAndActivity.title = isEven
          ? 'In Service (Idle)'
          : 'Out of Service (Needs Fixing)';
        overview.healthAndActivity.subtitle = isEven
          ? 'The device is healthy and ready for new tasks.'
          : 'The device is experiencing issues and needs attention.';

        if (overview.healthAndActivity.deviceStatus) {
          overview.healthAndActivity.deviceStatus.status = isEven
            ? 'IDLE'
            : 'MISSING';
          overview.healthAndActivity.deviceStatus.isCritical = !isEven;
        }

        // 2. Basic Information Section
        overview.basicInfo.batteryLevel = isEven ? 95 : 42;
        overview.basicInfo.version = isEven ? '14 (Stable)' : '14 (Beta)';

        if (overview.basicInfo.network) {
          overview.basicInfo.network.wifiRssi = isEven ? -50 : -85;
          overview.basicInfo.network.hasInternet = isEven;
        }

        // 3. Properties Section
        overview.properties = {
          ...scenario.overview.properties,
          'Refresh Count': String(this.getDeviceOverviewCallCount),
          'Last Refreshed At': new Date().toLocaleTimeString(),
          'Simulated State': isEven ? 'Even Refresh' : 'Odd Refresh',
        };

        // 4. Capabilities Section
        if (overview.capabilities) {
          overview.capabilities.supportedDrivers = isEven
            ? [
                ...(scenario.overview.capabilities.supportedDrivers || []),
                'DynamicFakeDriver',
              ]
            : scenario.overview.capabilities.supportedDrivers;
        }

        // 5. Dimensions Section
        if (overview.dimensions && overview.dimensions.supported) {
          // Inject dynamic dimension into the first available source group
          const sources = Object.keys(overview.dimensions.supported);
          if (sources.length > 0) {
            const source = sources[0];
            overview.dimensions.supported[source] = {
              ...overview.dimensions.supported[source],
              dimensions: [
                ...(scenario.overview.dimensions?.supported?.[source]
                  ?.dimensions || []),
                {
                  name: 'Dynamic Simulation Source',
                  value: isEven ? 'Toggle A' : 'Toggle B',
                },
              ],
            };
          }
        }

        // 6. Sub-Devices Section (if applicable)
        if (overview.subDevices && overview.subDevices.length > 0) {
          overview.subDevices[0].batteryLevel = isEven ? 80 : 20;
          if (overview.subDevices[0].network) {
            overview.subDevices[0].network.wifiRssi = isEven ? -55 : -90;
          }
        }

        // 7. Permissions Section
        if (overview.permissions) {
          overview.permissions.owners = isEven
            ? [
                ...(scenario.overview.permissions?.owners || []),
                'DynamicFakeOwner',
              ]
            : scenario.overview.permissions?.owners;
          overview.permissions.executors = isEven
            ? [
                ...(scenario.overview.permissions?.executors || []),
                'DynamicFakeExecutor',
              ]
            : scenario.overview.permissions?.executors;
        }

        // Create a cloned scenario with the modified overview to pass to header info function
        const simulatedScenario = {
          ...scenario,
          overview,
        };

        return of({
          overview,
          headerInfo: this.getMockDeviceHeaderInfo(simulatedScenario),
        }).pipe(delay(1000));
      }

      // Original logic for other devices
      return of({
        overview: scenario.overview,
        headerInfo: this.getMockDeviceHeaderInfo(scenario),
      }).pipe(delay(1000));
    } else {
      return throwError(
        () =>
          new Error(`Device with ID '${request.id}' not found in mock data.`),
      ).pipe(delay(1000));
    }
  }

  override getDeviceHeaderInfo(id: string): Observable<DeviceHeaderInfo> {
    const scenario = MOCK_DEVICE_SCENARIOS.find((s) => s.id === id);
    if (scenario) {
      return of(this.getMockDeviceHeaderInfo(scenario)).pipe(delay(1000));
    } else {
      return throwError(
        () => new Error(`Device with ID '${id}' not found in mock data.`),
      ).pipe(delay(1000));
    }
  }

  /**
   * Retrieves healthiness statistics for a device within a given time range.
   * @param id The device ID.
   * @param startDate The start date of the range.
   * @param endDate The end date of the range.
   */
  override getDeviceHealthinessStats(
    id: string,
    startDate: GoogleDate,
    endDate: GoogleDate,
  ): Observable<HealthinessStats> {
    const scenario = MOCK_DEVICE_SCENARIOS.find((s) => s.id === id);
    if (scenario) {
      const stats =
        scenario.healthinessStats ||
        generateHealthinessStats(startDate, endDate);
      return of(stats).pipe(delay(1000));
    } else {
      return throwError(
        () => new Error(`Device with ID '${id}' not found in mock data.`),
      ).pipe(delay(1000));
    }
  }

  /**
   * Retrieves test result statistics for a device within a given time range.
   * @param id The device ID.
   * @param startDate The start date of the range.
   * @param endDate The end date of the range.
   */
  override getDeviceTestResultStats(
    id: string,
    startDate: GoogleDate,
    endDate: GoogleDate,
  ): Observable<TestResultStats> {
    const scenario = MOCK_DEVICE_SCENARIOS.find((s) => s.id === id);
    if (scenario) {
      const stats =
        scenario.testResultStats || generateTestResultStats(startDate, endDate);
      return of(stats).pipe(delay(1000));
    } else {
      return throwError(
        () => new Error(`Device with ID '${id}' not found in mock data.`),
      ).pipe(delay(1000));
    }
  }

  /**
   * Retrieves recovery task statistics for a device within a given time range.
   * @param id The device ID.
   * @param startDate The start date of the range.
   * @param endDate The end date of the range.
   */
  override getDeviceRecoveryTaskStats(
    id: string,
    startDate: GoogleDate,
    endDate: GoogleDate,
  ): Observable<RecoveryTaskStats> {
    const scenario = MOCK_DEVICE_SCENARIOS.find((s) => s.id === id);
    if (scenario) {
      const stats =
        scenario.recoveryTaskStats ||
        generateRecoveryTaskStats(startDate, endDate);
      return of(stats).pipe(delay(1000));
    } else {
      return throwError(
        () => new Error(`Device with ID '${id}' not found in mock data.`),
      ).pipe(delay(1000));
    }
  }

  override takeScreenshot(id: string): Observable<TakeScreenshotResponse> {
    console.log(`FakeService: Taking screenshot for ${id}`);
    return of({
      screenshotUrl:
        'http://0.0.0.0:8000/device_detail/action_bar/resource/screenshot-demo.png',
      capturedAt: new Date().toISOString(),
    }).pipe(delay(1000));
  }

  override getLogcat(id: string): Observable<GetLogcatResponse> {
    console.log(`FakeService: Getting logcat for ${id}`);
    return of({
      logUrl:
        'http://0.0.0.0:8000/device_detail/action_bar/resource/logcat-demo.log',
      capturedAt: new Date().toISOString(),
    }).pipe(delay(1000));
  }

  override quarantineDevice(
    id: string,
    req: QuarantineDeviceRequest,
  ): Observable<QuarantineDeviceResponse> {
    if (!req.endTime) {
      return throwError(
        () =>
          new Error('Invalid quarantine request, missing parameter "endTime".'),
      ).pipe(delay(1000));
    }
    console.log(`FakeService: Quarantining ${id} until ${req.endTime}`);
    // The fake service can just echo back the requested expiry time.
    return of({quarantineExpiry: req.endTime}).pipe(delay(1000));
  }

  override unquarantineDevice(id: string): Observable<void> {
    console.log(`FakeService: Unquarantining ${id}`);
    return of(undefined).pipe(delay(1000));
  }

  // override remoteControl(
  //   id: string,
  //   req: RemoteControlRequest,
  // ): Observable<RemoteControlResponse> {
  //   console.log(`FakeService: Remote controlling ${id} with req:`, req);
  //   return of({
  //     sessionUrl: `https://xcid.google.example.com/provider/mh/create/?deviceId=${id}`,
  //   }).pipe(delay(1000));
  // }

  override getTestbedConfig(id: string): Observable<TestbedConfig> {
    console.log(`FakeService: Getting testbed config for ${id}`);
    const scenario = MOCK_DEVICE_SCENARIOS.find((s) => s.id === id);
    if (scenario) {
      return of({
        yamlContent: scenario.testbedConfig?.yamlContent || '',
        codeSearchLink: scenario.testbedConfig?.codeSearchLink || '',
      }).pipe(delay(1000));
    } else {
      return throwError(
        () => new Error(`Device with ID '${id}' not found in mock data.`),
      ).pipe(delay(1000));
    }
  }

  private getMockDeviceHeaderInfo(
    scenario: MockDeviceScenario,
  ): DeviceHeaderInfo {
    const overview = scenario.overview;
    const isAndroid = overview.isAndroid ?? overview.basicInfo.os === 'Android';
    const isMissing =
      overview.healthAndActivity.deviceStatus.status === 'MISSING';
    const isIdle = overview.healthAndActivity.deviceStatus.status === 'IDLE';
    const isFlashable = overview.healthAndActivity.deviceTypes.some(
      (t) => t.type === 'AndroidFlashableDevice',
    );
    const screenshotable = (
      overview.capabilities.supportedDecorators || []
    ).includes('AndroidScreenshotDecorator');

    const remoteControlEnabled = isAndroid && isIdle;
    const screenshotEnabled = isAndroid && !isMissing && screenshotable;
    const logcatEnabled = isAndroid && !isMissing;
    const flashEnabled = isAndroid && isFlashable;
    const isQuarantined = scenario.isQuarantined;
    const quarantineExpiry = scenario.quarantineExpiry || '';

    const quarantine: QuarantineInfo = {
      isQuarantined,
      expiry: quarantineExpiry,
    };

    const screenshotVisible = scenario.actionVisibility?.screenshot ?? true;
    const logcatVisible = scenario.actionVisibility?.logcat ?? true;
    const flashVisible = scenario.actionVisibility?.flash ?? true;
    const remoteControlVisible =
      scenario.actionVisibility?.remoteControl ?? true;
    const quarantineVisible = scenario.actionVisibility?.quarantine ?? true;

    return {
      id: overview.id,
      host: overview.host,
      quarantine,
      actions: {
        screenshot: {
          enabled: screenshotEnabled,
          visible: screenshotVisible,
          tooltip: screenshotEnabled
            ? 'Take screenshot'
            : !isAndroid
              ? 'Only for Android devices'
              : isMissing
                ? 'Device is missing'
                : 'Screenshot not supported',
          isReady: !scenario.allActionsNotReady,
        },
        logcat: {
          enabled: logcatEnabled,
          visible: logcatVisible,
          tooltip: logcatEnabled
            ? 'Get logcat'
            : !isAndroid
              ? 'Only for Android devices'
              : 'Device is missing',
          isReady: !scenario.allActionsNotReady,
        },
        flash: {
          state: {
            enabled: flashEnabled,
            visible: flashVisible,
            tooltip: flashEnabled
              ? 'Flash device'
              : !isAndroid
                ? 'Only for Android devices'
                : 'Device not flashable',
            isReady: !scenario.allActionsNotReady,
          },
          params: {
            deviceType: 'AndroidRealDevice',
            requiredDimensions: '',
          },
        },
        remoteControl: {
          enabled: remoteControlEnabled,
          visible: remoteControlVisible,
          tooltip: remoteControlEnabled
            ? 'Remote control'
            : !isAndroid
              ? 'Only for Android devices'
              : 'Device must be IDLE for remote control',
          isReady: !scenario.allActionsNotReady,
        },
        quarantine: {
          enabled: true,
          visible: quarantineVisible,
          tooltip: isQuarantined ? 'Unquarantine device' : 'Quarantine device',
          isReady: !scenario.allActionsNotReady,
        },
        configuration: {
          enabled: true,
          visible: true,
          tooltip: 'Configure device',
          isReady: !scenario.allActionsNotReady,
        },
        decommission: {
          enabled: true,
          visible: true,
          tooltip: 'Decommission device',
          isReady: !scenario.allActionsNotReady,
        },
      },
    };
  }

  // Future methods like listDevices, updateDeviceConfig, etc., would be added here.
}

// Optional: Provider for easy swapping in AppModule
// export const FAKE_DEVICE_SERVICE_PROVIDER = {
//   provide: DEVICE_SERVICE,
//   useClass: FakeDeviceService
// };
