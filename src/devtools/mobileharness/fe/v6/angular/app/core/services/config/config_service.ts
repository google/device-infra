import {InjectionToken} from '@angular/core';
import {Observable} from 'rxjs';
import {
  CheckDeviceWritePermissionResult,
  DeviceConfig,
  GetDeviceConfigResult,
  RecommendedWifi,
  UpdateDeviceConfigRequest,
  UpdateDeviceConfigResult,
} from '../../models/device_config_models';
import {
  CheckHostWritePermissionResult,
  GetHostConfigResult,
  UpdateHostConfigRequest,
  UpdateHostConfigResult,
} from '../../models/host_config_models';

/**
 * Injection token for the ConfigService.
 */
export const CONFIG_SERVICE = new InjectionToken<ConfigService>(
  'ConfigService',
);

/**
 * Abstract class defining the contract for configuration management operations
 * for both devices and host defaults.
 */
export abstract class ConfigService {
  // ===== Device Config Methods =====

  /**
   * Retrieves the configuration for a specific device.
   * Also returns metadata about host management.
   * @param deviceId The unique identifier of the device.
   * @returns An Observable emitting the device configuration result.
   */
  abstract getDeviceConfig(deviceId: string): Observable<GetDeviceConfigResult>;

  /**
   * Checks if the current authenticated user has permission to write/modify
   * the configuration of the specified device.
   * @param deviceId The unique identifier of the device.
   * @returns An Observable emitting the permission check result.
   */
  abstract checkDeviceWritePermission(
    deviceId: string,
  ): Observable<CheckDeviceWritePermissionResult>;

  /**
   * Updates the configuration for a specific device.
   * This method handles self-lockout prevention checks on the backend.
   * @param request The update request object.
   * @returns An Observable emitting the result of the update operation.
   */
  abstract updateDeviceConfig(
    request: UpdateDeviceConfigRequest,
  ): Observable<UpdateDeviceConfigResult>;

  /**
   * Retrieves a list of recommended Wi-Fi networks.
   * @return An Observable emitting the list of recommended Wi-Fi configurations.
   */
  abstract getRecommendedWifi(): Observable<RecommendedWifi[]>;

  // ===== Host Config Methods =====

  /**
   * Retrieves the default DeviceConfig set at the host level.
   * @param hostName The name of the host.
   * @returns An Observable emitting the host's default DeviceConfig, or null if not set.
   */
  abstract getHostDefaultDeviceConfig(
    hostName: string,
  ): Observable<DeviceConfig | null>;

  /**
   * Retrieves the configuration and UI control status for a specific host.
   * @param hostName The name of the host.
   * @returns An Observable emitting the host configuration result.
   */
  abstract getHostConfig(hostName: string): Observable<GetHostConfigResult>;

  /**
   * Checks if the current authenticated user has permission to write/modify
   * the configuration of the specified host.
   * @param hostName The name of the host.
   * @returns An Observable emitting the permission check result.
   */
  abstract checkHostWritePermission(
    hostName: string,
  ): Observable<CheckHostWritePermissionResult>;

  /**
   * Updates the configuration for a specific host.
   * This method handles self-lockout prevention checks on the backend.
   * @param request The update request object.
   * @returns An Observable emitting the result of the update operation.
   */
  abstract updateHostConfig(
    request: UpdateHostConfigRequest,
  ): Observable<UpdateHostConfigResult>;
}
