/** @fileoverview Mock host scenario with specific hostProperties items managed by Config Pusher. */

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
  hostProperties: [
    {key: 'pool', value: 'managed'}, // Index 0
    {key: 'custom', value: 'editable'}, // Index 1
    {key: 'location', value: 'svl'}, // Index 2
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
UI_STATUS.hostProperties.itemEditabilityOverrides = {
  0: {editable: false, reason: 'Managed by Config Pusher'},
  2: {editable: false, reason: 'Managed by Config Pusher'},
};

export const SCENARIO_HOST_PUSHER_ITEM_OVERRIDE: MockHostScenario = {
  hostName: 'host-pusher-item-override.example.com',
  scenarioName: '5. Pusher - Properties Item Override',
  hostConfigResult: {
    hostConfig: HOST_CONFIG,
    uiStatus: UI_STATUS,
  },
  defaultDeviceConfig: DEFAULT_DEVICE_CONFIG,
};
