/** @fileoverview Mock host scenario fully managed by Config Pusher. */

import {DeviceConfig} from '../../../models/device_config_models';
import {
  HostConfig,
  HostConfigUiStatus,
} from '../../../models/host_config_models';
import {MockHostScenario} from '../models';
import {createDefaultHostOverview, createPartStatus} from './ui_status_utils';

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
  hostProperties: [{key: 'pool', value: 'managed'}],
  deviceDiscovery: {
    monitoredDeviceUuids: [],
    testbedUuids: [],
    miscDeviceUuids: [],
    overTcpIps: [],
    overSshDevices: [],
    manekiSpecs: [],
  },
};

const REASON = 'Fully managed by Config Pusher';
const NON_EDITABLE = createPartStatus(true, false, REASON);

const UI_STATUS: HostConfigUiStatus = {
  hostAdmins: {...NON_EDITABLE},
  sshAccess: {...NON_EDITABLE},
  deviceConfigMode: {...NON_EDITABLE},
  deviceConfig: {
    sectionStatus: {...NON_EDITABLE},
    subSections: {},
  },
  hostProperties: {
    sectionStatus: {...NON_EDITABLE},
  },
  deviceDiscovery: {...NON_EDITABLE},
};

export const SCENARIO_HOST_PUSHER_ALL: MockHostScenario = {
  hostName: 'host-pusher-all.example.com',
  scenarioName: '6. Pusher - All Locked',
  overview: createDefaultHostOverview('host-pusher-all.example.com'),
  deviceSummaries: [],
  hostConfigResult: {
    hostConfig: HOST_CONFIG,
    uiStatus: UI_STATUS,
  },
  defaultDeviceConfig: DEFAULT_DEVICE_CONFIG,
};
