import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';

import {GoogleDate} from '../../../shared/utils/date_utils';
import {APP_DATA, AppData} from '../../models/app_data';
import {
  DeviceHeaderInfo,
  GetLogcatResponse,
  QuarantineDeviceRequest,
  QuarantineDeviceResponse,
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

import {DeviceService} from './device_service';

/** An implementation of the DeviceService that uses HTTP to fetch data. */
@Injectable()
export class HttpDeviceService extends DeviceService {
  private readonly appData: AppData = inject(APP_DATA);
  private readonly apiUrl = `${this.appData.labConsoleServerUrl}/v6/devices`;
  private readonly http = inject(HttpClient);

  constructor() {
    super();
  }

  /**
   * Gets the overview data of a device.
   * @param request The request containing device ID and host name.
   * @return An Observable emitting the device overview page data.
   */
  override getDeviceOverview(
    request: GetDeviceOverviewRequest,
  ): Observable<DeviceOverviewPageData> {
    const params: {[key: string]: string} = {};
    // force_refresh is always set to true for now.
    if (request.forceRefresh) {
      params['force_refresh'] = 'true';
    }
    if (request.hostName) {
      params['host_name'] = request.hostName;
    }
    const options = {params};
    return this.http.get<DeviceOverviewPageData>(
      `${this.apiUrl}/${request.id}/overview`,
      options,
    );
  }

  /**
   * Gets the header information of a device.
   * @param id The ID of the device.
   * @param hostName The name of the host running the device.
   * @return An Observable emitting the device header information.
   */
  override getDeviceHeaderInfo(
    id: string,
    hostName: string,
  ): Observable<DeviceHeaderInfo> {
    const options = {params: {'host_name': hostName}};
    return this.http.get<DeviceHeaderInfo>(
      `${this.apiUrl}/${id}/header-info`,
      options,
    );
  }

  /**
   * Gets the healthiness statistics of a device.
   * @param id The ID of the device.
   * @param startDate The start date of the time range.
   * @param endDate The end date of the time range.
   * @return An Observable emitting the healthiness statistics.
   */
  override getDeviceHealthinessStats(
    id: string,
    startDate: GoogleDate,
    endDate: GoogleDate,
  ): Observable<HealthinessStats> {
    return this.http.post<HealthinessStats>(
      `${this.apiUrl}/${id}/stats:getHealthiness`,
      {
        'start_date': {
          'year': startDate.year,
          'month': startDate.month,
          'day': startDate.day,
        },
        'end_date': {
          'year': endDate.year,
          'month': endDate.month,
          'day': endDate.day,
        },
      },
    );
  }

  /**
   * Gets the test result statistics of a device.
   * @param id The ID of the device.
   * @param startDate The start date of the time range.
   * @param endDate The end date of the time range.
   * @return An Observable emitting the test result statistics.
   */
  override getDeviceTestResultStats(
    id: string,
    startDate: GoogleDate,
    endDate: GoogleDate,
  ): Observable<TestResultStats> {
    return this.http.post<TestResultStats>(
      `${this.apiUrl}/${id}/stats:getTestResults`,
      {
        'start_date': {
          'year': startDate.year,
          'month': startDate.month,
          'day': startDate.day,
        },
        'end_date': {
          'year': endDate.year,
          'month': endDate.month,
          'day': endDate.day,
        },
      },
    );
  }

  /**
   * Gets the recovery task statistics of a device.
   * @param id The ID of the device.
   * @param startDate The start date of the time range.
   * @param endDate The end date of the time range.
   * @return An Observable emitting the recovery task statistics.
   */
  override getDeviceRecoveryTaskStats(
    id: string,
    startDate: GoogleDate,
    endDate: GoogleDate,
  ): Observable<RecoveryTaskStats> {
    return this.http.post<RecoveryTaskStats>(
      `${this.apiUrl}/${id}/stats:getRecoveryTasks`,
      {
        'start_date': {
          'year': startDate.year,
          'month': startDate.month,
          'day': startDate.day,
        },
        'end_date': {
          'year': endDate.year,
          'month': endDate.month,
          'day': endDate.day,
        },
      },
    );
  }

  /**
   * Takes a screenshot of a device.
   * @param id The ID of the device.
   * @param hostName The name of the host running the device.
   * @return An Observable emitting the screenshot response.
   */
  override takeScreenshot(
    id: string,
    hostName: string,
  ): Observable<TakeScreenshotResponse> {
    return this.http.post<TakeScreenshotResponse>(
      `${this.apiUrl}/${id}:takeScreenshot`,
      {'host_name': hostName},
    );
  }

  /**
   * Gets the logcat of a device.
   * @param id The ID of the device.
   * @param hostName The name of the host running the device.
   * @return An Observable emitting the logcat response.
   */
  override getLogcat(
    id: string,
    hostName: string,
  ): Observable<GetLogcatResponse> {
    return this.http.post<GetLogcatResponse>(`${this.apiUrl}/${id}:getLogcat`, {
      'host_name': hostName,
    });
  }

  /**
   * Quarantines a device.
   * @param id The ID of the device.
   * @param req The request containing quarantine end time and host name.
   * @return An Observable emitting the quarantine device response.
   */
  override quarantineDevice(
    id: string,
    req: QuarantineDeviceRequest,
  ): Observable<QuarantineDeviceResponse> {
    return this.http.post<QuarantineDeviceResponse>(
      `${this.apiUrl}/${id}:quarantine`,
      req,
    );
  }

  /**
   * Unquarantines a device.
   * @param id The ID of the device.
   * @param hostName The name of the host running the device.
   * @return An Observable that completes when the operation finishes.
   */
  override unquarantineDevice(id: string, hostName: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}:unquarantine`, {
      'host_name': hostName,
    });
  }

  /**
   * Prepares the device via HTTP POST.
   *
   * @param id The ID of the device to prepare.
   * @param hostName The name of the host running the device.
   * @return An Observable that completes when the prepare operation finishes.
   */
  override prepareDevice(id: string, hostName: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}:prepare`, {
      'host_name': hostName,
    });
  }

  // override remoteControl(
  //   id: string,
  //   req: RemoteControlRequest,
  // ): Observable<RemoteControlResponse> {
  //   return this.http.post<RemoteControlResponse>(
  //     `${this.apiUrl}/${id}:remoteControl`,
  //     req,
  //   );
  // }

  /**
   * Gets the testbed configuration of a device.
   * @param id The ID of the device.
   * @param hostName The name of the host running the device.
   * @return An Observable emitting the testbed configuration.
   */
  override getTestbedConfig(
    id: string,
    hostName: string,
  ): Observable<TestbedConfig> {
    const options = {params: {'host_name': hostName}};
    return this.http.get<TestbedConfig>(
      `${this.apiUrl}/${id}/testbed-config`,
      options,
    );
  }

  /**
   * Gets the test history of a device.
   * @param id The ID of the device.
   * @param hostName The name of the host running the device.
   * @param pageToken The page token for pagination.
   * @return An Observable emitting the device test history response.
   */
  override getDeviceTestHistory(
    id: string,
    hostName: string,
    pageToken = '',
  ): Observable<DeviceTestHistoryResponse> {
    const params: {[key: string]: string} = {};
    if (pageToken) {
      params['page_token'] = pageToken;
    }
    params['host_name'] = hostName;
    const options = {params};
    return this.http.get<DeviceTestHistoryResponse>(
      `${this.apiUrl}/${id}/test-history`,
      options,
    );
  }
}
