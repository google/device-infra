/**
 * @fileoverview Mock data for the "Out of Service, Recovering (Warning)" device
 * scenario. This represents a device that is running an automated recovery
 * task.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'RECOVERING-DEVICE';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-k-11.prod.example.com', ip: '192.168.11.111'},
  healthAndActivity: {
    title: 'Out of Service (Recovering)',
    subtitle: 'The device is running an automated recovery task.',
    state: 'OUT_OF_SERVICE_RECOVERING',
    deviceStatus: {status: 'BUSY', isCritical: false},
    deviceTypes: [
      {type: 'AndroidDevice', isAbnormal: false},
      {type: 'SomeAbnormalType', isAbnormal: true},
    ],
    lastInServiceTime: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
    currentTask: {
      type: 'Recovery Task',
      taskId: 'f9e8d7c6-b5a4-4008-adf9-26d1a70e5f61',
      jobId: 'job-uuid-5555-6666-7777-8888',
    },
    diagnostics: {
      diagnosis: 'Device is running a recovery task.',
      explanation:
        'An automated recovery task is running. If successful, the device will return to service automatically. No immediate action is required.',
    },
  },
  basicInfo: {
    model: 'Pixel 8',
    version: '14',
    form: 'physical',
    os: 'Android',
    batteryLevel: 40,
    network: {hasInternet: false},
    hardware: 'g/2345b',
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
 * recovering. This scenario is used for testing and development purposes to
 * simulate a device in a specific state.
 */
export const SCENARIO_OUT_OF_SERVICE_RECOVERING: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: 'Out of Service - Recovering',
  overview: OVERVIEW,
  config: CONFIG,
};
