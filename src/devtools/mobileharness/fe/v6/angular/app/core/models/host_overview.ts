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
export interface HostConnectivityStatus {
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
export interface LabServerActivity {
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
export interface LabServerInfo {
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
export interface DaemonServerStatus {
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
export interface DaemonServerInfo {
  /** The status of the daemon server. */
  readonly status: DaemonServerStatus;
  /** The version of the daemon server software. */
  readonly version: string;
}

/**
 * Semantic state of the device health.
 * Frontend uses this to determine icon and color.
 */
export interface DeviceHealthState {
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
export interface DeviceSummary {
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
  readonly isACIDSupport?: boolean;
}

/**
 * Represents the comprehensive data required to render the Host Detail Page's
 * overview section. This is the top-level interface for host overview data.
 * Corresponds to HostOverview in host_resources.proto.
 */
export interface HostOverview {
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
  | 'PERMISSION_DENIED' // 用户对设备缺乏权限
  | 'DEVICE_NOT_IDLE' // 设备状态不是IDLE (例如: BUSY, MISSING)
  | 'DEVICE_TYPE_NOT_SUPPORTED' // 设备类型不支持(例如: FailedDevice, AbnormalTestbedDevice, 或在多选模式下非AndroidRealDevice)
  | 'HOST_OS_NOT_SUPPORTED' // 主机操作系统是MacOS
  | 'ACID_NOT_SUPPORTED'; // 设备不支持AcidRemoteDriver

/**
 * Represents a device that is not eligible for remote control, including the reason.
 */
export interface IneligibleDevice {
  deviceId: string;
  reasonCode: IneligibilityReasonCode;
  /**
   * User-friendly message explaining why it's ineligible,
   * e.g., "Device is busy", "Permission denied", "Remote control not supported on MacOS hosts",
   * "Device does not support ACID remote control".
   */
  message: string;
}

/**
 * Represents a device that is eligible for remote control.
 */
export interface EligibleDevice {
  deviceId: string;
  /**
   * All proxy types supported by this device.
   * If the device is eligible, this list will include at least ADB_AND_VIDEO.
   */
  supportedProxyTypes: DeviceProxyType[];
  /**
   * All users or groups ('user/ldap', 'mdb/group') who have permission for THIS device.
   */
  runAsCandidates: string[];
}

/**
 * Options for creating a remote control session, applicable only if
 * eligibleDevices list is not empty.
 */
export interface SessionOptions {
  /**
   * List of proxy types supported by ALL devices in the eligibleDevices list.
   * This list is used to populate the "Connection Type" dropdown in the dialog.
   * If ADB_AND_VIDEO is required, but not in this list, bulk session may not be possible
   * depending on UI implementation.
   */
  commonProxyTypes: DeviceProxyType[];
  /**
   * List of identities (e.g., 'qiupingf', 'mdb/team-group') that have permission
   * to access ALL devices in the eligibleDevices list.
   * This list is used to populate the 'Run As' dropdown in the dialog.
   */
  commonRunAsCandidates: string[];
  /**
   * Maximum allowed session duration in hours.
   */
  maxDurationHours: number;
}

/**
 * Response structure for checkRemoteControlEligibility API.
 */
export interface CheckRemoteControlEligibilityResponse {
  /** List of devices eligible for remote control based on the validation rules. */
  eligibleDevices: EligibleDevice[];
  /** List of devices ineligible for remote control, with reasons. */
  ineligibleDevices: IneligibleDevice[];
  /**
   * Session configuration options. This field is populated only if
   * eligibleDevices is not empty, meaning at least one device can be controlled.
   */
  sessionOptions?: SessionOptions;
}

/**
 * Flash options for remote control.
 */
export interface FlashOptions {
  branch: string;
  buildId: string;
  target: string;
}

/**
 * Configuration for controlling a single device within a batch request.
 */
export interface DeviceRemoteControlConfig {
  deviceId: string;
  runAs: string;
}

/**
 * Request for RemoteControlDevices API for multiple devices.
 */
export interface RemoteControlDevicesRequest {
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
export interface RemoteControlDevicesResponse {
  sessions: Array<{deviceId: string; sessionUrl: string}>;
}
