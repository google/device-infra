/** @fileoverview Mock host scenario where device config only shows Wifi and Dimensions. */

import {DeviceConfig} from '../../../models/device_config_models';
import {HostConfig} from '../../../models/host_config_models';
import {MockHostScenario} from '../models';
import {
  createDefaultHostOverview,
  createDefaultUiStatus,
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
  hostProperties: [{key: 'pool', value: 'test'}],
  deviceDiscovery: {
    monitoredDeviceUuids: [],
    testbedUuids: [],
    miscDeviceUuids: [],
    overTcpIps: [],
    overSshDevices: [],
    manekiSpecs: [],
  },
};

const uiStatus = createDefaultUiStatus();
uiStatus.hostAdmins = {visible: false};
uiStatus.sshAccess = {visible: false};
uiStatus.deviceDiscovery = {visible: false};
uiStatus.hostProperties = {sectionStatus: {visible: false}};
uiStatus.deviceConfig = {
  sectionStatus: {
    visible: true,
    editability: {editable: true},
  },
  subSections: {
    permissions: {visible: false},
    wifi: {visible: true, editability: {editable: true}},
    dimensions: {visible: true, editability: {editable: true}},
    settings: {visible: false},
  },
};

/**
 * A mock host scenario where only Wifi and Dimensions configuration sections are visible in the device config section.
 */
export const SCENARIO_HOST_DEVICE_CONFIG_WIFI_DIMENSIONS_ONLY: MockHostScenario =
  {
    hostName: 'host-wifi-dims-only.example.com',
    scenarioName: '10. Device Config Wifi & Dims Only',
    overview: createDefaultHostOverview('host-wifi-dims-only.example.com'),
    deviceSummaries: [],
    hostConfigResult: {
      hostConfig: HOST_CONFIG,
      uiStatus,
    },
    defaultDeviceConfig: DEFAULT_DEVICE_CONFIG,
  };
