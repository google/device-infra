import {InjectionToken} from '@angular/core';
import {DeviceActions} from '../models/device_action';
import {HostActions, LabServerActions} from '../models/host_action';
import {JobActions} from '../models/job_overview';

/**
 * Enum representing all unimplemented features that will show a "Coming Soon" dialog.
 */
export enum ActionBarAction {
  // Host Actions
  HOST_CONFIGURATION = 'HOST_CONFIGURATION',
  HOST_DEBUG = 'HOST_DEBUG',
  HOST_DECOMMISSION = 'HOST_DECOMMISSION',
  HOST_ADVANCED_OPERATIONS = 'HOST_ADVANCED_OPERATIONS',

  HOST_RELEASE = 'HOST_RELEASE',
  HOST_START = 'HOST_START',
  HOST_RESTART = 'HOST_RESTART',
  HOST_STOP = 'HOST_STOP',

  // Device Actions
  DEVICE_CONFIGURATION = 'DEVICE_CONFIGURATION',
  DEVICE_SCREENSHOT = 'DEVICE_SCREENSHOT',
  DEVICE_REMOTE_CONTROL = 'DEVICE_REMOTE_CONTROL',
  DEVICE_FLASH = 'DEVICE_FLASH',
  DEVICE_LOGCAT = 'DEVICE_LOGCAT',
  DEVICE_QUARANTINE = 'DEVICE_QUARANTINE',
  DEVICE_DECOMMISSION = 'DEVICE_DECOMMISSION',

  // Job Actions
  JOB_KILL = 'JOB_KILL',
}

/**
 * Enum representing the context of the action bar.
 */
export type ActionContext = 'default' | 'hostDevices' | 'hostDevicesItem';

/**
 * Metadata for each Action Bar action.
 */
export interface ActionMetadata {
  readonly displayName: string;
  readonly legacyScreenshotLinks?: {
    readonly default: string;
    readonly hostDevices?: string;
    readonly hostDevicesItem?: string;
  };
}

/**
 * Centralized configuration for Action Bar items, including "Coming Soon" details.
 */
export const ACTION_BAR_CONFIG: Record<ActionBarAction, ActionMetadata> = {
  // Host Features
  [ActionBarAction.HOST_CONFIGURATION]: {
    displayName: 'Host Configuration',
  },
  [ActionBarAction.HOST_DEBUG]: {
    displayName: 'Debug',
  },

  [ActionBarAction.HOST_START]: {
    displayName: 'Start Server',
  },
  [ActionBarAction.HOST_RESTART]: {
    displayName: 'Restart Server',
  },
  [ActionBarAction.HOST_STOP]: {
    displayName: 'Stop Server',
  },
  [ActionBarAction.HOST_DECOMMISSION]: {
    displayName: 'Decommission',
  },

  [ActionBarAction.HOST_RELEASE]: {
    displayName: 'Release',
  },
  [ActionBarAction.HOST_ADVANCED_OPERATIONS]: {
    displayName: 'Advanced Operations',
  },

  // Device Features
  [ActionBarAction.DEVICE_CONFIGURATION]: {
    displayName: 'Device Configuration',
  },
  [ActionBarAction.DEVICE_REMOTE_CONTROL]: {
    displayName: 'Remote Control',
  },
  [ActionBarAction.DEVICE_DECOMMISSION]: {
    displayName: 'Decommission',
  },
  [ActionBarAction.DEVICE_SCREENSHOT]: {
    displayName: 'Screenshot',
  },
  [ActionBarAction.DEVICE_LOGCAT]: {
    displayName: 'Get Logcat',
  },
  [ActionBarAction.DEVICE_FLASH]: {
    displayName: 'Flash',
  },
  [ActionBarAction.DEVICE_QUARANTINE]: {
    displayName: 'Quarantine',
  },
  [ActionBarAction.JOB_KILL]: {
    displayName: 'Kill Job',
  },
};

/**
 * Injection token for the action bar configuration.
 */
export const ACTION_BAR_CONFIG_TOKEN = new InjectionToken<
  Record<ActionBarAction, ActionMetadata>
>('ActionBarConfig', {
  providedIn: 'root',
  factory: () => ACTION_BAR_CONFIG,
});

/**
 * UI Configuration for Device Actions.
 *
 * NOTE: This interface was enriched with the 'feature' field to support a
 * "next-level" data-driven refactoring. By mapping each action to its
 * corresponding ActionBarAction (used for the "Coming Soon" popup), we eliminate
 * the need for redundant 'featureMap' dictionaries across different components
 * (like DeviceActionBar and HostOverview). This separation of data and logic
 * was a key requirement to optimize code reuse and maintenance.
 */
export interface DeviceActionUiConfig {
  readonly label: string;
  readonly icon: string;
  readonly testIdPrefix: string;
  readonly loadingLabel?: string;
  /** The corresponding feature flag for the "Coming Soon" popup. */
  readonly feature: ActionBarAction;
}

