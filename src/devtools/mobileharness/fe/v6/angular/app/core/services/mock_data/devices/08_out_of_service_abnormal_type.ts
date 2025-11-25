/**
 * @fileoverview Mock data for the "Out of Service, Abnormal Type (Critical)"
 * device scenario. This represents a device that has entered a non-standard
 * state (e.g., fastboot mode) and cannot run tests.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'FAIL-MDEV-456-XYZ';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-h-8.prod.example.com', ip: '192.168.8.108'},
  healthAndActivity: {
    title: 'Out of Service (Needs Fixing)',
    subtitle: 'The device is in an error state and requires attention.',
    state: 'OUT_OF_SERVICE_NEEDS_FIXING',
    deviceStatus: {status: 'PREPPING', isCritical: true},
    deviceTypes: [
      {type: 'AndroidDevice', isAbnormal: false},
      {type: 'FailedAndroidDevice', isAbnormal: true},
      {type: 'AbnormalAndroidFlashableDevice', isAbnormal: true},
    ],
    lastInServiceTime: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
    diagnostics: {
      diagnosis:
        'The device has abnormal types: <code class="text-sm">FailedAndroidDevice, AbnormalAndroidFlashableDevice</code>.',
      explanation:
        'These types indicate the device is in a non-standard state (e.g., fastboot mode, offline) and cannot run tests.',
      suggestedAction:
        'Try rebooting the device. If the abnormal types persist, consider quarantining the device for manual debugging or re-imaging.',
    },
  },
  basicInfo: {
    model: 'Pixel 7 Pro',
    version: '14',
    form: 'physical',
    os: 'Android',
    batteryLevel: 10,
    network: {wifiRssi: -82, hasInternet: true},
    hardware: 'g/22abc',
    build: 'AP1A.240405.002.B1',
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
 * an abnormal type. This scenario is used for testing and development purposes
 * to simulate a device in a specific state.
 */
export const SCENARIO_OUT_OF_SERVICE_ABNORMAL_TYPE: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: 'Out of Service - Abnormal Type',
  overview: OVERVIEW,
  config: CONFIG,
  isQuarantined: false,
};
