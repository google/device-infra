import {
  DeviceConfig,
  DeviceConfigUiStatus,
} from '../models/device_config_models';

/**
 * Default UI status for the Device Configuration.
 */
export const DEFAULT_DEVICE_CONFIG_UI_STATUS: DeviceConfigUiStatus = {
  permissions: {visible: true, editability: {editable: false}},
  wifi: {visible: true, editability: {editable: false}},
  dimensions: {visible: true, editability: {editable: false}},
  settings: {visible: true, editability: {editable: false}},
};

/**
 * Default values for the Device Configuration.
 */
export const DEFAULT_DEVICE_CONFIG: DeviceConfig = {
  permissions: {
    owners: [],
    executors: [],
  },
  wifi: {
    type: 'none',
    ssid: 'GoogleGuest',
    psk: '',
    scanSsid: false,
  },
  dimensions: {
    supported: [],
    required: [],
  },
  settings: {
    maxConsecutiveFail: 5,
    maxConsecutiveTest: 10000,
  },
};
