/** @fileoverview Mock host scenario with deviceDiscovery section hidden. */

import {DeviceConfig} from '../../../models/device_config_models';
import {HostConfig} from '../../../models/host_config_models';
import {MockHostScenario} from '../models';
import {createDefaultUiStatus, createPartStatus} from './ui_status_utils';

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
UI_STATUS.deviceDiscovery = createPartStatus(false);

export const SCENARIO_HOST_DISCOVERY_HIDDEN: MockHostScenario = {
  hostName: 'host-discovery-hidden.example.com',
  scenarioName: '8. Device Discovery Hidden',
  hostConfigResult: {
    hostConfig: HOST_CONFIG,
    uiStatus: UI_STATUS,
  },
  defaultDeviceConfig: DEFAULT_DEVICE_CONFIG,
};
