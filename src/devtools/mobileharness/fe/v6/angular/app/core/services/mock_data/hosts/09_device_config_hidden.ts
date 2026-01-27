/** @fileoverview Mock host scenario with deviceConfig section hidden. */

import {HostConfig} from '../../../models/host_config_models';
import {MockHostScenario} from '../models';
import {
  createDefaultHostOverview,
  createDefaultUiStatus,
  createPartStatus,
} from './ui_status_utils';

const HOST_CONFIG: HostConfig = {
  permissions: {
    hostAdmins: ['admin1', 'derekchen'],
    sshAccess: [],
  },
  deviceConfigMode: 'PER_DEVICE',
  // deviceConfig is undefined
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
UI_STATUS.deviceConfig = {
  sectionStatus: createPartStatus(false),
  subSections: {},
};
UI_STATUS.deviceConfigMode = createPartStatus(false);

export const SCENARIO_HOST_DEVICE_CONFIG_HIDDEN: MockHostScenario = {
  hostName: 'host-device-config-hidden.example.com',
  scenarioName: '9. Device Config Hidden',
  overview: createDefaultHostOverview('host-device-config-hidden.example.com'),
  deviceSummaries: [],
  hostConfigResult: {
    hostConfig: HOST_CONFIG,
    uiStatus: UI_STATUS,
  },
  defaultDeviceConfig: null,
};
