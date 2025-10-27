import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';

import {DeviceOverview} from '../../models/device_overview';
import {HealthinessStats, RecoveryTaskStats, TestResultStats} from '../../models/device_stats';

import {DeviceService} from './device_service';

/** An implementation of the DeviceService that uses HTTP to fetch data. */
export class HttpDeviceService extends DeviceService {
  // TODO: Read this from the app init data.
  private readonly apiUrl = 'http://localhost:8788/v6/devices';
  private readonly http;

  constructor(http: HttpClient) {
    super();
    this.http = http;
  }

  override getDeviceOverview(id: string): Observable<DeviceOverview> {
    // http://localhost:8788/v6/devices/123/overview
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
