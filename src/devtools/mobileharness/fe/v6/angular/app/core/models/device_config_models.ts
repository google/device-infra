/**
 * @fileoverview Data models for the Device Configuration feature.
 * These interfaces define the shape of data used by the ConfigService
 * and the Device Configuration UI components.
 */

import {WritePermissionResult} from './action_common';


/**
 * Represents a single dimension name-value pair.
 * A device dimension is defined by a name and a corresponding value.
 */
export declare interface DeviceDimension {
  name: string;
  value: string;
}

/**
 * Defines who can control and use a device.
 */
export declare interface PermissionInfo {
  /**
   * List of users or groups who have full control over the device,
   * including changing its configuration.
   * Usernames are typically without domain. Group names are as defined in the system.
   */
  owners?: string[];
  /**
   * List of users or groups who are allowed to execute tests on the device.
   */
  executors?: string[];
}

/**
 * Configuration for the device's default Wi-Fi network.
 */
export declare interface WifiConfig {
  /**
   * Type of Wi-Fi configuration.
   * 'none': No default Wi-Fi is set.
   * 'pre-configured': Uses a known network from a predefined list.
   * 'custom': Uses a custom SSID and PSK.
   */
  type: 'none' | 'pre-configured' | 'custom';
  /** The network name (SSID). Relevant for 'pre-configured' and 'custom'. */
  ssid: string;
  /** The password (PSK) for the network. Relevant for 'custom'. */
  psk: string;
  /** Whether the network is hidden (does not broadcast its SSID). */
  scanSsid: boolean;
}

/**
 * Represents a recommended Wi-Fi network configuration.
 */
export declare interface RecommendedWifi {
  ssid: string;
  psk: string;
}

/**
 * The response object for fetching recommended Wi-Fi networks.
 */
export declare interface GetRecommendedWifiResponse {
  recommendations: RecommendedWifi[];
}

/**
 * Settings related to device stability and reboot behavior during testing.
 */
export declare interface StabilitySettings {
  /**
   * The maximum number of consecutive test failures on this device
   * before the device is rebooted.
   */
  maxConsecutiveFail: number;
  /**
   * The maximum number of tests (passed or failed) that can run on this device
   * between reboots.
   */
  maxConsecutiveTest: number;
}

/**
 * The core interface representing the complete device configuration displayed
 * in the UI.
 */
export declare interface DeviceConfig {
  permissions?: PermissionInfo;
  wifi?: WifiConfig;
  dimensions?: {
    /**
     * Dimensions that describe the device's capabilities or attributes.
     * Jobs can request these to target devices.
     */
    supported?: DeviceDimension[];
    /**
     * Dimensions that a job MUST specify to be allocated this device.
     * Used to reserve devices for specific pools or purposes.
     */
    required?: DeviceDimension[];
  };
  settings?: StabilitySettings;
}

/**
 * Describes the editability of a visible part.
 */
export declare interface Editability {
  editable?: boolean;
  // Message explaining why editable is false.
  reason?: string;
}

/**
 * Represents the UI control status of a single configuration part.
 */
export declare interface PartStatus {
  // True if the part should be visible in the UI.
  visible?: boolean;
  // Details about editability, only relevant and expected if visible is true.
  editability?: Editability;
}

/**
 * Defines which parts of the DeviceConfig are visible and editable in the UI.
 */
export declare interface DeviceConfigUiStatus {
  permissions: PartStatus;
  wifi: PartStatus;
  dimensions: PartStatus;
  settings: PartStatus;
}

/**
 * The result object returned when fetching a device's configuration.
 */
export declare interface GetDeviceConfigResult {
  /** The device's configuration, or null if no config is set. */
  deviceConfig: DeviceConfig | null;
  /** True if the device inherits its base configuration from the host. */
  isHostManaged: boolean;
  /** The name of the host the device is connected to. */
  hostName?: string;
  /**
   * The UI status for the device configuration sections.
   * If omitted or partially provided, the frontend assumes visible=true and editable=true by default.
   */
  uiStatus?: Partial<DeviceConfigUiStatus>;
}

/**
 * The result object returned when checking device config permissions.
 */
export declare interface CheckDeviceConfigPermissionResult
  extends WritePermissionResult {}

/**
 * Error code for update operations matching config_service.proto UpdateError.Code.
 */
export type UpdateErrorCode =
  | 'CODE_UNSPECIFIED'
  | 'SELF_LOCKOUT_DETECTED'
  | 'PERMISSION_DENIED'
  | 'VALIDATION_ERROR'
  | 'UNKNOWN';

/**
 * Error details for update operations matching config_service.proto UpdateError.
 */
export declare interface UpdateError {
  code: UpdateErrorCode;
  message?: string;
}

/**
 * The result object returned after attempting to update a device's
 * configuration.
 */
export declare interface UpdateDeviceConfigResult {
  /** True if the update operation was successful. */
  success: boolean;
  /** Details about the error if the update failed. */
  error?: UpdateError;
}

/**
 * Enum representing the distinct sections of the device configuration dialog.
 */
export enum ConfigSection {
  DEVICE_CONFIG_SECTION_UNKNOWN = 'DEVICE_CONFIG_SECTION_UNKNOWN',
  PERMISSIONS = 'PERMISSIONS',
  WIFI = 'WIFI',
  DIMENSIONS = 'DIMENSIONS',
  STABILITY = 'STABILITY',
  ALL = 'ALL',
}

/**
 * Interface for the request object used when updating a device's configuration.
 */
export declare interface UpdateDeviceConfigRequest {
  id: string;
  config: DeviceConfig;
  section: ConfigSection;
  options?: {overrideSelfLockout?: boolean};
  universe?: string;
}

