import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';

import {toDeviceProxyType} from '../../../shared/utils/enum_utils';
import {APP_DATA, AppData} from '../../models/app_data';
import {
  DecommissionHostResponse,
  GetHostDebugInfoResponse,
  HostHeaderInfo,
  HostReleaseConfig,
  PopularFlag,
  ReleaseLabServerRequest,
  ReleaseLabServerResponse,
  RestartLabServerResponse,
  StartLabServerResponse,
  StopLabServerResponse,
} from '../../models/host_action';
import {
  CheckRemoteControlEligibilityResponse,
  DeviceTarget,
  GetHostDeviceSummariesResponse,
  HostOverviewPageData,
  RemoteControlDevicesRequest,
  RemoteControlDevicesResponse,
} from '../../models/host_overview';

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

  override getHostHeaderInfo(hostName: string): Observable<HostHeaderInfo> {
    return this.http.get<HostHeaderInfo>(
      `${this.apiUrl}/${hostName}/header-info`,
    );
  }

  override getHostOverview(hostName: string): Observable<HostOverviewPageData> {
    return this.http.get<HostOverviewPageData>(
      `${this.apiUrl}/${hostName}/overview`,
    );
  }

  override getHostDeviceSummaries(
    hostName: string,
  ): Observable<GetHostDeviceSummariesResponse> {
    return this.http.get<GetHostDeviceSummariesResponse>(
      `${this.apiUrl}/${hostName}/devices`,
    );
  }

  override getHostDebugInfo(
    hostName: string,
  ): Observable<GetHostDebugInfoResponse> {
    return this.http.get<GetHostDebugInfoResponse>(
      `${this.apiUrl}/${hostName}/debug-info`,
    );
  }

  override getPopularFlags(hostName: string): Observable<PopularFlag[]> {
    return this.http.get<PopularFlag[]>(
      `${this.apiUrl}/${hostName}/popular-flags`,
    );
  }

  override updatePassThroughFlags(
    hostName: string,
    flags: string,
  ): Observable<void> {
    return this.http.post<void>(
      `${this.apiUrl}/${hostName}/updatePassThroughFlags`,
      {
        flags,
      },
    );
  }

  override getReleaseConfigs(
    hostName: string,
  ): Observable<HostReleaseConfig[]> {
    return this.http.get<HostReleaseConfig[]>(
      `${this.apiUrl}/${hostName}/release-configs`,
    );
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

  override checkRemoteControlEligibility(
    hostName: string,
    targets: DeviceTarget[],
  ): Observable<CheckRemoteControlEligibilityResponse> {
    return this.http
      .post<CheckRemoteControlEligibilityResponse>(
        `${this.apiUrl}/${hostName}/checkRemoteControlEligibility`,
        {
          'targets': targets,
        },
      )
      .pipe(
        map((response) => {
          for (const res of response.results) {
            res.supportedProxyTypes =
              res.supportedProxyTypes?.map(toDeviceProxyType) ?? [];
          }

          if (response.sessionOptions) {
            response.sessionOptions.commonProxyTypes =
              response.sessionOptions.commonProxyTypes?.map(
                toDeviceProxyType,
              ) ?? [];
          }
          return response;
        }),
      );
  }

  override remoteControlDevices(
    hostName: string,
    req: RemoteControlDevicesRequest,
  ): Observable<RemoteControlDevicesResponse> {
    return this.http.post<RemoteControlDevicesResponse>(
      `${this.apiUrl}/${hostName}/remoteControlDevices`,
      req,
    );
  }

  override decommissionHost(
    hostName: string,
  ): Observable<DecommissionHostResponse> {
    return this.http.post<DecommissionHostResponse>(
      `${this.apiUrl}/${hostName}:decommission`,
      {},
    );
  }

  override releaseLabServer(
    hostName: string,
    req: ReleaseLabServerRequest,
  ): Observable<ReleaseLabServerResponse> {
    return this.http.post<ReleaseLabServerResponse>(
      `${this.apiUrl}/${hostName}:deploy`,
      req,
    );
  }

  override startLabServer(
    hostName: string,
  ): Observable<StartLabServerResponse> {
    return this.http.post<StartLabServerResponse>(
      `${this.apiUrl}/${hostName}:start`,
      {},
    );
  }

  override restartLabServer(
    hostName: string,
  ): Observable<RestartLabServerResponse> {
    return this.http.post<RestartLabServerResponse>(
      `${this.apiUrl}/${hostName}:restart`,
      {},
    );
  }

  override stopLabServer(hostName: string): Observable<StopLabServerResponse> {
    return this.http.post<StopLabServerResponse>(
      `${this.apiUrl}/${hostName}:stop`,
      {},
    );
  }
}
