/**
 * @fileoverview Mock data for a testbed device scenario with a single eligible sub-device.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview, TestbedConfig} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'TESTBED-SINGLE-ELIGIBLE-SUB';
const HOST_NAME = 'host-tb-single.example.com';

const CONFIG: DeviceConfig = {
  permissions: {
    owners: ['testbed-owner'],
    executors: ['testbed-runner'],
  },
  wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
  dimensions: {supported: [], required: []},
  settings: {
    maxConsecutiveFail: 5,
    maxConsecutiveTest: 10000,
  },
};

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {
    name: HOST_NAME,
    ip: '192.168.100.10',
  },
  healthAndActivity: {
    title: 'In Service (Idle)',
    subtitle: 'The device is healthy and ready for new tasks.',
    state: 'IN_SERVICE_IDLE',
    deviceStatus: {
      status: 'IDLE',
      isCritical: false,
    },
    deviceTypes: [
      {
        type: 'TestbedDevice',
        isAbnormal: false,
      },
    ],
    lastInServiceTime: '2025-09-09T03:33:47.715Z',
  },
  basicInfo: {
    model: 'Testbed',
    version: 'N/A',
    form: 'testbed',
    os: 'Android',
    batteryLevel: null,
    network: {},
  },
  permissions: CONFIG.permissions,
  capabilities: {
    supportedDrivers: ['MoblyTest'],
    supportedDecorators: [],
  },
  dimensions: {
    supported: {
      'From Device Config': {
        dimensions: [{name: 'testbed_type', value: 'single_eligible'}],
      },
    },
    required: {},
  },
  properties: {},
  subDevices: [
    {
      id: DEVICE_ID + '_sub_1',
      types: [{type: 'AndroidRealDevice', isAbnormal: false}],
      dimensions: [
        {name: 'model', value: 'Pixel 8'},
        {name: 'version', value: '14'},
      ],
    },
  ],
};

const TESTBED_CONFIG: TestbedConfig = {
  yamlContent: `- name: SingleEligibleTestbed
  devices:
    - id: '${DEVICE_ID}_sub_1'
      type: AndroidRealDevice`,
  codeSearchLink: `configs/testbed/single.yaml`,
};

/** Mock data for a testbed device with a single eligible sub-device. */
export const SCENARIO_TESTBED_SINGLE_ELIGIBLE: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: '24. Testbed Device (Single Eligible Sub-Device)',
  overview: OVERVIEW,
  config: CONFIG,
  isQuarantined: false,
  testbedConfig: TESTBED_CONFIG,
};
