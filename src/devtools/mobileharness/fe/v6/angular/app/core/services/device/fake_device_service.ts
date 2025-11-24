import {Injectable} from '@angular/core';
import {Observable, of, throwError} from 'rxjs';
import {
  DeviceHeaderInfo,
  GetLogcatResponse,
  QuarantineDeviceRequest,
  QuarantineDeviceResponse,
  RemoteControlRequest,
  RemoteControlResponse,
  TakeScreenshotResponse,
} from '../../models/device_action';
import {
  DeviceOverview,
  DeviceOverviewPageData,
} from '../../models/device_overview';
import {
  HealthinessStats,
  RecoveryTaskStats,
  TestResultStats,
} from '../../models/device_stats';
import {MOCK_DEVICE_SCENARIOS} from '../mock_data';
import {DeviceService} from './device_service';

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
        headerInfo: this.getMockDeviceHeaderInfo(scenario.overview),
      });
    } else {
      return throwError(
        () => new Error(`Device with ID '${id}' not found in mock data.`),
      );
    }
  }

  override getDeviceHeaderInfo(id: string): Observable<DeviceHeaderInfo> {
    const scenario = MOCK_DEVICE_SCENARIOS.find((s) => s.id === id);
    if (scenario) {
      return of(this.getMockDeviceHeaderInfo(scenario.overview));
    } else {
      return throwError(
        () => new Error(`Device with ID '${id}' not found in mock data.`),
      );
    }
  }

  /**
   * Retrieves healthiness statistics for a device within a given time range.
   * @param id The device ID.
   * @param startTime ISO 8601 string representing the start of the range.
   * @param endTime ISO 8601 string representing the end of the range.
   */
  override getDeviceHealthinessStats(
    id: string,
    startTime: string,
    endTime: string,
  ): Observable<HealthinessStats> {
    console.log(
      `FakeService: Fetching Healthiness for ${id} from ${startTime} to ${endTime}`,
    );
    // In a real scenario, we would check if the device exists.
    // Here, we just return mock data based on the date range.
    return of();
  }

  /**
   * Retrieves test result statistics for a device within a given time range.
   * @param id The device ID.
   * @param startTime ISO 8601 string representing the start of the range.
   * @param endTime ISO 8601 string representing the end of the range.
   */
  override getDeviceTestResultStats(
    id: string,
    startTime: string,
    endTime: string,
  ): Observable<TestResultStats> {
    console.log(
      `FakeService: Fetching Test Results for ${id} from ${startTime} to ${endTime}`,
    );
    return of();
  }

  /**
   * Retrieves recovery task statistics for a device within a given time range.
   * @param id The device ID.
   * @param startTime ISO 8601 string representing the start of the range.
   * @param endTime ISO 8601 string representing the end of the range.
   */
  override getDeviceRecoveryTaskStats(
    id: string,
    startTime: string,
    endTime: string,
  ): Observable<RecoveryTaskStats> {
    console.log(
      `FakeService: Fetching Recovery Tasks for ${id} from ${startTime} to ${endTime}`,
    );
    return of();
  }

  override takeScreenshot(id: string): Observable<TakeScreenshotResponse> {
    console.log(`FakeService: Taking screenshot for ${id}`);
    return of({
      screenshotUrl: 'https://screenshot.googleexampleplex.com/faked',
      capturedAt: new Date().toISOString(),
    });
  }

  override getLogcat(id: string): Observable<GetLogcatResponse> {
    console.log(`FakeService: Getting logcat for ${id}`);
    return of({
      logUrl: 'https://example.com/logcat.txt',
      capturedAt: new Date().toISOString(),
    });
  }

  override quarantineDevice(
    id: string,
    req: QuarantineDeviceRequest,
  ): Observable<QuarantineDeviceResponse> {
    console.log(`FakeService: Quarantining ${id} for ${req.durationHours}h`);
    const expiry = new Date();
    expiry.setHours(expiry.getHours() + req.durationHours);
    return of({quarantineExpiry: expiry.toISOString()});
  }

  override unquarantineDevice(id: string): Observable<void> {
    console.log(`FakeService: Unquarantining ${id}`);
    return of(undefined);
  }

  override remoteControl(
    id: string,
    req: RemoteControlRequest,
  ): Observable<RemoteControlResponse> {
    console.log(`FakeService: Remote controlling ${id} with req:`, req);
    return of({
      sessionUrl: `https://xcid.google.example.com/provider/mh/create/?deviceId=${id}`,
    });
  }

  private getMockDeviceHeaderInfo(overview: DeviceOverview): DeviceHeaderInfo {
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

    return {
      id: overview.id,
      host: overview.host,
      quarantine: {
        isQuarantined: overview.healthAndActivity.isQuarantined,
        expiry: overview.healthAndActivity.quarantineExpiry,
      },
      actions: {
        screenshot: {
          enabled: screenshotEnabled,
          visible: true,
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
          visible: true,
          tooltip: logcatEnabled
            ? 'Get logcat'
            : !isAndroid
              ? 'Only for Android devices'
              : 'Device is missing',
        },
        flash: {
          enabled: flashEnabled,
          visible: true,
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
          visible: true,
          tooltip: remoteControlEnabled
            ? 'Remote control'
            : !isAndroid
              ? 'Only for Android devices'
              : 'Device must be IDLE for remote control',
          runAsOptions: [{value: 'test', label: 'test', isDefault: true}],
          defaultRunAs: 'test',
        },
        quarantine: {
          enabled: true,
          visible: true,
          tooltip: 'Quarantine device',
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
