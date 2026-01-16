/** @fileoverview Mock host scenario in SHARED device config mode. */

import {DeviceConfig} from '../../../models/device_config_models';
import {HostConfig} from '../../../models/host_config_models';
import {MockHostScenario} from '../models';
import {
  createDefaultHostOverview,
  createDefaultUiStatus,
} from './ui_status_utils';

const SHARED_DEVICE_CONFIG: DeviceConfig = {
  permissions: {owners: ['admin1'], executors: ['shared-user']},
  wifi: {type: 'pre-configured', ssid: 'SharedWIFI', psk: '', scanSsid: false},
  dimensions: {supported: [{name: 'pool', value: 'shared'}], required: []},
  settings: {maxConsecutiveFail: 3, maxConsecutiveTest: 500},
};

const HOST_CONFIG: HostConfig = {
  permissions: {
    hostAdmins: ['admin1', 'derekchen'],
    sshAccess: [],
  },
  deviceConfigMode: 'SHARED',
  deviceConfig: SHARED_DEVICE_CONFIG,
  hostProperties: [],
  deviceDiscovery: {
    monitoredDeviceUuids: [],
    testbedUuids: [],
    miscDeviceUuids: [],
    overTcpIps: [],
    overSshDevices: [],
    manekiSpecs: [],
  },
};

export const SCENARIO_HOST_SHARED_MODE: MockHostScenario = {
  hostName: 'host-shared-mode.example.com',
  scenarioName: '3. Shared Mode',
  overview: createDefaultHostOverview('host-shared-mode.example.com'),
  deviceSummaries: [],
  hostConfigResult: {
    hostConfig: HOST_CONFIG,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null, // No default in SHARED mode
};
