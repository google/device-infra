import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';

import {CheckDeviceWritePermissionResult, DeviceConfig, GetDeviceConfigResult, UpdateDeviceConfigRequest, UpdateDeviceConfigResult} from '../../models/device_config_models';
import {CheckHostWritePermissionResult, GetHostConfigResult, UpdateHostConfigRequest, UpdateHostConfigResult} from '../../models/host_config_models';

import {ConfigService} from './config_service';

/** An implementation of the ConfigService that uses HTTP to fetch data. */
export class HttpConfigService extends ConfigService {
  private readonly apiUrl = '/api/v1';

  private readonly http: HttpClient;

  constructor(http: HttpClient) {
    super();
    this.http = http;
  }

  // ===== Device Config Methods =====

  override getDeviceConfig(
      deviceId: string,
      ): Observable<GetDeviceConfigResult> {
    return this.http.get<GetDeviceConfigResult>(
        `${this.apiUrl}/devices/${deviceId}/config`,
    );
  }

  override checkDeviceWritePermission(
      deviceId: string,
      ): Observable<CheckDeviceWritePermissionResult> {
    return this.http.get<CheckDeviceWritePermissionResult>(
        `${this.apiUrl}/devices/${deviceId}/config:checkWritePermission`,
    );
  }

  override updateDeviceConfig(
      request: UpdateDeviceConfigRequest,
      ): Observable<UpdateDeviceConfigResult> {
    return this.http.post<UpdateDeviceConfigResult>(
        `${this.apiUrl}/devices/${request.deviceId}/config`,
        request,
    );
  }

  // ===== Host Config Methods =====

  override getHostDefaultDeviceConfig(
      hostName: string,
      ): Observable<DeviceConfig|null> {
    return this.http.get<DeviceConfig|null>(
        `${this.apiUrl}/hosts/${hostName}/defaultDeviceConfig`,
    );
  }

  override getHostConfig(hostName: string): Observable<GetHostConfigResult> {
    return this.http.get<GetHostConfigResult>(
        `${this.apiUrl}/hosts/${hostName}/config`,
    );
  }

  override checkHostWritePermission(
      hostName: string,
      ): Observable<CheckHostWritePermissionResult> {
    return this.http.get<CheckHostWritePermissionResult>(
        `${this.apiUrl}/hosts/${hostName}/config:checkWritePermission`,
    );
  }

  override updateHostConfig(
      request: UpdateHostConfigRequest,
      ): Observable<UpdateHostConfigResult> {
    return this.http.post<UpdateHostConfigResult>(
        `${this.apiUrl}/hosts/${request.hostName}/config`,
        request,
    );
  }
}
