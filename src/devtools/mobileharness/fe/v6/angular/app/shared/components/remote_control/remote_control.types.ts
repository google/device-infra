import {SubDeviceInfo} from '../../../core/models/device_overview';
import {
  DeviceEligibilityResult,
  SessionOptions,
  SubDeviceEligibilityResult,
} from '../../../core/models/host_overview';

/** Labels for DeviceProxyType enum values. */
export const PROXY_TYPE_LABELS: Record<number, string> = {
  0: 'Auto (Default)',
  1: 'ADB & Video',
  2: 'ADB Console',
  3: 'USB-over-IP',
  4: 'SSH',
  5: 'Video Only',
};

/** Duration presets in minutes. */
export const DURATION_CHIPS = [60, 120, 240, 480, 720];

/** Short duration presets in minutes for devices with low max duration. */
export const DURATION_CHIPS_SHORT = [15, 30, 45, 60, 120, 180];

/** Data structure for device list items in the remote control dialog. */
export interface RemoteControlDeviceInfo {
  /** The unique identifier of the device. */
  id: string;
  /** The model name of the device. */
  model: string | undefined;
  /** Whether the device is a testbed. */
  isTestbed: boolean;
  /** Optional sub-devices if this is a parent device (e.g. Testbed). */
  subDevices: SubDeviceInfo[] | undefined;
}

/** Data required to initialize the RemoteControlDialog. */
export interface RemoteControlDialogData {
  /** The list of devices to configure for remote control. */
  devices: RemoteControlDeviceInfo[];
  /** The results of the eligibility check for each device. */
  eligibilityResults: DeviceEligibilityResult[];
  /** Session configuration options common to all selected devices. */
  sessionOptions: SessionOptions;
}

/** Internal state representation for a device in the dialog's list. */
export interface DeviceListItem {
  /** Summary information about the device. */
  summary: RemoteControlDeviceInfo;
  /** The individual eligibility result for this device. */
  eligibility: DeviceEligibilityResult;
  /** List of account identities valid for triggering a session. */
  validIdentities: string[];
  /** Whether the current user has access to initiate a session. */
  hasAccess: boolean;
  /** Eligibility results for sub-devices if any. */
  subDevices: SubDeviceEligibilityResult[];
  /** Count of sub-devices that are ready/eligible. */
  readySubDeviceCount: number;
}
