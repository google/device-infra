/**
 * @fileoverview Mock data for the "Out of Service, DIRTY >1hr (Critical)"
 * device scenario. This represents a device that has been stuck in a cleanup
 * state for an extended period, indicating a potential problem.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'ANDROID-DIRTY-OLD';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-j-10.prod.example.com', ip: '192.168.10.110'},
  healthAndActivity: {
    title: 'Out of Service (Needs Fixing)',
    subtitle: 'The device is in an error state and requires attention.',
    state: 'OUT_OF_SERVICE_NEEDS_FIXING',
    deviceStatus: {status: 'DIRTY', isCritical: true},
    deviceTypes: [{type: 'AndroidDevice', isAbnormal: false}],
    lastInServiceTime: new Date(Date.now() - 90 * 60 * 1000).toISOString(),
    diagnostics: {
      diagnosis: 'The device status is <strong>DIRTY</strong>.',
      explanation: 'This means it is in a cleanup phase.',
      suggestedAction:
        'The device has been in DIRTY state for over an hour. This might indicate a problem with the cleanup process. Check device logs.',
    },
  },
  basicInfo: {
    model: 'Pixel Tablet',
    version: '14',
    form: 'physical',
    os: 'Android',
    batteryLevel: 80,
    network: {wifiRssi: -60, hasInternet: true},
    hardware: 'g/23tab',
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
 * dirty. This scenario is used for testing and development purposes to simulate
 * a device in a specific state.
 */
export const SCENARIO_OUT_OF_SERVICE_DIRTY: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: 'Out of Service - Dirty',
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
