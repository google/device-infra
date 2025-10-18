/**
 * @fileoverview Mock data for a host scenario.
 * This represents a host machine that provides a default configuration for the
 * devices connected to it.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {HostConfig} from '../../../models/host_config_models';
import {MockHostScenario} from '../models';
import {createDefaultUiStatus} from './ui_status_utils';

const HOST_NAME = 'host-x.example.com';

const DEFAULT_CONFIG: DeviceConfig = {
  permissions: {
    owners: ['host-admin', 'derekchen'],
    executors: ['host-runner'],
  },
  wifi: {
    type: 'pre-configured',
    ssid: 'WL-MobileHarness',
    psk: '',
    scanSsid: false,
  },
  dimensions: {
    supported: [
      {name: 'location', value: 'SH-ABC-123'},
      {name: 'rack', value: '42'},
    ],
    required: [{name: 'power', value: 'dedicated'}],
  },
  settings: {maxConsecutiveFail: 3, maxConsecutiveTest: 200},
};

const HOST_CONFIG: HostConfig = {
  permissions: {
    hostAdmins: ['host-admin', 'derekchen'],
    sshAccess: [
      {
        loginUser: 'mobileharness',
        principals: ['mobileharness', 'derekchen'],
      },
    ],
  },
  deviceConfigMode: 'PER_DEVICE',
  deviceConfig: DEFAULT_CONFIG,
  hostProperties: [
    {key: 'lab', value: 'main'},
    {key: 'test', value: 'true'},
  ],
  deviceDiscovery: {
    monitoredDeviceUuids: ['device1'],
    testbedUuids: [],
    miscDeviceUuids: [],
    overTcpIps: [],
    overSshDevices: [],
    manekiSpecs: [],
  },
};

/**
 * Represents a mock host scenario with a predefined host name and default
 * device configuration. This scenario is used for testing and development
 * purposes to simulate a host with specific settings.
 */
export const SCENARIO_HOST_X_PROD: MockHostScenario = {
  hostName: HOST_NAME,
  scenarioName: 'X. Host X Prod',
  hostConfigResult: {
    hostConfig: HOST_CONFIG,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: DEFAULT_CONFIG,
};