/**
 * UI configuration metadata for device actions.
 * Maps each device action to its label, icon, test ID prefix, and corresponding feature/permission.
 * This is used to render device actions consistently across different views (ActionBar, Menus).
 */
export const DEVICE_ACTION_UI_CONFIG: Record<
  keyof DeviceActions,
  DeviceActionUiConfig
> = {
  'configuration': {
    label: 'Configuration',
    icon: 'settings',
    testIdPrefix: 'configuration',
    feature: ActionBarAction.DEVICE_CONFIGURATION,
  },
  'screenshot': {
    label: 'Screenshot',
    icon: 'screenshot',
    testIdPrefix: 'screenshot',
    loadingLabel: 'Taking...',
    feature: ActionBarAction.DEVICE_SCREENSHOT,
  },
  'remoteControl': {
    label: 'Remote Control',
    icon: 'important_devices',
    testIdPrefix: 'remoteControl',
    feature: ActionBarAction.DEVICE_REMOTE_CONTROL,
  },
  'flash': {
    label: 'Flash',
    icon: 'flash_on',
    testIdPrefix: 'flash',
    feature: ActionBarAction.DEVICE_FLASH,
  },
  'logcat': {
    label: 'Get Logcat',
    icon: 'description',
    testIdPrefix: 'logcat',
    loadingLabel: 'Fetching...',
    feature: ActionBarAction.DEVICE_LOGCAT,
  },
  'quarantine': {
    label: 'Quarantine',
    icon: 'block',
    testIdPrefix: 'quarantine',
    loadingLabel: 'Working...',
    feature: ActionBarAction.DEVICE_QUARANTINE,
  },
  'decommission': {
    label: 'Decommission',
    icon: 'delete_sweep',
    testIdPrefix: 'decommission',
    feature: ActionBarAction.DEVICE_DECOMMISSION,
  },
};

/**
 * Interface defining the UI configuration for a host or lab server action.
 * Includes properties for displaying the action button/menu item.
 */
export interface HostActionUiConfig {
  readonly label: string;
  readonly icon: string;
  readonly testIdPrefix: string;
  readonly feature: ActionBarAction;
  readonly customClass?: string;
}

/**
 * UI configuration metadata for host actions.
 * Maps each host action to its display properties.
 */
export const HOST_ACTION_UI_CONFIG: Record<
  keyof HostActions,
  HostActionUiConfig
> = {
  'configuration': {
    label: 'Configuration',
    icon: 'settings',
    testIdPrefix: 'configuration',
    feature: ActionBarAction.HOST_CONFIGURATION,
  },
  'decommission': {
    label: 'Decommission',
    icon: 'delete_sweep',
    testIdPrefix: 'decommission',
    feature: ActionBarAction.HOST_DECOMMISSION,
    customClass: 'decommission-btn',
  },
  'debug': {
    label: 'Debug',
    icon: 'bug_report',
    testIdPrefix: 'debug',
    feature: ActionBarAction.HOST_DEBUG,
  },
  'advancedOperations': {
    label: 'Advanced Operations',
    icon: 'build_circle',
    testIdPrefix: 'advancedOperations',
    feature: ActionBarAction.HOST_ADVANCED_OPERATIONS,
  },
};

/**
 * UI configuration metadata for lab server actions.
 * Maps each lab server action to its display properties.
 */
export const LAB_SERVER_ACTION_UI_CONFIG: Record<
  keyof LabServerActions,
  HostActionUiConfig
> = {
  'release': {
    label: 'Release',
    icon: 'upgrade',
    testIdPrefix: 'release',
    feature: ActionBarAction.HOST_RELEASE,
  },
  'restart': {
    label: 'Restart',
    icon: 'restart_alt',
    testIdPrefix: 'restart',
    feature: ActionBarAction.HOST_RESTART,
  },
  'start': {
    label: 'Start',
    icon: 'play_arrow',
    testIdPrefix: 'start',
    feature: ActionBarAction.HOST_START,
  },
  'stop': {
    label: 'Stop',
    icon: 'stop_circle',
    testIdPrefix: 'stop',
    feature: ActionBarAction.HOST_STOP,
  },
};

/**
 * Interface defining the UI configuration for a job action.
 */
export interface JobActionUiConfig {
  readonly label: string;
  readonly icon: string;
  readonly testIdPrefix: string;
  readonly feature: ActionBarAction;
  readonly loadingLabel?: string;
  readonly customClass?: string;
}

/**
 * UI configuration metadata for job actions.
 * Maps each job action to its display properties.
 */
export const JOB_ACTION_UI_CONFIG: Record<keyof JobActions, JobActionUiConfig> =
  {
    'kill': {
      label: 'Kill Job',
      icon: 'cancel',
      testIdPrefix: 'kill-job',
      feature: ActionBarAction.JOB_KILL,
      loadingLabel: 'Killing...',
      customClass: 'kill-job-btn',
    },
  };
