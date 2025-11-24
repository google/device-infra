/**
 * @fileoverview Mock data for the "Out of Service, MISSING (Critical)" device
 * scenario. This represents a device that has stopped sending heartbeats and is
 * considered offline.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'LNX-MSNG-A9B8C7';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-f-6.prod.example.com', ip: '192.168.6.106'},
  healthAndActivity: {
    title: 'Out of Service (Needs Fixing)',
    subtitle: 'The device is in an error state and requires attention.',
    state: 'OUT_OF_SERVICE_NEEDS_FIXING',
    deviceStatus: {status: 'MISSING', isCritical: true},
    deviceTypes: [{type: 'LinuxDevice', isAbnormal: false}],
    lastInServiceTime: new Date(
      Date.now() - 30 * 24 * 60 * 60 * 1000,
    ).toISOString(),
    diagnostics: {
      diagnosis: 'The device status is <strong>MISSING</strong>.',
      explanation: 'This means it has stopped sending heartbeats.',
      suggestedAction:
        'Check if the device is powered on and physically connected to the host via USB. Ensure the host machine is running correctly.',
    },
    isQuarantined: true,
    quarantineExpiry: new Date(Date.now() + 24 * 3600 * 1000).toISOString(),
  },
  basicInfo: {
    model: 'N/A',
    version: '20.04',
    form: 'physical',
    os: 'Ubuntu Linux',
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
 * Represents a mock device scenario where the device is out of service and
 * missing. This scenario is used for testing and development purposes to
 * simulate a device in a specific state.
 */
export const SCENARIO_OUT_OF_SERVICE_MISSING: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: 'Out of Service - Missing',
  overview: OVERVIEW,
  config: CONFIG,
};
