/**
 * @fileoverview Mock data for the "Out of Service, INIT <1hr (Warning)" device
 * scenario. This represents a device that is temporarily unavailable due to
 * routine maintenance.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'ANDROID-INIT-XYZ';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-i-9.prod.example.com', ip: '192.168.9.109'},
  healthAndActivity: {
    title: 'Out of Service (Temporary Maintenance)',
    subtitle:
      'The device is temporarily unavailable due to routine maintenance.',
    state: 'OUT_OF_SERVICE_TEMP_MAINT',
    deviceStatus: {status: 'INIT', isCritical: true},
    deviceTypes: [{type: 'AndroidDevice', isAbnormal: false}],
    lastInServiceTime: new Date(Date.now() - 25 * 60 * 1000).toISOString(),
    diagnostics: {
      diagnosis:
        'Device is in a temporary maintenance state (<strong>INIT</strong>).',
      explanation:
        'This is usually part of a routine process like initialization or cleanup. The device is expected to become available shortly.',
    },
  },
  basicInfo: {
    model: 'Pixel Fold',
    version: '14',
    form: 'physical',
    os: 'Android',
    batteryLevel: 55,
    network: {wifiRssi: -65, hasInternet: false},
    hardware: 'g/23xyz',
    build: 'AP1A.240405.002',
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
 * initializing. This scenario is used for testing and development purposes to
 * simulate a device in a specific state.
 */
export const SCENARIO_OUT_OF_SERVICE_INIT: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: 'Out of Service - Initializing',
  overview: OVERVIEW,
  config: CONFIG,
};
