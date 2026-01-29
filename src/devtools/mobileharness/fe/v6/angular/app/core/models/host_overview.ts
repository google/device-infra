/**
 * @fileoverview Defines interfaces for Host Overview data, structured for
 * presentation in the UI, corresponding to messages in host_resources.proto.
 * These interfaces are designed according to the BFF principle, where the
 * backend provides semantic state and pre-formatted text, and the frontend
 * uses this data to render the UI, including icons, colors, and layouts.
 */

import {HealthState, SubDeviceInfo} from './device_overview';


/**
 * Semantic state of host connectivity with the OmniLab master server.
 * Frontend uses this to determine icon and color.
 */
export type HostConnectivityState = 'RUNNING' | 'MISSING' | 'UNKNOWN';

/**
 * Represents the data needed to render the Host Connectivity card.
 * Corresponds to HostConnectivityStatus in host_resources.proto.
 */
export declare interface HostConnectivityStatus {
  /** The semantic state used by FE to determine styling (icon/color). */
  readonly state: HostConnectivityState;
  /** The display text for the state, e.g., "Running", "Missing". */
  readonly title: string;
  /**
   * Timestamp of when host was first detected as missing, in
   * RFC 3339 format (e.g., "2025-11-04T23:38:00Z").
   * Only provided by backend if state is 'MISSING'.
   * Frontend uses this to calculate and display offline duration.
   */
  readonly missingStartTime?: string;
  /** Tooltip text explaining the current connectivity status. */
  readonly tooltip: string;
}

/**
 * Semantic state of Lab Server activity.
 * Frontend uses this to determine icon, color, and spinning animation.
 */
export type LabServerActivityState =
  | 'STARTED'
  | 'STARTED_BUT_DISCONNECTED'
  | 'STARTING'
  | 'ERROR'
  | 'DRAINING'
  | 'DRAINED'
  | 'STOPPING'
  | 'STOPPED'
  | 'UNKNOWN';

/**
 * Represents the data needed to render Lab Server activity.
 * Corresponds to LabServerInfo.Activity in host_resources.proto.
 */
export declare interface LabServerActivity {
  /** The semantic state used by FE to determine styling (icon/color/spin). */
  readonly state: LabServerActivityState;
  /** The display text for the state, e.g., "Started", "Stopping". */
  readonly title: string;
  /** Tooltip text explaining the current activity state. */
  readonly tooltip: string;
}

/**
 * Represents the data needed to render the Lab Server card.
 * Corresponds to LabServerInfo in host_resources.proto.
 */
export declare interface LabServerInfo {
  /** Host connectivity status details. */
  readonly connectivity: HostConnectivityStatus;
  /**
   * The activity of the lab server.
   * This is optional because it is not applicable to all host types (e.g., Core
   * Lab).
   */
  readonly activity?: LabServerActivity;
  /** The version of the lab server software. */
  readonly version: string;
  /** Pass-through flags configured for the lab server. */
  passThroughFlags: string;
}

/**
 * Semantic state of the Daemon server process.
 * Frontend uses this to determine icon and color.
 */
export type DaemonServerState = 'RUNNING' | 'MISSING' | 'UNKNOWN';

/**
 * Represents the data needed to render Daemon server status.
 * Corresponds to DaemonServerInfo.Status in host_resources.proto.
 */
export declare interface DaemonServerStatus {
  /** The semantic state used by FE to determine styling (icon/color). */
  readonly state: DaemonServerState;
  /** The display text for the state, e.g., "Running", "Missing". */
  readonly title: string;
  /**
   * Timestamp of when daemon was first detected as missing, in RFC 3339
   * format (e.g., "2025-11-04T23:38:00Z").
   * Only provided by backend if state is 'MISSING'.
   * Frontend uses this to calculate and display missing duration.
   */
  readonly missingStartTime?: string;
  /** Tooltip text explaining the current daemon status. */
  readonly tooltip: string;
}

/**
 * Represents the data needed to render the Daemon Server card.
 * Corresponds to DaemonServerInfo in host_resources.proto.
 */
export declare interface DaemonServerInfo {
  /** The status of the daemon server. */
  readonly status: DaemonServerStatus;
  /** The version of the daemon server software. */
  readonly version: string;
}

