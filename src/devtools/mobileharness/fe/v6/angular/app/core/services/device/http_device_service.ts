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
  TestbedConfig,
} from '../../models/device_overview';
import {
  HealthinessStats,
  RecoveryTaskStats,
  TestResultStats,
} from '../../models/device_stats';

import {DeviceService} from './device_service';

/** An implementation of the DeviceService that uses HTTP to fetch data. */
@Injectable()
export class HttpDeviceService extends DeviceService {
  private readonly appData: AppData = inject(APP_DATA);
  private readonly apiUrl = `${this.appData.labConsoleServerUrl}/v6/devices`;
  private readonly http: HttpClient;

  constructor(http: HttpClient) {
    super();
    this.http = http;
  }

  override getDeviceOverview(id: string): Observable<DeviceOverviewPageData> {
    return this.http.get<DeviceOverviewPageData>(
      `${this.apiUrl}/${id}/overview`,
    );
  }

  override getDeviceHeaderInfo(id: string): Observable<DeviceHeaderInfo> {
    return this.http.get<DeviceHeaderInfo>(`${this.apiUrl}/${id}/header-info`);
  }

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

  override takeScreenshot(id: string): Observable<TakeScreenshotResponse> {
    return this.http.post<TakeScreenshotResponse>(
      `${this.apiUrl}/${id}:screenshot`,
      {},
    );
  }

  override getLogcat(id: string): Observable<GetLogcatResponse> {
    return this.http.post<GetLogcatResponse>(
      `${this.apiUrl}/${id}:getLogcat`,
      {},
    );
  }

  override quarantineDevice(
    id: string,
    req: QuarantineDeviceRequest,
  ): Observable<QuarantineDeviceResponse> {
    return this.http.post<QuarantineDeviceResponse>(
      `${this.apiUrl}/${id}:quarantine`,
      req,
    );
  }

  override unquarantineDevice(id: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}:unquarantine`, {});
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

  override getTestbedConfig(id: string): Observable<TestbedConfig> {
    return this.http.get<TestbedConfig>(`${this.apiUrl}/${id}/testbedConfig`);
  }
}
