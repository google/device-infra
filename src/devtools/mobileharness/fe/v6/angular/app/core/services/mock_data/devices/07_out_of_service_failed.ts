/**
 * @fileoverview Mock data for the "Out of Service, FAILED (Critical)" device
 * scenario. This represents a device that failed to prepare for a task and
 * could not be automatically recovered.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'ANDROID-FAILED-DEVICE';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-e-5.prod.example.com', ip: '192.168.5.105'},
  healthAndActivity: {
    title: 'Out of Service (Needs Fixing)',
    subtitle: 'The device is in an error state and requires attention.',
    state: 'OUT_OF_SERVICE_NEEDS_FIXING',
    deviceStatus: {status: 'FAILED', isCritical: true},
    deviceTypes: [{type: 'AndroidDevice', isAbnormal: false}],
    lastInServiceTime: '2025-07-24T12:30:00.000Z',
    diagnostics: {
      diagnosis: 'The device status is <strong>FAILED</strong>.',
      explanation:
        'This means it failed to prepare for a task and could not be automatically recovered.',
      suggestedAction:
        'Check device logs for errors that occurred during the preparation phase. The device may need to be re-imaged or manually recovered.',
    },
  },
  basicInfo: {
    model: 'Pixel 6',
    version: '13',
    form: 'physical',
    os: 'Android',
    batteryLevel: 15,
    network: {},
    hardware: 'g/21fba',
    build: 'SP1A.210812.015',
  },
  permissions: {owners: [], executors: []},
  capabilities: {supportedDrivers: [], supportedDecorators: []},
  dimensions: {supported: {}, required: {}},
  properties: {},
};

const CONFIG: DeviceConfig = {
  permissions: {owners: [], executors: []},
  wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
  dimensions: {
    supported: [],
    required: [],
  },
  settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
};

/**
 * Represents a mock device scenario where the device is out of service and has
 * failed. This scenario is used for testing and development purposes to
 * simulate a device in a specific state.
 */
export const SCENARIO_OUT_OF_SERVICE_FAILED: MockDeviceScenario = {
  id: 'gLinux-host-001-device-07',
  scenarioName: 'Out of Service - Failed',
  overview: OVERVIEW,
  config: CONFIG,
};
