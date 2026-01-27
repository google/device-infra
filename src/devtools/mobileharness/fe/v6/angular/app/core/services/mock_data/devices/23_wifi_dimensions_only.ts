/**
 * @fileoverview Mock data for a device scenario where only Wifi and Dimensions are visible.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'WIFI_DIMENSIONS_ONLY_DEVICE';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-restricted.prod.example.com', ip: '192.168.1.102'},
  healthAndActivity: {
    title: 'Restricted Config Device',
    subtitle: 'This device has restricted configuration options.',
    state: 'IN_SERVICE_IDLE',
    deviceStatus: {status: 'IDLE', isCritical: false},
    deviceTypes: [{type: 'AndroidRealDevice', isAbnormal: false}],
    lastInServiceTime: new Date().toISOString(),
  },
  basicInfo: {
    model: 'Restricted Pixel',
    version: '14',
    form: 'physical',
    os: 'Android',
    batteryLevel: 100,
    network: {wifiRssi: -50, hasInternet: true},
    hardware: 'restricted-hw',
    build: 'RESTRICTED.001',
  },
  permissions: {
    owners: ['admin'],
    executors: ['admin'],
  },
  capabilities: {
    supportedDrivers: ['AndroidInstrumentation'],
    supportedDecorators: ['AndroidScreenshotDecorator'],
  },
  dimensions: {
    supported: {
      'From Device Config': {
        dimensions: [{name: 'pool', value: 'restricted-pool'}],
      },
    },
    required: {
      'From Device Config': {
        dimensions: [{name: 'type', value: 'restricted'}],
      },
    },
  },
  properties: {},
};

const CONFIG: DeviceConfig = {
  permissions: {
    owners: ['admin'],
    executors: ['admin'],
  },
  wifi: {
    type: 'pre-configured',
    ssid: 'RestrictedWiFi',
    psk: '',
    scanSsid: true,
  },
  dimensions: {
    supported: [{name: 'pool', value: 'restricted-pool'}],
    required: [{name: 'type', value: 'restricted'}],
  },
  settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
};

/**
 * A mock device scenario where only Wifi and Dimensions configuration sections are visible.
 */
export const SCENARIO_WIFI_DIMENSIONS_ONLY: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: 'WiFi & Dimensions Only',
  overview: OVERVIEW,
  config: CONFIG,
  isQuarantined: false,
  actionVisibility: {
    screenshot: true,
    logcat: true,
    flash: true,
    remoteControl: true,
    quarantine: true,
  },
};
