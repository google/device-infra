/**
 * @fileoverview Defines the data models for the Device entity, specifically for
 * the Device Detail Page. These interfaces represent the contract between the
 * backend service and the frontend components.
 *
 * The guiding principle is "heavy backend, light frontend":
 * - The backend is responsible for complex business logic, data aggregation,
 *   and determining the device's state.
 * - The frontend is responsible for presentation, using the data provided
 *   by the backend.
 */

import {DeviceHeaderInfo, HostInfo} from './device_action';

/**
 * Represents the comprehensive data required to render the "Overview" tab
 * on the Device Detail Page.
 */
export declare interface DeviceOverview {
  /** The unique identifier of the device. */
  id: string;

  /** Information about the host machine this device is connected to. */
  host: HostInfo;

  /** Detailed information about the device's current health, activity, and status. */
  healthAndActivity: HealthAndActivityInfo;

  /** Core hardware and software specifications of the device. */
  basicInfo: BasicDeviceInfo;

  /** Permissions defining who can own and execute tests on the device. */
  permissions: PermissionInfo;

  /** Lists of supported drivers and decorators for the device. */
  capabilities: CapabilitiesInfo;

  /**
   * Device dimensions used for test scheduling, grouped by their source.
   * Dimensions are key-value pairs, where a key can have multiple values.
   */
  dimensions: Dimensions;

  /**
   * Additional device properties, as a simple key-value map.
   * Unlike dimensions, each property key has only a single string value.
   */
  properties: Record<string, string>;

  /**
   * If the device is a testbed, this contains information about its sub-devices.
   */
  subDevices?: SubDeviceInfo[];
}

// --- Helper Interfaces for DeviceOverview ---

/**
 * Represents a specific type classification of a device (e.g., AndroidRealDevice).
 */
export declare interface DeviceType {
  /** The type string. */
  type: string;
  /**
   * Backend-determined flag indicating if this type suggests an abnormal
   * or unhealthy state (e.g., FailedAndroidDevice, DisconnectedDevice)
   * and should be highlighted visually.
   */
  isAbnormal: boolean;
}

/**
 * Network connectivity details for a device.
 */
export declare interface NetworkInfo {
  /** WiFi signal strength in dBm, e.g., -65. Optional. */
  wifiRssi?: number;
  /** Indicates if the device has internet access. Optional. */
  hasInternet?: boolean;
}

/**
 * Information about remote control capabilities and status.
 */
export declare interface RemoteControlInfo {
  isSupported?: boolean;
  unsupportedReason?: string;
}

/**
 * Enum-like union of strings representing the high-level health state of the device.
 * This state is determined by the backend based on various factors (status, types, tasks)
 * and is used by the frontend to drive the main visual representation in the
 * Health & Activity card (icon, colors, animations).
 */
export type HealthState =
  | 'IN_SERVICE_IDLE' // Healthy and available.
  | 'IN_SERVICE_BUSY' // Healthy and running a standard task.
  | 'OUT_OF_SERVICE_RECOVERING' // Running an automated recovery process.
  | 'OUT_OF_SERVICE_TEMP_MAINT' // Temporarily unavailable due to routine maintenance (e.g., INIT, DIRTY < 1hr).
  | 'OUT_OF_SERVICE_NEEDS_FIXING' // In an error state requiring attention.
  | 'IDLE_BUT_QUARANTINED' // Idle but quarantined.
  | 'UNKNOWN'; // Fallback for unexpected states.

/**
 * Contains all data related to the device's health, status, and current activity.
 */
export declare interface HealthAndActivityInfo {
  /**
   * The main title to display in the Health & Activity card, summarizing the state.
   * e.g., "In Service (Idle)", "Out of Service (Recovering)".
   * Determined by the backend.
   */
  title: string;

  /**
   * A concise, human-readable explanation of the device's state.
   * e.g., "The device is healthy and ready for new tasks."
   * Determined by the backend.
   */
  subtitle: string;

  /**
   * The high-level health state category. The frontend uses this to select
   * appropriate icons, color schemes, and animations.
   */
  state: HealthState;

  /** The raw device status from the underlying system. */
  deviceStatus: {
    /** The status string, e.g., IDLE, BUSY, MISSING, FAILED, INIT, DIRTY. */
    status: string;
    /**
     * Backend-determined flag indicating if this raw status is considered
     * critical and should be highlighted visually (e.g., red chip).
     */
    isCritical: boolean;
  };

  /**
   * List of device types (e.g., AndroidRealDevice, AdbDevice).
   */
  deviceTypes: DeviceType[];