/**
 * Change classification for a selected device in a batch configuration dialog.
 * Aligns with config_service.proto DeviceConfigChangeStatus.
 */
export enum DeviceConfigChangeStatus {
  DEVICE_CONFIG_CHANGE_STATUS_UNSPECIFIED = 'DEVICE_CONFIG_CHANGE_STATUS_UNSPECIFIED',
  /**
   * The device configuration can be modified by the current user, and no
   * target value was supplied in the request yet (Step 1 initial load).
   */
  DEVICE_CONFIG_CHANGE_STATUS_WRITABLE = 'DEVICE_CONFIG_CHANGE_STATUS_WRITABLE',
  /**
   * The device is writable and its current configuration differs from the
   * requested target configuration (Step 2 review).
   */
  DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE = 'DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE',
  /**
   * The device is writable and its current configuration already matches the
   * requested target configuration (Step 2 review). No write is needed.
   */
  DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES = 'DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES',
  /**
   * The device configuration cannot be modified by the current user (e.g.
   * locked by host-level configuration, Config Pusher, insufficient
   * permission, or per-device config read failure).
   */
  DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY = 'DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY',
}

/**
 * Default Wi-Fi network parameters on a device in batch Wi-Fi operations.
 * Unlike single-device WifiConfig, carries no legacy UI "type" field.
 * Aligns with config_service.proto DeviceWifi.
 */
export declare interface DeviceWifi {
  /** Wi-Fi network name (SSID). Empty when no default Wi-Fi is configured. */
  ssid: string;
  /** Password (PSK) for the Wi-Fi network. Empty for open networks. */
  psk?: string;
  /** True if the network hides its SSID and requires active scanning. */
  scanSsid?: boolean;
}

/**
 * Clears the default Wi-Fi network on the target devices.
 * Aligns with config_service.proto ClearDeviceWifi.
 */
export declare interface ClearDeviceWifi {}

/**
 * Target Wi-Fi state to preview or apply in a batch operation.
 * Aligns with config_service.proto TargetDeviceWifi.
 */
export declare interface TargetDeviceWifi {
  /** Removes default_wifi from the target devices (sets Wi-Fi to None). */
  clearWifi?: ClearDeviceWifi;
  /** Sets default_wifi to this network on the target devices. */
  wifi?: DeviceWifi;
}

/**
 * Indicates the network comes from the curated credential store (1P),
 * where fleet-wide device counts do not apply.
 * Aligns with config_service.proto GetWifiSuggestionsResponse.CuratedWifiPreset.
 */
export declare interface CuratedWifiPreset {}

/**
 * Indicates the network is aggregated from active device configurations
 * in the fleet (ATS), carrying the fleet usage count.
 * Aligns with config_service.proto GetWifiSuggestionsResponse.FleetWifiInUse.
 */
export declare interface FleetWifiInUse {
  deviceCount?: number;
}

/**
 * A candidate Wi-Fi network available to pick from in batch Wi-Fi operations.
 * Aligns with config_service.proto GetWifiSuggestionsResponse.CandidateWifi.
 */
export declare interface CandidateWifi {
  /** Network credentials (ssid, psk, scan_ssid). */
  wifi: DeviceWifi;
  curatedPreset?: CuratedWifiPreset;
  fleetInUse?: FleetWifiInUse;
}

/**
 * Request for GetWifiSuggestions.
 * Aligns with config_service.proto GetWifiSuggestionsRequest.
 */
export declare interface GetWifiSuggestionsRequest {
  universe?: string;
  fleet?: string;
}

/**
 * Response for GetWifiSuggestions.
 * Aligns with config_service.proto GetWifiSuggestionsResponse.
 */
export declare interface GetWifiSuggestionsResponse {
  candidateWifis?: CandidateWifi[];
}

/**
 * Current Wi-Fi configuration and change status for a single selected device.
 * Aligns with config_service.proto BatchGetDeviceWifiConfigsResponse.DeviceWifiConfigEntry.
 */
export declare interface DeviceWifiConfigEntry {
  deviceId: string;
  /** Current default Wi-Fi configuration (empty ssid if unconfigured). */
  currentWifi?: DeviceWifi;
  /** Change status for this device. */
  status: DeviceConfigChangeStatus;
  /** Explanation when status is DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY. */
  readOnlyReason?: string;
}

/**
 * Request for BatchGetDeviceWifiConfigs.
 * Aligns with config_service.proto BatchGetDeviceWifiConfigsRequest.
 */
export declare interface BatchGetDeviceWifiConfigsRequest {
  deviceIds: string[];
  target?: TargetDeviceWifi;
  universe?: string;
  fleet?: string;
}

/**
 * Response for BatchGetDeviceWifiConfigs.
 * Aligns with config_service.proto BatchGetDeviceWifiConfigsResponse.
 */
export declare interface BatchGetDeviceWifiConfigsResponse {
  entries?: DeviceWifiConfigEntry[];
}

/**
 * Request for BatchUpdateDeviceWifiConfigs.
 * Aligns with config_service.proto BatchUpdateDeviceWifiConfigsRequest.
 */
export declare interface BatchUpdateDeviceWifiConfigsRequest {
  deviceIds: string[];
  target: TargetDeviceWifi;
  universe?: string;
  fleet?: string;
}

/**
 * Response for BatchUpdateDeviceWifiConfigs.
 * Maps failed device_id to its corresponding UpdateError.
 * An empty map indicates all target devices succeeded.
 * Aligns with config_service.proto BatchUpdateDeviceWifiConfigsResponse.
 */
export declare interface BatchUpdateDeviceWifiConfigsResponse {
  errors?: Record<string, UpdateError>;
}

