import {InjectionToken} from '@angular/core';

/**
 * Enum representing all unimplemented features that will show a "Coming Soon" dialog.
 */
export enum ActionBarAction {
  // Host Actions
  HOST_CONFIGURATION = 'HOST_CONFIGURATION',
  HOST_DEBUG = 'HOST_DEBUG',
  HOST_START = 'HOST_START',
  HOST_RESTART = 'HOST_RESTART',
  HOST_STOP = 'HOST_STOP',
  HOST_DECOMMISSION = 'HOST_DECOMMISSION',

  HOST_RELEASE = 'HOST_RELEASE',

  // Device Actions
  DEVICE_CONFIGURATION = 'DEVICE_CONFIGURATION',
  DEVICE_SCREENSHOT = 'DEVICE_SCREENSHOT',
  DEVICE_REMOTE_CONTROL = 'DEVICE_REMOTE_CONTROL',
  DEVICE_FLASH = 'DEVICE_FLASH',
  DEVICE_LOGCAT = 'DEVICE_LOGCAT',
  DEVICE_QUARANTINE = 'DEVICE_QUARANTINE',
  DEVICE_DECOMMISSION = 'DEVICE_DECOMMISSION',
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
