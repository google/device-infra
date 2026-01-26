/**
 * @fileoverview Mock data for the "Idle But Quarantined" device scenario.
 * This scenario represents a device that is IDLE but has been quarantined.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'IDLE_BUT_QUARANTINED-NO-PERM';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-q-15.prod.example.com', ip: '192.168.15.115'},
  healthAndActivity: {
    title: 'Idle But Quarantined',
    subtitle: 'The device is idle but quarantined.',
    state: 'IDLE_BUT_QUARANTINED',
    deviceStatus: {status: 'IDLE', isCritical: false},
    deviceTypes: ['AndroidDevice', 'AndroidRealDevice'].map((type) => ({
      type,
      isAbnormal: false,
    })),
    lastInServiceTime: new Date().toISOString(),
    diagnostics: {
      diagnosis: 'Device is Quarantined.',
      explanation:
        'The device is currently quarantined, making it unavailable for test allocation, despite being in an IDLE state.',
      suggestedAction:
        'Unquarantine the device to make it available for tests.',
    },
  },
  basicInfo: {
    model: 'Pixel 9',
    version: '15',
    form: 'physical',
    os: 'Android',
    batteryLevel: 88,
    network: {wifiRssi: -50, hasInternet: true},
    hardware: 'g/2445a',
    build: 'AP1A.240605.002',
  },
  permissions: {
    owners: ['user-a', 'group-infra-team'],
    executors: ['test-runner-service-account'],
  },
  capabilities: {
    supportedDrivers: ['AndroidInstrumentation'],
    supportedDecorators: ['AndroidLogCatDecorator'],
  },
  dimensions: {
    supported: {
      'From Device Config': {
        dimensions: [{name: 'pool', value: 'quarantine-test'}],
      },
    },
    required: {},
  },
  properties: {},
};

const CONFIG: DeviceConfig = {
  permissions: {
    owners: ['user-a', 'group-infra-team'],
    executors: ['test-runner-service-account'],
  },
  wifi: {
    type: 'pre-configured',
    ssid: 'GoogleGuest',
    psk: '',
    scanSsid: false,
  },
  dimensions: {
    supported: [{name: 'pool', value: 'quarantine-test'}],
    required: [],
  },
  settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
};

/**
 * Represents a mock device scenario where the device is idle but quarantined.
 */
export const SCENARIO_IDLE_BUT_QUARANTINED: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: '15. Idle But Quarantined',
  overview: OVERVIEW,
  config: CONFIG,
  isQuarantined: true,
  quarantineExpiry: new Date(Date.now() + 24 * 3600 * 1000).toISOString(),
  actionVisibility: {
    screenshot: true,
    logcat: true,
    flash: true,
    remoteControl: true,
    quarantine: true,
  },
};
