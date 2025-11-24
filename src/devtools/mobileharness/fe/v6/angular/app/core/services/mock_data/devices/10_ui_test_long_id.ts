/**
 * @fileoverview Mock data for the "UI Test, Long ID (Critical)" device
 * scenario. This is used to test how the UI handles extremely long device and
 * host names, ensuring proper truncation and layout stability.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID =
  'VERY-LONG-DEVICE-ID-ANDROID-0123456789-ABCDEF-XYZ-NEEDS-TRUNCATION-TO-SHOW-PROPERLY';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {
    name: 'very-long-host-name-that-also-needs-truncation.example.com',
    ip: '192.168.12.112',
  },
  healthAndActivity: {
    title: 'Out of Service (Needs Fixing)',
    subtitle: 'The device is in an error state and requires attention.',
    state: 'OUT_OF_SERVICE_NEEDS_FIXING',
    deviceStatus: {status: 'FAILED', isCritical: true},
    deviceTypes: [{type: 'AndroidDevice', isAbnormal: false}],
    lastInServiceTime: '2025-07-24T12:30:00.000Z',
    isQuarantined: false,
    quarantineExpiry: '',
    diagnostics: {
      diagnosis: 'The device status is <strong>FAILED</strong>.',
      explanation:
        'This means it failed to prepare for a task and could not be automatically recovered.',
      suggestedAction: 'Check device logs for errors.',
    },
  },
  basicInfo: {
    model: 'Pixel 6',
    version: '13',
    form: 'physical',
    os: 'Android',
    batteryLevel: 0,
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
 * Represents a mock device scenario with a long ID for UI testing. This
 * scenario is used for testing and development purposes to simulate a device
 * with a specific state.
 */
export const SCENARIO_UI_TEST_LONG_ID: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: '10. UI Test, Long ID (Critical)',
  overview: OVERVIEW,
  config: CONFIG,
};
