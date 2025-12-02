/**
 * @fileoverview Mock data for a busy, non-flashable Android device scenario.
 * "Remote Control" and "Flash" should be hidden.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'android-busy-no-flash-01';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'android-host.prod.example.com', ip: '192.168.1.103'},
  healthAndActivity: {
    title: 'In Service (Busy)',
    subtitle: 'The device is healthy and currently running a task.',
    state: 'IN_SERVICE_BUSY',
    deviceStatus: {status: 'BUSY', isCritical: false},
    deviceTypes: [{type: 'AndroidRealDevice', isAbnormal: false}], // Not flashable type
    lastInServiceTime: new Date().toISOString(),
    currentTask: {
      type: 'Test',
      taskId: 'some-task-id',
    },
  },
  basicInfo: {
    model: 'Pixel 7',
    version: '13',
    form: 'physical',
    os: 'Android',
    batteryLevel: 75,
    network: {hasInternet: true},
  },
  permissions: {
    owners: ['user-b'],
    executors: [],
  },
  capabilities: {
    supportedDrivers: ['AndroidInstrumentation'],
    supportedDecorators: [
      'AndroidScreenshotDecorator',
      'AndroidLogCatDecorator',
    ],
  },
  dimensions: {
    supported: {},
    required: {},
  },
  properties: {},
};

const CONFIG: DeviceConfig = {
  permissions: {
    owners: ['user-b'],
    executors: [],
  },
  wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
  dimensions: {supported: [], required: []},
  settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
};

/** Mock data for a busy, non-flashable Android device scenario. */
export const SCENARIO_ANDROID_BUSY_NO_FLASH: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: '18. Android Device (Busy, No Flash)',
  overview: OVERVIEW,
  config: CONFIG,
  isQuarantined: false,
  actionVisibility: {
    screenshot: true,
    logcat: true,
    flash: false,
    remoteControl: false,
    quarantine: true,
  },
};
