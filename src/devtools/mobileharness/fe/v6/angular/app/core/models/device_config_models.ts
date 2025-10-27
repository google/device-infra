/**
 * @fileoverview Data models for the Device Configuration feature.
 * These interfaces define the shape of data used by the ConfigService
 * and the Device Configuration UI components.
 */

/**
 * Represents a single dimension name-value pair.
 * A device dimension is defined by a name and a corresponding value.
 */
export interface DeviceDimension {
  name: string;
  value: string;
}

/**
 * Defines who can control and use a device.
 */
export interface PermissionInfo {
  /**
   * List of users or groups who have full control over the device,
   * including changing its configuration.
   * Usernames are typically without domain. Group names are as defined in the system.
   */
  owners: string[];
  /**
   * List of users or groups who are allowed to execute tests on the device.
   */
  executors: string[];
}

/**
 * Configuration for the device's default Wi-Fi network.
 */
export interface WifiConfig {
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
 * Settings related to device stability and reboot behavior during testing.
 */
export interface StabilitySettings {
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
export interface DeviceConfig {
  permissions: PermissionInfo;
  wifi: WifiConfig;
  dimensions: {
    /**
     * Dimensions that describe the device's capabilities or attributes.
     * Jobs can request these to target devices.
     */
    supported: DeviceDimension[];
    /**
     * Dimensions that a job MUST specify to be allocated this device.
     * Used to reserve devices for specific pools or purposes.
     */
    required: DeviceDimension[];
  };
  settings: StabilitySettings;
}

/**
 * The result object returned when fetching a device's configuration.
 */
export interface GetDeviceConfigResult {
  /** The device's configuration, or null if no config is set. */
  deviceConfig: DeviceConfig | null;
  /** True if the device inherits its base configuration from the host. */
  isHostManaged: boolean;
  /** The name of the host the device is connected to. */
  hostName?: string;
}

/**
 * The result object returned when checking device write permissions.
 */
export interface CheckDeviceWritePermissionResult {
  /** True if the current user has permission to edit the device config. */
  hasPermission: boolean;
  /** The username of the user for whom the permission was checked. */
  userName?: string;
}

/**
 * The result object returned after attempting to update a device's
 * configuration.
 */
export interface UpdateDeviceConfigResult {
  /** True if the update operation was successful. */
  success: boolean;
  /** Details about the error if the update failed. */
  error?: {
    code:
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
  UNKNOWN = 'UNKNOWN',
  PERMISSIONS = 'PERMISSIONS',
  WIFI = 'WIFI',
  DIMENSIONS = 'DIMENSIONS',
  STABILITY = 'STABILITY',
  ALL = 'ALL',
}

/**
 * Interface for the request object used when updating a device's configuration.
 */
export interface UpdateDeviceConfigRequest {
  deviceId: string;
  config: DeviceConfig;
  section: ConfigSection;
  options?: {overrideSelfLockout?: boolean};
}
