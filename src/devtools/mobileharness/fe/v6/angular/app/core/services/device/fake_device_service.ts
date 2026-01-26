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
  DeviceOverviewPageData,
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
  constructor() {
    super();
  }

  /**
   * Retrieves the detailed overview data for a specific device by its ID
   * from the mock dataset.
   * @param id The unique identifier of the device.
   * @returns An Observable emitting the DeviceOverview data if found,
   *          or an error Observable if not found.
   */
  override getDeviceOverview(id: string): Observable<DeviceOverviewPageData> {
    const scenario = MOCK_DEVICE_SCENARIOS.find((s) => s.id === id);
    if (scenario) {
      return of({
        overview: scenario.overview,
        headerInfo: this.getMockDeviceHeaderInfo(scenario),
      }).pipe(delay(1000));
    } else {
      return throwError(
        () => new Error(`Device with ID '${id}' not found in mock data.`),
      );
    }
  }

  override getDeviceHeaderInfo(id: string): Observable<DeviceHeaderInfo> {
    const scenario = MOCK_DEVICE_SCENARIOS.find((s) => s.id === id);
    if (scenario) {
      return of(this.getMockDeviceHeaderInfo(scenario)).pipe(delay(1000));
    } else {
      return throwError(
        () => new Error(`Device with ID '${id}' not found in mock data.`),
      );
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
      return of(generateHealthinessStats(startDate, endDate)).pipe(delay(1000));
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
      return of(generateRecoveryTaskStats(startDate, endDate)).pipe(
        delay(1000),
      );
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
      );
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
      });
    } else {
      return throwError(
        () => new Error(`Device with ID '${id}' not found in mock data.`),
      );
    }
  }

  private getMockDeviceHeaderInfo(
    scenario: MockDeviceScenario,
  ): DeviceHeaderInfo {
    const overview = scenario.overview;
    const isAndroid = overview.basicInfo.os === 'Android';
    const isMissing =
      overview.healthAndActivity.deviceStatus.status === 'MISSING';
    const isIdle = overview.healthAndActivity.deviceStatus.status === 'IDLE';
    const isFlashable = overview.healthAndActivity.deviceTypes.some(
      (t) => t.type === 'AndroidFlashableDevice',
    );
    const screenshotable = overview.capabilities.supportedDecorators.includes(
      'AndroidScreenshotDecorator',
    );

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
        },
        logcat: {
          enabled: logcatEnabled,
          visible: logcatVisible,
          tooltip: logcatEnabled
            ? 'Get logcat'
            : !isAndroid
              ? 'Only for Android devices'
              : 'Device is missing',
        },
        flash: {
          enabled: flashEnabled,
          visible: flashVisible,
          tooltip: flashEnabled
            ? 'Flash device'
            : !isAndroid
              ? 'Only for Android devices'
              : 'Device not flashable',
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
        },
        quarantine: {
          enabled: true,
          visible: quarantineVisible,
          tooltip: isQuarantined ? 'Unquarantine device' : 'Quarantine device',
        },
        configuration: {
          enabled: true,
          visible: true,
          tooltip: 'Configure device',
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
