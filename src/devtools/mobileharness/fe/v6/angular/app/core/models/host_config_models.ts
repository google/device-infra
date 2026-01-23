/**
 * @fileoverview Data models for the Host Configuration feature.
 */

import {
  DeviceConfig,
  ConfigSection as DeviceConfigSection,
} from './device_config_models';

/**
 * Defines a principal (user or group) allowed to SSH as a specific login user.
 */
export declare interface SshPrincipal {
  loginUser: string; // The username on the host machine (e.g., 'root', 'mobileharness')
  principals: string[]; // List of users or MDB groups allowed to assume the loginUser role
}

/**
 * Permissions for the host.
 */
export declare interface HostPermissions {
  hostAdmins: string[]; // Users/groups who can edit this host config
  sshAccess: SshPrincipal[]; // Defines SSH access rules. Will be [] if no rules are set.
}

/**
 * Device configuration mode for the host.
 */
export type DeviceConfigMode =
  | 'NODEVICE_CONFIG_MODE_UNSPECIFIEDNE'
  | 'PER_DEVICE'
  | 'SHARED';

/**
 * Specifications for Maneki device detection.
 */
export declare interface ManekiSpec {
  type: string;
  macAddress: string;
}

/**
 * Settings for device discovery.
 */
export declare interface DeviceDiscoverySettings {
  monitoredDeviceUuids: string[];
  testbedUuids: string[];
  miscDeviceUuids: string[];
  overTcpIps: string[];
  overSshDevices: Array<{
    ipAddress: string;
    username: string;
    password: string; // Note: Sensitivity of storing passwords should be reviewed.
    sshDeviceType: string;
  }>;
  manekiSpecs: ManekiSpec[];
}

/**
 * Represents a single host property key-value pair.
 */
export declare interface HostProperty {
  key: string;
  value: string;
}

/**
 * The main interface for host configuration.
 */
export declare interface HostConfig {
  permissions: HostPermissions;
  deviceConfigMode: DeviceConfigMode;
  deviceConfig?: DeviceConfig; // Optional to match JSON omission
  hostProperties: HostProperty[]; // Array of key-value pairs
  deviceDiscovery: DeviceDiscoverySettings;
}

/**
 * Describes the editability of a visible part.
 */
export declare interface Editability {
  editable: boolean;
  // Message explaining why editable is false.
  reason?: string;
}

/**
 * Represents the UI control status of a single configuration part.
 */
export declare interface PartStatus {
  // True if the part should be visible in the UI.
  visible: boolean;
  // Details about editability, only relevant and expected if visible is true.
  editability?: Editability;
}

/**
 * Defines the UI status for the Host Properties section.
 */
export declare interface HostPropertiesUiStatus {
  // Status of the Host Properties section as a whole.
  // visible: controls if the section is shown.
  // editability.editable: controls if the "Add Property" button is enabled.
  sectionStatus: PartStatus;

  // Optional overrides for the editability of specific HostProperty items,
  // keyed by their index in the HostConfig.hostProperties array.
  // Items NOT in this map are considered editable by default.
  itemEditabilityOverrides?: {[index: number]: Editability};
}

/**
 * Defines which parts of the HostConfig are visible and editable in the Lab Console UI.
 * This is derived from the backend's management mode and host type.
 */
export declare interface HostConfigUiStatus {
  // Corresponds to parts within the HOST_PERMISSIONS section
  hostAdmins: PartStatus;
  sshAccess: PartStatus;

  // Corresponds to the DEVICE_CONFIG_MODE section
  deviceConfigMode: PartStatus;

  // Corresponds to the DEVICE_CONFIG section
  deviceConfig: PartStatus;

  // Corresponds to the HOST_PROPERTIES section
  hostProperties: HostPropertiesUiStatus;

  // Corresponds to the DEVICE_DISCOVERY section
  deviceDiscovery: PartStatus;
}

/**
 * Result of fetching host configuration.
 */
export declare interface GetHostConfigResult {
  hostConfig?: HostConfig; // Optional as the whole config might be null
  uiStatus: HostConfigUiStatus;
}

/**
 * Enum representing the top-level sections (UI tabs) of the HostConfig object for partial updates.
 */
export enum HostConfigSection {
  HOST_CONFIG_SECTION_UNSPECIFIED = 'HOST_CONFIG_SECTION_UNSPECIFIED',
  HOST_PERMISSIONS = 'HOST_PERMISSIONS',
  DEVICE_CONFIG_MODE = 'DEVICE_CONFIG_MODE',
  DEVICE_CONFIG = 'DEVICE_CONFIG',
  HOST_PROPERTIES = 'HOST_PROPERTIES',
  DEVICE_DISCOVERY = 'DEVICE_DISCOVERY',
}

/**
 * Defines the scope of a partial update.
 */
export declare interface HostConfigUpdateScope {
  section: HostConfigSection;
  deviceConfigSection?: DeviceConfigSection; // Relevant only if section is DEVICE_CONFIG
}

/**
 * Options for the update request.
 */
export declare interface UpdateOptions {
  overrideSelfLockout?: boolean;
}

/**
 * Request to update host configuration.
 */
export declare interface UpdateHostConfigRequest {
  hostName: string;
  config: HostConfig;
  // If scope is omitted, it implies updating all editable parts of the HostConfig
  // based on the user's permissions and the HostConfigUiStatus.
  scope?: HostConfigUpdateScope;
  options?: UpdateOptions;
}

/**
 * Result of updating host configuration.
 */
export declare interface UpdateHostConfigResult {
  success: boolean;
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
 * Result of checking host write permission.
 */
export declare interface CheckHostWritePermissionResult {
  hasPermission: boolean;
  userName?: string;
}
