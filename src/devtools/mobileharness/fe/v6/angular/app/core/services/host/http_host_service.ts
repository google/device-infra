import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';

import {APP_DATA, AppData} from '../../models/app_data';
import {DeviceSummary, HostOverview} from '../../models/host_overview';

import {HostService} from './host_service';

/** An implementation of the HostService that uses HTTP to fetch data. */
@Injectable()
export class HttpHostService extends HostService {
  private readonly appData: AppData = inject(APP_DATA);
  private readonly apiUrl = `${this.appData.labConsoleServerUrl}/v6/hosts`;
  private readonly http = inject(HttpClient);

  constructor() {
    super();
  }

  override getHostOverview(hostName: string): Observable<HostOverview> {
    return this.http.get<HostOverview>(`${this.apiUrl}/${hostName}/overview`);
  }

  override getHostDeviceSummaries(
    hostName: string,
  ): Observable<DeviceSummary[]> {
    return this.http.get<DeviceSummary[]>(`${this.apiUrl}/${hostName}/devices`);
  }

  override updatePassThroughFlags(
    hostName: string,
    flags: string,
  ): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${hostName}/passThroughFlags`, {
      flags,
    });
  }

  override decommissionMissingDevices(
    hostName: string,
    deviceControlIds: string[],
  ): Observable<void> {
    return this.http.post<void>(
      `${this.apiUrl}/${hostName}/decommissionMissingDevices`,
      {
        deviceControlIds,
      },
    );
  }
}
