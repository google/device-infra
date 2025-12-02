/**
 * @fileoverview Mock data for the "In Service, BUSY, Linux" device scenario.
 * This scenario represents a healthy Linux device that is currently running a
 * test.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'LNX-STABLE-A4B1C2';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-b-2.prod.example.com', ip: '192.168.2.202'},
  healthAndActivity: {
    title: 'In Service (Busy)',
    subtitle: 'The device is healthy and currently running a task.',
    state: 'IN_SERVICE_BUSY',
    deviceStatus: {status: 'BUSY', isCritical: false},
    deviceTypes: [{type: 'LinuxDevice', isAbnormal: false}],
    lastInServiceTime: new Date().toISOString(),
    currentTask: {
      type: 'Test',
      taskId: 'a1b2c3d4-e5f6-4008-adf9-26d1a70e5f61',
      jobId: 'job-uuid-1111-2222-3333-4444',
    },
  },
  basicInfo: {
    model: 'N/A',
    version: '22.04',
    form: 'virtual',
    os: 'Ubuntu Linux',
    batteryLevel: null,
    network: {hasInternet: true},
  },
  permissions: {owners: ['user-b'], executors: ['linux-test-runner']},
  capabilities: {
    supportedDrivers: ['HostBin', 'NoOpDriver'],
    supportedDecorators: [],
  },
  dimensions: {
    supported: {
      'From Device Config': {
        dimensions: [
          {name: 'cpu-arch', value: 'x86_64'},
          {name: 'container-support', value: 'docker'},
        ],
      },
    },
    required: {},
  },
  properties: {'host-package-version': '2.1.4'},
};

const CONFIG: DeviceConfig = {
  permissions: {owners: ['user-b'], executors: ['linux-test-runner']},
  wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
  dimensions: {
    supported: [
      {name: 'cpu-arch', value: 'x86_64'},
      {name: 'container-support', value: 'docker'},
    ],
    required: [],
  },
  settings: {maxConsecutiveFail: 10, maxConsecutiveTest: 5000},
};

/**
 * Represents a mock device scenario where the device is in service and busy.
 * This scenario is used for testing and development purposes to simulate a
 * device in a specific state.
 */
export const SCENARIO_IN_SERVICE_BUSY: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: 'In Service - Busy',
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
