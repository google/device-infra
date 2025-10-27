import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';

import {APP_DATA, AppData} from '../../models/app_data';
import {DeviceOverview} from '../../models/device_overview';
import {HealthinessStats, RecoveryTaskStats, TestResultStats} from '../../models/device_stats';

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

  override getDeviceOverview(id: string): Observable<DeviceOverview> {
    return this.http.get<DeviceOverview>(`${this.apiUrl}/${id}/overview`);
  }

  override getDeviceHealthinessStats(
      id: string,
      startTime: string,
      endTime: string,
      ): Observable<HealthinessStats> {
    return this.http.get<HealthinessStats>(
        `${this.apiUrl}/${id}/stats/healthiness`, {
          params: {startTime, endTime},
        });
  }

  override getDeviceTestResultStats(
      id: string,
      startTime: string,
      endTime: string,
      ): Observable<TestResultStats> {
    return this.http.get<TestResultStats>(
        `${this.apiUrl}/${id}/stats/testresults`, {
          params: {startTime, endTime},
        });
  }

  override getDeviceRecoveryTaskStats(
      id: string,
      startTime: string,
      endTime: string,
      ): Observable<RecoveryTaskStats> {
    return this.http.get<RecoveryTaskStats>(
        `${this.apiUrl}/${id}/stats/recoverytasks`,
        {
          params: {startTime, endTime},
        },
    );
  }
}