  /**
   * The timestamp of when the device was last considered "In Service".
   * Stored in ISO 8601 format string (e.g., '2023-10-27T10:00:00Z').
   * Null if the device has never been in service or the time is unknown.
   */
  lastInServiceTime: string | null;

  /**
   * Details of the current task if the device is BUSY.
   */
  currentTask?: {
    /** Type of the running task, e.g., "Test", "Recovery Task". */
    type: string;
    /** The unique identifier for the task. */
    taskId: string;
    /** Optional Job ID if the task is part of a larger job. */
    jobId?: string;
  };

  /**
   * Structured diagnostic information, displayed in a collapsible section
   * when the device is in an out-of-service state.
   * This field is undefined if the device is In Service.
   */
  diagnostics?: {
    /**
     * Text describing the primary diagnosis of the issue.
     * Can contain simple, safe HTML like <strong>, <code>.
     */
    diagnosis: string;
    /**
     * Text providing more context or explanation about the diagnosis.
     * Can contain simple, safe HTML.
     */
    explanation: string;
    /**
     * Suggested steps the user can take to resolve the issue.
     * Typically present only for 'OUT_OF_SERVICE_NEEDS_FIXING' states.
     * Can contain simple, safe HTML.
     */
    suggestedAction?: string;
  };

  // Deprecated: These fields are now in DeviceHeaderInfo.
  // isQuarantined?: boolean;
  // quarantineExpiry?: string;
}

/**
 * Basic hardware, software, and real-time information about the device.
 */
export declare interface BasicDeviceInfo {
  /** Device model, e.g., "Pixel 8 Pro". */
  model: string;
  /** OS version, e.g., "14" for Android, "22.04" for Ubuntu. */
  version: string;
  /** Form factor of the device. */
  form: 'physical' | 'virtual' | 'testbed' | 'unknown';
  /** Operating system, e.g., "Android", "Ubuntu Linux". */
  os: string;
  /** Battery level percentage (0-100), or null if not applicable. */
  batteryLevel: number | null;
  /** Network connectivity details. */
  network: NetworkInfo;
  /** Hardware identifier, common for Android devices (e.g., "cheetah"). Optional. */
  hardware?: string;
  /** Build ID or version, common for Android devices. Optional. */
  build?: string;
}

/**
 * Information about user and group permissions for the device.
 */
export declare interface PermissionInfo {
  /** List of users/groups who own the device and can change its config. */
  owners: string[];
  /** List of users/groups who can execute tests on the device. */
  executors: string[];
}

/**
 * Lists of supported test drivers and decorators.
 */
export declare interface CapabilitiesInfo {
  /** Test drivers compatible with this device. */
  supportedDrivers: string[];
  /** Decorators that can be applied to tests running on this device. */
  supportedDecorators: string[];
}

/**
 * Represents one dimension of the device.
 * Corresponds to deviceinfra.fe.v6.device.DeviceDimension
 */
export declare interface DeviceDimension {
  name?: string;
  value?: string;
}

/**
 * Represents a group of dimensions from a single source.
 * Corresponds to deviceinfra.fe.v6.device.DimensionSourceGroup
 */
export declare interface DimensionSourceGroup {
  dimensions?: DeviceDimension[];
}

/**
 * Device dimensions, categorized into 'supported' and 'required'.
 * The keys of the 'supported' and 'required' objects are the source strings
 * from the proto map.
 * Corresponds to deviceinfra.fe.v6.device.Dimensions
 */
export declare interface Dimensions {
  supported?: {[key: string]: DimensionSourceGroup};
  required?: {[key: string]: DimensionSourceGroup};
}

/**
 * Information about a sub-device in a testbed.
 */
export declare interface SubDeviceInfo {
  id: string;
  types: DeviceType[];
  /** Dimensions of the sub-device. */
  dimensions?: DeviceDimension[];

  /** Model of the sub-device. */
  model?: string;
  /** Version of the sub-device. */
  version?: string;
  /** Battery level percentage (0-100), or null if not applicable. */
  batteryLevel?: number | null;
  /** Network connectivity details. */
  network?: NetworkInfo;
  /** Remote control support details. */
  remoteControl?: RemoteControlInfo;
}

/**
 * Testbed configuration in YAML format.
 */
export declare interface TestbedConfig {
  yamlContent: string;
  codeSearchLink: string;
}

/**
 * Data for device overview page.
 */
export declare interface DeviceOverviewPageData {
  headerInfo: DeviceHeaderInfo;
  overview: DeviceOverview;
}

