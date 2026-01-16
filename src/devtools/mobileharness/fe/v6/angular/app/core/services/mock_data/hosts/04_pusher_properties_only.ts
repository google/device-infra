/** @fileoverview Mock host scenario with hostProperties managed by Config Pusher. */

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
  hostProperties: [
    {key: 'pool', value: 'managed'},
    {key: 'location', value: 'svl'},
  ],
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
UI_STATUS.hostProperties.sectionStatus = createPartStatus(
  true,
  false,
  'Managed by Config Pusher',
);

export const SCENARIO_HOST_PUSHER_PROPERTIES: MockHostScenario = {
  hostName: 'host-pusher-properties.example.com',
  scenarioName: '4. Pusher - Properties Only',
  overview: createDefaultHostOverview('host-pusher-properties.example.com'),
  deviceSummaries: [],
  hostConfigResult: {
    hostConfig: HOST_CONFIG,
    uiStatus: UI_STATUS,
  },
  defaultDeviceConfig: DEFAULT_DEVICE_CONFIG,
};
