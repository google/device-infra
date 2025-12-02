/**
 * @fileoverview Mock data for an Android device that does not support screenshots.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'android-no-screenshot-01';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'android-host.prod.example.com', ip: '192.168.1.103'},
  healthAndActivity: {
    title: 'In Service (Idle)',
    subtitle: 'The device is healthy and ready for new tasks.',
    state: 'IN_SERVICE_IDLE',
    deviceStatus: {status: 'IDLE', isCritical: false},
    deviceTypes: [
      {type: 'AndroidRealDevice', isAbnormal: false},
      {type: 'AndroidFlashableDevice', isAbnormal: false},
    ],
    lastInServiceTime: new Date().toISOString(),
  },
  basicInfo: {
    model: 'Pixel 6',
    version: '12',
    form: 'physical',
    os: 'Android',
    batteryLevel: 80,
    network: {hasInternet: true},
  },
  permissions: {
    owners: ['user-c'],
    executors: [],
  },
  capabilities: {
    supportedDrivers: ['AndroidInstrumentation'],
    supportedDecorators: ['AndroidLogCatDecorator'], // Missing AndroidScreenshotDecorator
  },
  dimensions: {
    supported: {},
    required: {},
  },
  properties: {},
};

const CONFIG: DeviceConfig = {
  permissions: {
    owners: ['user-c'],
    executors: [],
  },
  wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
  dimensions: {supported: [], required: []},
  settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
};

/** Mock data for an Android device that does not support screenshots. */
export const SCENARIO_ANDROID_NO_SCREENSHOT: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: '19. Android Device (No Screenshot)',
  overview: OVERVIEW,
  config: CONFIG,
  isQuarantined: false,
  actionVisibility: {
    screenshot: false,
    logcat: true,
    flash: true,
    remoteControl: true,
    quarantine: true,
  },
};
