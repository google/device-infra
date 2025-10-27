/**
 * @fileoverview Mock data for the "Out of Service, Unknown Time (Critical)"
 * device scenario. This represents a device that is in a critical state and has
 * never been in service, so its last-in-service time is unknown.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'CRITICAL-UNKNOWN-TIME';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-z-99.prod.example.com', ip: '192.168.99.199'},
  healthAndActivity: {
    title: 'Out of Service (Needs Fixing)',
    subtitle: 'The device is in an error state and requires attention.',
    state: 'OUT_OF_SERVICE_NEEDS_FIXING',
    deviceStatus: {status: 'MISSING', isCritical: true},
    deviceTypes: [{type: 'AndroidDevice', isAbnormal: false}],
    lastInServiceTime: null,
    diagnostics: {
      diagnosis: 'The device status is <strong>MISSING</strong>.',
      explanation: 'This means it has stopped sending heartbeats.',
      suggestedAction: 'Check device power and USB connection.',
    },
  },
  basicInfo: {
    model: 'Pixel 5',
    version: '12',
    form: 'physical',
    os: 'Android',
    batteryLevel: null,
    network: {},
    hardware: 'g/20xyz',
    build: 'SD1A.210817.036',
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
 * an unknown timestamp. This scenario is used for testing and development
 * purposes to simulate a device in a specific state.
 */
export const SCENARIO_OUT_OF_SERVICE_UNKNOWN_TIME: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: '11. Out of Service, Unknown Time (Critical)',
  overview: OVERVIEW,
  config: CONFIG,
};
