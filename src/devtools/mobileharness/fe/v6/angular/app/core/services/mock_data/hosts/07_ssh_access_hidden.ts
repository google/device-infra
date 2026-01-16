/** @fileoverview Mock host scenario with sshAccess section hidden. */

import {DeviceConfig} from '../../../models/device_config_models';
import {HostConfig} from '../../../models/host_config_models';
import {MockHostScenario} from '../models';
import {
  createDefaultHostOverview,
  createDefaultUiStatus,
  createPartStatus,
} from './ui_status_utils';

const DEFAULT_DEVICE_CONFIG: DeviceConfig = {
  permissions: {owners: ['admin1'], executors: ['user1']},
  wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
  dimensions: {supported: [], required: []},
  settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
};

const HOST_CONFIG: HostConfig = {
  permissions: {
    hostAdmins: ['admin1', 'derekchen'],
    sshAccess: [],
  },
  deviceConfigMode: 'PER_DEVICE',
  deviceConfig: DEFAULT_DEVICE_CONFIG,
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

const UI_STATUS = createDefaultUiStatus();
UI_STATUS.sshAccess = createPartStatus(false);

export const SCENARIO_HOST_SSH_HIDDEN: MockHostScenario = {
  hostName: 'host-ssh-hidden.example.com',
  scenarioName: '7. SSH Access Hidden',
  overview: createDefaultHostOverview('host-ssh-hidden.example.com'),
  deviceSummaries: [],
  hostConfigResult: {
    hostConfig: HOST_CONFIG,
    uiStatus: UI_STATUS,
  },
  defaultDeviceConfig: DEFAULT_DEVICE_CONFIG,
};
