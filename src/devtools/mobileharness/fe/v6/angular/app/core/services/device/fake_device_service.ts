import {HttpErrorResponse} from '@angular/common/http';
import {Injectable} from '@angular/core';
import {Observable, of, throwError, timer} from 'rxjs';
import {delay, switchMap} from 'rxjs/operators';

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
  GetDeviceOverviewRequest,
  TestbedConfig,
} from '../../models/device_overview';
import {
  HealthinessStats,
  RecoveryTaskStats,
  TestResultStats,
} from '../../models/device_stats';
import {DeviceTestHistoryResponse} from '../../models/device_test_history';
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
 * This service simulates network latency and occasional failures for testing
 * purposes. This is NOT prod code, so NO need to add test file for this.
 */
@Injectable({
  providedIn: 'root',
})
export class FakeDeviceService extends DeviceService {
  private getDeviceOverviewCallCount = 0;
  private failPage3Load = true;

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
    const wrapper = MOCK_DEVICE_SCENARIOS.find((s) => s.id === request.id);
    if (!wrapper) {
      return throwError(
        () =>
          new Error(`Device with ID '${request.id}' not found in mock data.`),
      ).pipe(delay(1000));
    }

    try {
      this.getDeviceOverviewCallCount++;
      const scenario = wrapper.factory(this.getDeviceOverviewCallCount);
      return of({
        overview: scenario.overview,
        headerInfo: this.getMockDeviceHeaderInfo(scenario),
      }).pipe(delay(1000));
    } catch (e: unknown) {
      return throwError(() => e).pipe(delay(1000));
    }
  }

  /**
   * Retrieves the header information for a specific device.
   * @param id The ID of the device.
   * @param hostName The name of the host.
   * @return An Observable emitting the DeviceHeaderInfo.
   */
  override getDeviceHeaderInfo(
    id: string,
    hostName: string,
  ): Observable<DeviceHeaderInfo> {
    const wrapper = MOCK_DEVICE_SCENARIOS.find((s) => s.id === id);
    const scenario = wrapper?.factory(this.getDeviceOverviewCallCount);
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
   * @return An Observable emitting the HealthinessStats.
   */
  override getDeviceHealthinessStats(
    id: string,
    startDate: GoogleDate,
    endDate: GoogleDate,
  ): Observable<HealthinessStats> {
    const wrapper = MOCK_DEVICE_SCENARIOS.find((s) => s.id === id);
    const scenario = wrapper?.factory(this.getDeviceOverviewCallCount);
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
   * @return An Observable emitting the TestResultStats.
   */
  override getDeviceTestResultStats(
    id: string,
    startDate: GoogleDate,
    endDate: GoogleDate,
  ): Observable<TestResultStats> {
    const wrapper = MOCK_DEVICE_SCENARIOS.find((s) => s.id === id);
    const scenario = wrapper?.factory(this.getDeviceOverviewCallCount);
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
   * @return An Observable emitting the RecoveryTaskStats.
   */
  override getDeviceRecoveryTaskStats(
    id: string,
    startDate: GoogleDate,
    endDate: GoogleDate,
  ): Observable<RecoveryTaskStats> {
    const wrapper = MOCK_DEVICE_SCENARIOS.find((s) => s.id === id);
    const scenario = wrapper?.factory(this.getDeviceOverviewCallCount);
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

  /**
   * Simulates taking a screenshot of a device.
   * @param id The ID of the device.
   * @param hostName The name of the host.
   * @return An Observable emitting the TakeScreenshotResponse.
   */
  override takeScreenshot(
    id: string,
    hostName: string,
  ): Observable<TakeScreenshotResponse> {
    console.log(`FakeService: Taking screenshot for ${id}`);
    if (id.includes('permission-denied')) {
      return throwError(
        () =>
          new HttpErrorResponse({
            status: 403,
            statusText: 'Forbidden',
            error: {
              code: 7,
              message: `User does not have permission to take screenshot of device ${id}.`,
            },
          }),
      ).pipe(delay(1000));
    }
    if (id.includes('logical-error')) {
      return throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            statusText: 'Not Found',
            error: {
              code: 5,
              message: 'Device was disconnected during screenshot',
            },
          }),
      ).pipe(delay(1000));
    }
    if (id.includes('rpc-error')) {
      return throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            statusText: 'Internal Server Error',
            error: {
              code: 13,
              message: 'RPC Failure: Connection reset by peer',
            },
          }),
      ).pipe(delay(1000));
    }

    return of({
      screenshotUrl:
        'http://0.0.0.0:8000/device_detail/action_bar/resource/screenshot-demo.png',
      capturedAt: new Date().toISOString(),
    }).pipe(delay(1000));
  }

  /**
   * Simulates getting the logcat of a device.
   * @param id The ID of the device.
   * @param hostName The name of the host.
   * @return An Observable emitting the GetLogcatResponse.
   */
  override getLogcat(
    id: string,
    hostName: string,
  ): Observable<GetLogcatResponse> {
    console.log(`FakeService: Getting logcat for ${id}`);
    if (id.includes('permission-denied')) {
      return throwError(
        () =>
          new HttpErrorResponse({
            status: 403,
            statusText: 'Forbidden',
            error: {
              code: 7,
              message: `User does not have permission to get logcat of device ${id}.`,
            },
          }),
      ).pipe(delay(1000));
    }
    if (id.includes('logical-error')) {
      return throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            statusText: 'Not Found',
            error: {
              code: 5,
              message: 'Device was disconnected while retrieving logcat',
            },
          }),
      ).pipe(delay(1000));
    }
    if (id.includes('rpc-error')) {
      return throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            statusText: 'Internal Server Error',
            error: {
              code: 13,
              message: 'RPC Failure: Connection timed out',
            },
          }),
      ).pipe(delay(1000));
    }

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

  /**
   * Simulates unquarantining a device.
   * @param id The ID of the device.
   * @param hostName The name of the host.
   * @return An Observable that completes when the operation finishes.
   */
  override unquarantineDevice(id: string, hostName: string): Observable<void> {
    console.log(`FakeService: Unquarantining ${id}`);
    return of(undefined).pipe(delay(1000));
  }

  /**
   * Prepares the device (Fake implementation).
   *
   * @param id The ID of the device to prepare.
   * @param hostName The name of the host.
   * @return An Observable that completes after a delay.
   */
  override prepareDevice(id: string, hostName: string): Observable<void> {
    console.log(`FakeService: Preparing device ${id}`);
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

  /**
   * Retrieves the testbed configuration for a device.
   * @param id The ID of the device.
   * @param hostName The name of the host.
   * @return An Observable emitting the TestbedConfig.
   */
  override getTestbedConfig(
    id: string,
    hostName: string,
  ): Observable<TestbedConfig> {
    console.log(`FakeService: Getting testbed config for ${id}`);
    const wrapper = MOCK_DEVICE_SCENARIOS.find((s) => s.id === id);
    const scenario = wrapper?.factory(this.getDeviceOverviewCallCount);
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
    const decommissionVisible = isMissing;
    const isFusion = Object.values(overview.dimensions?.supported ?? {}).some(
      (group) =>
        group.dimensions?.some(
          (dim) =>
            dim.name === 'dm_type' && dim.value?.toLowerCase() === 'fusion',
        ),
    );
    const prepareVisible =
      scenario.actionVisibility?.prepare ?? (!isMissing && isFusion);

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
          visible: decommissionVisible,
          tooltip: 'Decommission device',
          isReady: !scenario.allActionsNotReady,
        },
        prepare: {
          enabled: true,
          visible: prepareVisible,
          tooltip: 'Prepare the device (reset to a known good state)',
          isReady: !scenario.allActionsNotReady,
        },
      },
    };
  }

  /**
   * Retrieves fake device test history for testing.
   *
   * @param id The device ID.
   * @param hostName The name of the host.
   * @param pageToken The pagination token (optional).
   * @return An Observable emitting the fake test history response.
   *
   * Explanation: This function returns mock data for the device test history table,
   * simulating pagination and delay. It has been updated to include jobId in the
   * link target to support direct linking to test details.
   */
  override getDeviceTestHistory(
    id: string,
    hostName: string,
    pageToken = '',
  ): Observable<DeviceTestHistoryResponse> {
    const columns = [
      {key: 'test_id', displayName: 'Test ID'},
      {key: 'name', displayName: 'Test name'},
      {key: 'user', displayName: 'User'},
      {key: 'actual_user', displayName: 'Actual user'},
      {key: 'status', displayName: 'Status'},
      {key: 'result', displayName: 'Result'},
      {key: 'start_time', displayName: 'Start time'},
      {key: 'duration', displayName: 'Duration'},
      {key: 'host_name', displayName: 'Lab (host)'},
      {key: 'devices', displayName: 'Devices'},
    ];

    const pageNum =
      pageToken === '' ? 1 : Number(pageToken.replace('page', '')) || 1;

    // Simulate failure on Page 3 load
    if (pageNum === 3 && this.failPage3Load) {
      this.failPage3Load = false; // Next time it will succeed
      return timer(1000).pipe(
        switchMap(() =>
          throwError(
            () => new Error('Simulated network failure while loading Page 3.'),
          ),
        ),
      );
    }

    // Reset failure for next test run?
    if (pageNum === 1) {
      this.failPage3Load = true;
    }

    const now = Date.now();
    const rows = [];
    for (let i = 0; i < 5; i++) {
      const testId = `fake-test-${pageNum}-${String(i).padStart(4, '0')}`;
      const jobId = `fake-job-${pageNum}-${String(i).padStart(4, '0')}`;
      rows.push({
        id: testId,
        cells: [
          {
            link: {
              text: testId,
              target: {test: {testId, jobId}},
            },
          },
          {text: {value: `Page ${pageNum} Test ${i}`}},
          {text: {value: i % 2 === 0 ? 'dafeng' : 'mhar-robot'}},
          {text: {value: i % 2 === 0 ? 'dafeng' : 'system'}},
          {
            status: {
              text: i % 2 === 0 ? 'Done' : 'Running',
              indicator:
                i % 2 === 0
                  ? ('INDICATOR_NEUTRAL' as const)
                  : ('INDICATOR_ACTIVE' as const),
            },
          },
          {
            status: {
              text: i % 2 === 0 ? 'Pass' : 'Unknown',
              indicator:
                i % 2 === 0
                  ? ('INDICATOR_OK' as const)
                  : ('INDICATOR_NEUTRAL' as const),
            },
          },
          {text: {value: String(now - (i + 1) * 3600_000)}},
          {text: {value: i % 2 === 0 ? '125000' : ''}},
          {text: {value: 'lab-host-01'}},
          {multiLink: {entries: [{text: id, target: {device: {id}}}]}},
        ],
      });
    }

    let nextPageToken = '';
    if (pageNum < 5) {
      nextPageToken = `page${pageNum + 1}`;
    }

    return of({columns, rows, nextPageToken}).pipe(delay(1000));
  }

  // Future methods like listDevices, updateDeviceConfig, etc., would be added here.
}

// Optional: Provider for easy swapping in AppModule
// export const FAKE_DEVICE_SERVICE_PROVIDER = {
//   provide: DEVICE_SERVICE,
//   useClass: FakeDeviceService
// };
