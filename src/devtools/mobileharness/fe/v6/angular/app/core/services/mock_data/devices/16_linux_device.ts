/**
 * @fileoverview Mock data for a Linux device scenario where most device actions
 * are not applicable.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'linux-device--01';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'linux-host.prod.example.com', ip: '192.168.1.102'},
  healthAndActivity: {
    title: 'In Service (Idle)',
    subtitle: 'The device is healthy and ready for new tasks.',
    state: 'IN_SERVICE_IDLE',
    deviceStatus: {status: 'IDLE', isCritical: false},
    deviceTypes: [{type: 'LinuxDevice', isAbnormal: false}],
    lastInServiceTime: new Date().toISOString(),
  },
  basicInfo: {
    model: 'Generic PC',
    version: '22.04',
    form: 'physical',
    os: 'Ubuntu Linux',
    batteryLevel: null,
    network: {hasInternet: true},
  },
  permissions: {
    owners: ['user-a', 'group-linux-admins'],
    executors: ['test-runner-service-account'],
  },
  capabilities: {
    supportedDrivers: ['NoOpDriver'],
    supportedDecorators: [],
  },
  dimensions: {
    supported: {
      'From Device Config': {
        dimensions: [{name: 'pool', value: 'linux-pool'}],
      },
    },
    required: {},
  },
  properties: {},
};

const CONFIG: DeviceConfig = {
  permissions: {
    owners: ['user-a', 'group-linux-admins'],
    executors: ['test-runner-service-account'],
  },
  wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
  dimensions: {
    supported: [{name: 'pool', value: 'linux-pool'}],
    required: [],
  },
  settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
};

/** Mock data for a Linux device scenario. */
export const SCENARIO_LINUX_DEVICE: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: '16. Linux Device (Actions Hidden)',
  overview: OVERVIEW,
  config: CONFIG,
  isQuarantined: false,
  actionVisibility: {
    screenshot: false,
    logcat: false,
    flash: false,
    remoteControl: false,
    quarantine: true, // Quarantine is a generic action
  },
};
