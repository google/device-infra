/**
 * @fileoverview Mock data for a missing Android device scenario.
 * Most actions should be disabled or hidden.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'android-missing-device-01';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'android-host.prod.example.com', ip: '192.168.1.103'},
  healthAndActivity: {
    title: 'Out of Service (Needs Fixing)',
    subtitle: 'The device is in an error state and requires attention.',
    state: 'OUT_OF_SERVICE_NEEDS_FIXING',
    deviceStatus: {status: 'MISSING', isCritical: true},
    deviceTypes: [{type: 'AndroidRealDevice', isAbnormal: true}],
    lastInServiceTime: new Date(Date.now() - 3600 * 1000).toISOString(), // 1 hour ago
    diagnostics: {
      diagnosis: 'The device status is <strong>MISSING</strong>.',
      explanation: 'This means it has stopped sending heartbeats.',
      suggestedAction:
        'Check if the device is powered on and physically connected to the host via USB. Ensure the host machine is running correctly.',
    },
  },
  basicInfo: {
    model: 'Pixel 8',
    version: '14',
    form: 'physical',
    os: 'Android',
    batteryLevel: null,
    network: {},
  },
  permissions: {
    owners: ['user-a'],
    executors: [],
  },
  capabilities: {
    supportedDrivers: [],
    supportedDecorators: [],
  },
  dimensions: {
    supported: {},
    required: {},
  },
  properties: {},
};

const CONFIG: DeviceConfig = {
  permissions: {
    owners: ['user-a'],
    executors: [],
  },
  wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
  dimensions: {supported: [], required: []},
  settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
};

/** Mock data for a missing Android device scenario. */
export const SCENARIO_ANDROID_MISSING: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: '17. Android Device (Missing)',
  overview: OVERVIEW,
  config: CONFIG,
  isQuarantined: false,
  actionVisibility: {
    screenshot: false,
    logcat: false,
    flash: true,
    remoteControl: false,
    quarantine: true,
  },
};