/**
 * Semantic state of the device health.
 * Frontend uses this to determine icon and color.
 */
export declare interface DeviceHealthState {
  /** The semantic state used by FE to determine styling (icon/color). */
  readonly health: HealthState;
  /** The display text for the state, e.g., "In Service", "Out of Service". */
  readonly title: string;
  /** Tooltip text explaining the current health state. */
  readonly tooltip: string;
}

/**
 * A summary of a device connected to the host, for display in the device list.
 * Corresponds to DeviceSummary in host_resources.proto.
 */
export declare interface DeviceSummary {
  readonly id: string;
  readonly healthState: DeviceHealthState;
  readonly types: Array<{
    /** The type string. */
    type: string;
    /**
     * Backend-determined flag indicating if this type suggests an abnormal
     * or unhealthy state (e.g., FailedAndroidDevice, DisconnectedDevice)
     * and should be highlighted visually.
     */
    isAbnormal: boolean;
  }>;
  readonly deviceStatus: {
    /** The status string, e.g., IDLE, BUSY, MISSING, FAILED, INIT, DIRTY. */
    status: string;
    /**
     * Backend-determined flag indicating if this raw status is considered
     * critical and should be highlighted visually (e.g., red chip).
     */
    isCritical: boolean;
  };
  readonly label: string;
  // TODO: Consider if we need to display more complex form of required dimensions.
  // The type of `requiredDims` may need to update.
  readonly requiredDims: string;
  readonly model: string;
  readonly version: string;
  readonly subDevices?: SubDeviceInfo[];
  /** Optional parent device ID (e.g. for sub-devices belonging to a testbed). */
  readonly parentDeviceId?: string;
}

/**
 * Represents the comprehensive data required to render the Host Detail Page's
 * overview section. This is the top-level interface for host overview data.
 * Corresponds to HostOverview in host_resources.proto.
 */
export declare interface HostOverview {
  /** The unique name (ID) of the host. */
  readonly hostName: string;
  /** The IP address of the host. */
  readonly ip: string;
  /**
   * The user-friendly lab type name, derived by the backend,
   * e.g., "Core Lab", "Satellite Lab (SLaaS)".
   */
  readonly labTypeDisplayNames: string[];
  /** Lab server information. */
  readonly labServer: LabServerInfo;
  /** Daemon server information. */
  readonly daemonServer: DaemonServerInfo;
  /** A map of host-level properties for display. */
  readonly properties: {[key: string]: string};
  /** OS of the host machine, e.g., "gLinux", "macOS". */
  readonly os: string;
}

/**
 * Types of proxy supported by devices for remote control.
 */
export enum DeviceProxyType {
  NONE = 0,
  /**
   * Represents ADB + Video streaming capability, typically provided by ACID.
   * This type is available if 'AcidRemoteDriver' is listed in device's supportedDrivers.
   */
  ADB_AND_VIDEO = 1,
  ADB_ONLY = 2,
  USB_IP = 3,
  SSH = 4,
  VIDEO = 5,
}

/**
 * Reason code for a device being ineligible for remote control.
 */
export type IneligibilityReasonCode =
  | 'PERMISSION_DENIED' // User lacks permission for the device
  | 'DEVICE_NOT_IDLE' // Device state is not IDLE (e.g., BUSY, MISSING)
  | 'DEVICE_TYPE_NOT_SUPPORTED' // Device type is not supported (e.g., FailedDevice, AbnormalTestbedDevice, or non-AndroidRealDevice in multi-selection mode)
  | 'HOST_OS_NOT_SUPPORTED' // Host OS is MacOS
  | 'ACID_NOT_SUPPORTED'; // Device does not support AcidRemoteDriver

/**
 * The eligibility result for a sub-device.
 */
export declare interface SubDeviceEligibilityResult {
  deviceId: string;
  isEligible: boolean;
  ineligibilityReason?: {
    code: IneligibilityReasonCode;
    message: string;
  };
}

/**
 * The eligibility result for a single device.
 */
