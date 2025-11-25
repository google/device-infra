/**
 * @fileoverview Mock data for the "Out of Service, No Type (Critical)" device
 * scenario. This represents a device that has failed to report any type,
 * making it unusable for testing.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'UKN-TYP-F0E1D2';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-g-7.prod.example.com', ip: '192.168.7.107'},
  healthAndActivity: {
    title: 'Out of Service (Needs Fixing)',
    subtitle: 'The device is in an error state and requires attention.',
    state: 'OUT_OF_SERVICE_NEEDS_FIXING',
    deviceStatus: {status: 'IDLE', isCritical: false},
    deviceTypes: [],
    lastInServiceTime: '2025-07-23T20:00:00.000Z',
    diagnostics: {
      diagnosis: 'The device has no type detected.',
      explanation:
        'A device must have at least one type to be considered for tasks. This often happens during initial setup or after a critical error.',
      suggestedAction:
        'Ensure the device is properly flashed and has completed its initial setup. If the problem persists, quarantine the device for manual inspection.',
    },
  },
  basicInfo: {
    model: 'Unknown',
    version: 'Unknown',
    form: 'unknown',
    os: 'Unknown',
    batteryLevel: null,
    network: {},
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
 * no type. This scenario is used for testing and development purposes to
 * simulate a device in a specific state.
 */
export const SCENARIO_OUT_OF_SERVICE_NO_TYPE: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: 'Out of Service - No Type',
  overview: OVERVIEW,
  config: CONFIG,
  isQuarantined: false,
};
