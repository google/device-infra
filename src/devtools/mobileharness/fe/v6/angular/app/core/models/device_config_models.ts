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
 * The result object returned when checking device write permissions.
 */
export declare interface CheckDeviceWritePermissionResult
  extends WritePermissionResult {}

/**
 * The result object returned after attempting to update a device's
 * configuration.
 */
export declare interface UpdateDeviceConfigResult {
  /** True if the update operation was successful. */
  success: boolean;
  /** Details about the error if the update failed. */
  error?: {
    code:
      | 'CODE_UNSPECIFIED'
      | 'SELF_LOCKOUT_DETECTED'
      | 'PERMISSION_DENIED'
      | 'VALIDATION_ERROR'
      | 'UNKNOWN';
    message?: string;
  };
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
 * Current Wi-Fi configuration and authorization for a target device in batch Wi-Fi operations.
 */
export declare interface DeviceCurrentWifi {
  deviceId: string;
  wifi?: WifiConfig;
  writable: boolean;
  unwritableReason?: string;
}

/**
 * A candidate Wi-Fi network available to pick from in batch Wi-Fi operations.
 */
export declare interface CandidateWifi {
  ssid: string;
  psk?: string;
  scanSsid: boolean;
  deviceCount: number;
}

/**
 * Request for GetBatchWifiContext.
 */
export declare interface GetBatchWifiContextRequest {
  deviceIds: string[];
}

/**
 * Response for GetBatchWifiContext.
 */
export declare interface GetBatchWifiContextResponse {
  current: DeviceCurrentWifi[];
  candidateWifis: CandidateWifi[];
}

/**
 * Request for BatchUpdateDeviceConfig.
 */
export declare interface BatchUpdateDeviceConfigRequest {
  deviceIds: string[];
  wifi?: Partial<WifiConfig>;
  dimension?: unknown;
}

/**
 * Error detail for a failed device update in BatchUpdateDeviceConfig.
 */
export declare interface BatchUpdateError {
  code?: string;
  message?: string;
}

/**
 * Response for BatchUpdateDeviceConfig.
 */
export declare interface BatchUpdateDeviceConfigResponse {
  errors?: Record<string, BatchUpdateError | string>;
}