export declare interface DeviceEligibilityResult {
  deviceId: string;
  /** Whether this specific device can be remotely controlled. */
  isEligible: boolean;
  /**
   * Detailed reason if the device is ineligible.
   * Populated only if isEligible is false.
   * Used for displaying error messages in BLOCK_DEVICES_INELIGIBLE status.
   */
  ineligibilityReason?: {
    code: IneligibilityReasonCode;
    message: string;
  };
  /**
   * Supported proxy types for this device.
   * Used for displaying compatibility details in BLOCK_NO_COMMON_PROXY status.
   */
  supportedProxyTypes: DeviceProxyType[];
  /**
   * Candidates for 'Run As' for this device.
   */
  runAsCandidates?: string[];
  /**
   * If the device is a composite device (e.g. testbed), this field contains
   * eligibility details for its sub-devices.
   */
  subDeviceResults?: SubDeviceEligibilityResult[];
}

/**
 * The overall verdict dictating the frontend flow.
 */
export enum EligibilityStatus {
  /**
   * All devices are eligible and have a common proxy.
   * If the user has partial permission, the dialog will show the permission denied device card and allow
   * the user to proceed.
   * UI Action: Open configuration dialog immediately.
   * If the user has partial permission, the status should be READY and allow
   * the user to proceed, but show a warning in the dialog.
   */
  READY = 'READY',

  /**
   * One or more devices are ineligible (e.g. status is busy, missing; no acid support, etc).
   * UI Action: Show dialog listing ineligible devices and reasons.
   */
  BLOCK_DEVICES_INELIGIBLE = 'BLOCK_DEVICES_INELIGIBLE',

  /**
   * All devices are eligible individually, but for multiple devices, they share no common proxy type,
   * or for a single device, it has no supported proxy type.
   * UI Action: Show dialog listing supported proxies for each device.
   */
  BLOCK_NO_COMMON_PROXY = 'BLOCK_NO_COMMON_PROXY',

  /**
   * User has no permission for any of the selected devices.
   * UI Action: Show global permission denied error.
   */
  BLOCK_ALL_PERMISSION_DENIED = 'BLOCK_ALL_PERMISSION_DENIED',
}

/**
 * Options for creating a remote control session, applicable only if
 * status is 'READY'.
 */
export declare interface SessionOptions {
  /**
   * List of proxy types supported by ALL ELIGIBLE devices.
   */
  commonProxyTypes: DeviceProxyType[];
  /**
   * List of identities that have permission to access ALL ELIGIBLE devices.
   */
  commonRunAsCandidates: string[];
  /**
   * Maximum allowed session duration in hours.
   * If the dimension pool value is shared then the value is 3 hours else 12 hours.
   */
  maxDurationHours: number;
}

/**
 * Response structure for checkRemoteControlEligibility API.
 */
export declare interface CheckRemoteControlEligibilityResponse {
  /**
   * The overall verdict dictating the frontend flow.
   */
  status: EligibilityStatus;

  /**
   * Consolidated list of results for ALL requested devices.
   * Always populated.
   */
  results: DeviceEligibilityResult[];

  /**
   * Session options derived from the intersection of all ELIGIBLE devices.
   * Present only if status is 'READY'.
   */
  sessionOptions?: SessionOptions;
}

/**
 * Flash options for remote control.
 */
export declare interface FlashOptions {
  branch: string;
  buildId: string;
  target: string;
  subDeviceIds?: string[];
}

/**
 * Configuration for controlling a single device within a batch request.
 */
export declare interface DeviceRemoteControlConfig {
  deviceId: string;
  runAs: string;
  parentDeviceId?: string;
}

/**
 * Request for RemoteControlDevices API for multiple devices.
 */
export declare interface RemoteControlDevicesRequest {
  deviceConfigs: DeviceRemoteControlConfig[];
  durationSeconds: number;
  proxyType: DeviceProxyType;
  videoResolution?: 'DEFAULT' | 'HIGH' | 'LOW';
  maxVideoSize?: 'DEFAULT' | '1024';
  flashOptions?: FlashOptions;
}

/**
 * Response for RemoteControlDevices API.
 */
export declare interface RemoteControlDevicesResponse {
  sessions: Array<{deviceId: string; sessionUrl: string}>;
}

