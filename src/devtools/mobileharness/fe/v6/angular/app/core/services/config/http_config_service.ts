import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';

import {APP_DATA, AppData} from '../../models/app_data';
import {
  CheckDeviceWritePermissionResult,
  DeviceConfig,
  GetDeviceConfigResult,
  GetRecommendedWifiResponse,
  RecommendedWifi,
  UpdateDeviceConfigRequest,
  UpdateDeviceConfigResult,
} from '../../models/device_config_models';
import {
  CheckHostWritePermissionResult,
  GetHostConfigResult,
  GetHostDefaultDeviceConfigResponse,
  UpdateHostConfigRequest,
  UpdateHostConfigResult,
} from '../../models/host_config_models';
import {
  normalizeDeviceConfig,
  normalizeDeviceConfigUiStatus,
} from '../../utils/device_config_utils';
import {normalizeHostConfig as normalize} from '../../utils/host_config_utils';
import {ConfigService} from './config_service';

/** An implementation of the ConfigService that uses HTTP to fetch data. */
@Injectable()
export class HttpConfigService extends ConfigService {
  private readonly appData: AppData = inject(APP_DATA);
  private readonly apiUrl = `${this.appData.labConsoleServerUrl}/v6`;
  private readonly http = inject(HttpClient);

  constructor() {
    super();
  }

  // ===== Device Config Methods =====

  override getDeviceConfig(
    deviceId: string,
  ): Observable<GetDeviceConfigResult> {
    return this.http
      .get<GetDeviceConfigResult>(`${this.apiUrl}/devices/${deviceId}/config`)
      .pipe(
        map((result) => {
          result.deviceConfig = normalizeDeviceConfig(result.deviceConfig!);
          result.uiStatus = normalizeDeviceConfigUiStatus(result.uiStatus);
          return result;
        }),
      );
  }

  override checkDeviceWritePermission(
    deviceId: string,
    universe?: string,
  ): Observable<CheckDeviceWritePermissionResult> {
    return this.http.post<CheckDeviceWritePermissionResult>(
      `${this.apiUrl}/devices/${deviceId}/config:checkWritePermission`,
      {id: deviceId, universe},
    );
  }

  override updateDeviceConfig(
    request: UpdateDeviceConfigRequest,
  ): Observable<UpdateDeviceConfigResult> {
    return this.http.post<UpdateDeviceConfigResult>(
      `${this.apiUrl}/devices/${request.id}/config:update`,
      request,
    );
  }

  override getRecommendedWifi(): Observable<RecommendedWifi[]> {
    return this.http
      .get<GetRecommendedWifiResponse>(
        `${this.apiUrl}/configs/wifi/recommendations`,
      )
      .pipe(map((response) => response.recommendations || []));
  }

  // ===== Host Config Methods =====

  override getHostDefaultDeviceConfig(
    hostName: string,
  ): Observable<DeviceConfig | null> {
    return this.http
      .post<GetHostDefaultDeviceConfigResponse>(
        `${this.apiUrl}/hosts/${hostName}:getDefaultDeviceConfig`,
        {},
      )
      .pipe(
        map((response) => normalizeDeviceConfig(response.deviceConfig || null)),
      );
  }

  override getHostConfig(hostName: string): Observable<GetHostConfigResult> {
    return this.http
      .get<GetHostConfigResult>(`${this.apiUrl}/hosts/${hostName}/config`)
      .pipe(
        map((result) => {
          if (result.hostConfig) {
            result.hostConfig = normalize(result.hostConfig);
          }
          return result;
        }),
      );
  }

  override checkHostWritePermission(
    hostName: string,
    universe?: string,
  ): Observable<CheckHostWritePermissionResult> {
    return this.http.post<CheckHostWritePermissionResult>(
      `${this.apiUrl}/hosts/${hostName}/config:checkWritePermission`,
      {hostName, universe},
    );
  }

  override updateHostConfig(
    request: UpdateHostConfigRequest,
  ): Observable<UpdateHostConfigResult> {
    return this.http.post<UpdateHostConfigResult>(
      `${this.apiUrl}/hosts/${request.hostName}/config:update`,
      request,
    );
  }
}
