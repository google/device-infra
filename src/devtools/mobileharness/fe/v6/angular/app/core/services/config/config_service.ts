import {InjectionToken} from '@angular/core';
import {Observable} from 'rxjs';
import {
  BatchGetConfigurableDimensionKeysRequest,
  BatchGetConfigurableDimensionKeysResponse,
  BatchGetDeviceDimensionConfigsRequest,
  BatchGetDeviceDimensionConfigsResponse,
  BatchGetDeviceWifiConfigsRequest,
  BatchGetDeviceWifiConfigsResponse,
  BatchUpdateDeviceDimensionConfigsRequest,
  BatchUpdateDeviceDimensionConfigsResponse,
  BatchUpdateDeviceWifiConfigsRequest,
  BatchUpdateDeviceWifiConfigsResponse,
  CheckDeviceConfigPermissionResult,
  DeviceConfig,
  GetConfigurableDimensionsRequest,
  GetConfigurableDimensionsResponse,
  GetDeviceConfigResult,
  GetDimensionValueSuggestionsRequest,
  GetDimensionValueSuggestionsResponse,
  GetWifiSuggestionsRequest,
  GetWifiSuggestionsResponse,
  RecommendedWifi,
  UpdateDeviceConfigRequest,
  UpdateDeviceConfigResult,
} from '../../models/device_config_models';
import {
  CheckHostConfigPermissionResult,
  GetHostConfigResult,
  UnlockHostPropertiesResponse,
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
   * @param hostName The host name to disambiguate devices with same ID under different hosts.
   * @return An Observable emitting the device configuration result.
   */
  abstract getDeviceConfig(
    deviceId: string,
    hostName: string, // hostName is required to disambiguate devices.
  ): Observable<GetDeviceConfigResult>;

  /**
   * Checks if the current authenticated user has permission to write/modify
   * the configuration of the specified device.
   * @param deviceId The unique identifier of the device.
   * @return An Observable emitting the permission check result.
   */
  abstract checkDeviceConfigPermission(
    deviceId: string,
    universe?: string,
  ): Observable<CheckDeviceConfigPermissionResult>;

  /**
   * Updates the configuration for a specific device.
   * This method handles self-lockout prevention checks on the backend.
   * @param request The update request object.
   * @return An Observable emitting the result of the update operation.
   */
  abstract updateDeviceConfig(
    request: UpdateDeviceConfigRequest,
  ): Observable<UpdateDeviceConfigResult>;

  /**
   * Retrieves a list of recommended Wi-Fi networks.
   * @return An Observable emitting the list of recommended Wi-Fi configurations.
   */
  abstract getRecommendedWifi(): Observable<RecommendedWifi[]>;

  /**
   * Retrieves candidate Wi-Fi networks (curated presets in 1P; fleet-in-use networks
   * ranked by deviceCount descending in ATS).
   * @param request Optional request object.
   * @return An Observable emitting candidate Wi-Fi networks.
   */
  abstract getWifiSuggestions(
    request?: GetWifiSuggestionsRequest,
  ): Observable<GetWifiSuggestionsResponse>;

  /**
   * Retrieves current Wi-Fi configurations and writability for the selected devices.
   * @param request The batch request containing device IDs.
   * @return An Observable emitting device Wi-Fi configs and writability.
   */
  abstract batchGetDeviceWifiConfigs(
    request: BatchGetDeviceWifiConfigsRequest,
  ): Observable<BatchGetDeviceWifiConfigsResponse>;

  /**
   * Updates the default Wi-Fi configuration across the selected devices.
   * @param request The batch update request with target device IDs and Wi-Fi configuration.
   * @return An Observable emitting per-device update errors.
   */
  abstract batchUpdateDeviceWifiConfigs(
    request: BatchUpdateDeviceWifiConfigsRequest,
  ): Observable<BatchUpdateDeviceWifiConfigsResponse>;

  /**
   * Retrieves dimensions available for batch configuration.
   * @param request Optional request containing search query to filter dimensions.
   * @return An Observable emitting the configurable dimensions response.
   */
  abstract getConfigurableDimensions(
    request?: GetConfigurableDimensionsRequest,
  ): Observable<GetConfigurableDimensionsResponse>;

  /**
   * Retrieves existing configurable dimension keys from DeviceConfig along with
   * system-detected dimension blocklist.
   * @param request The batch configurable dimension keys request.
   * @return An Observable emitting candidate keys and system dimension names.
   */
  abstract batchGetConfigurableDimensionKeys(
    request: BatchGetConfigurableDimensionKeysRequest,
  ): Observable<BatchGetConfigurableDimensionKeysResponse>;

  /**
   * Retrieves fleet-wide candidate value-sets for a specific dimension target.
   * @param request The dimension value suggestions request.
   * @return An Observable emitting candidate value-sets.
   */
  abstract getDimensionValueSuggestions(
    request: GetDimensionValueSuggestionsRequest,
  ): Observable<GetDimensionValueSuggestionsResponse>;

  /**
   * Retrieves current values and writability for selected devices under a dimension target.
   * @param request The batch get device dimension configs request.
   * @return An Observable emitting per-device dimension configurations.
   */
  abstract batchGetDeviceDimensionConfigs(
    request: BatchGetDeviceDimensionConfigsRequest,
  ): Observable<BatchGetDeviceDimensionConfigsResponse>;

  /**
   * Replaces the value-set for a specific dimension target across selected devices.
   * @param request The batch update device dimension configs request.
   * @return An Observable emitting per-device update errors.
   */
  abstract batchUpdateDeviceDimensionConfigs(
    request: BatchUpdateDeviceDimensionConfigsRequest,
  ): Observable<BatchUpdateDeviceDimensionConfigsResponse>;

  // ===== Host Config Methods =====

  /**
   * Retrieves the default DeviceConfig set at the host level.
   * @param hostName The name of the host.
   * @return An Observable emitting the host's default DeviceConfig, or null if not set.
   */
  abstract getHostDefaultDeviceConfig(
    hostName: string,
  ): Observable<DeviceConfig | null>;

  /**
   * Retrieves the configuration and UI control status for a specific host.
   * @param hostName The name of the host.
   * @return An Observable emitting the host configuration result.
   */
  abstract getHostConfig(hostName: string): Observable<GetHostConfigResult>;

  /**
   * Checks if the current authenticated user has permission to write/modify
   * the configuration of the specified host.
   * @param hostName The name of the host.
   * @return An Observable emitting the permission check result.
   */
  abstract checkHostConfigPermission(
    hostName: string,
    universe?: string,
  ): Observable<CheckHostConfigPermissionResult>;

  /**
   * Updates the configuration for a specific host.
   * This method handles self-lockout prevention checks on the backend.
   * @param request The update request object.
   * @return An Observable emitting the result of the update operation.
   */
  abstract updateHostConfig(
    request: UpdateHostConfigRequest,
  ): Observable<UpdateHostConfigResult>;

  /**
   * Unlocks the host properties for a specific host by disabling Config Pusher management.
   * @param hostName The name of the host.
   * @return An Observable emitting the result of the unlock operation.
   */
  abstract unlockHostProperties(
    hostName: string,
  ): Observable<UnlockHostPropertiesResponse>;
}
