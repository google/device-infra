/** @fileoverview Mock host scenario with only permissions, wifi, and stability visible in device config. */

import {DeviceConfig} from '../../../models/device_config_models';
import {
  HostConfig,
  HostConfigUiStatus,
} from '../../../models/host_config_models';
import {MockHostScenario} from '../models';
import {
  createDefaultHostOverview,
  createHostActions,
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
    hostAdmins: ['admin1'],
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

const VISIBLE_EDITABLE = createPartStatus(true, true);
const HIDDEN = createPartStatus(false);

const UI_STATUS: HostConfigUiStatus = {
  hostAdmins: {...HIDDEN},
  deviceConfigMode: {...HIDDEN},
  deviceConfig: {
    sectionStatus: {...VISIBLE_EDITABLE},
    subSections: {
      permissions: {...VISIBLE_EDITABLE},
      wifi: {...VISIBLE_EDITABLE},
      settings: {...VISIBLE_EDITABLE},
      dimensions: {...HIDDEN},
    },
  },
  hostProperties: {
    sectionStatus: {...HIDDEN},
  },
  deviceDiscovery: {...HIDDEN},
};

/** Mock scenario for host with only permissions, wifi, and stability visible in device config. */
export const SCENARIO_HOST_PERMISSIONS_WIFI_STA: MockHostScenario = {
  hostName: 'host-permissions-wifi-sta.example.com',
  scenarioName: '13. Permissions, Wifi, Sta Only',
  overview: createDefaultHostOverview('host-permissions-wifi-sta.example.com'),
  deviceSummaries: [],
  hostConfigResult: {
    hostConfig: HOST_CONFIG,
    uiStatus: UI_STATUS,
  },
  defaultDeviceConfig: DEFAULT_DEVICE_CONFIG,
  actions: createHostActions(),
};
