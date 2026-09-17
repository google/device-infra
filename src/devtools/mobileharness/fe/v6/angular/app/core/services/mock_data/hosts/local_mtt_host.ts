/**
 * @fileoverview Mock host scenario matching local MTT development environment.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {HostConfig} from '../../../models/host_config_models';
import {DeviceSummary} from '../../../models/host_overview';
import {MockHostScenario} from '../models';
import {
  createDefaultHostOverview,
  createDefaultReleaseResponse,
  createDefaultUiStatus,
  createHostActions,
} from './ui_status_utils';

const HOST_NAME = 'mtt-host.example.com';

const DEFAULT_DEVICE_CONFIG: DeviceConfig = {
  permissions: {owners: ['tianch'], executors: ['tianch']},
  wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
  dimensions: {supported: [], required: []},
  settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
};

const HOST_CONFIG: HostConfig = {
  permissions: {
    hostAdmins: ['tianch'],
  },
  deviceConfigMode: 'PER_DEVICE',
  deviceConfig: DEFAULT_DEVICE_CONFIG,
  hostProperties: [{key: 'env', value: 'local-mtt'}],
  deviceDiscovery: {
    monitoredDeviceUuids: [],
    testbedUuids: [],
    miscDeviceUuids: [],
    overTcpIps: [],
    overSshDevices: [],
    manekiSpecs: [],
  },
};

const DEVICE_SUMMARIES: DeviceSummary[] = [0, 1, 2, 3, 4].map((i) => ({
  id: `${HOST_NAME}:NoOpDevice-${i}`,
  healthState: {
    health: 'IN_SERVICE_IDLE',
    title: 'In Service (Idle)',
    tooltip: 'Device is healthy and ready for tasks.',
  },
  types: [{type: 'NoOpDevice', isAbnormal: false}],
  deviceStatus: {isCritical: false, status: 'IDLE'},
  label: `NoOpDevice-${i}`,
  requiredDims: '',
  model: 'NoOpDevice',
  version: '1.0',
}));

const SCENARIO_HOST_LOCAL_MTT_DATA: MockHostScenario = {
  hostName: HOST_NAME,
  scenarioName: 'Local MTT Host',
  overview: {
    ...createDefaultHostOverview(HOST_NAME),
    hostName: HOST_NAME,
    ip: '127.0.0.1',
    os: 'Linux',
    canUpgrade: false,
    uiLabTypes: ['SATELLITE'],
    labServer: {
      connectivity: {
        state: 'RUNNING',
        title: 'Running',
        tooltip: 'Host is running and connected in local MTT.',
      },
      version: 'local-mtt',
      passThroughFlags: '',
    },
    daemonServer: {
      status: {
        state: 'RUNNING',
        title: 'Running',
        tooltip: 'The Daemon Server is running.',
      },
      version: 'local-mtt',
    },
  },
  deviceSummaries: DEVICE_SUMMARIES,
  hostConfigResult: {
    hostConfig: HOST_CONFIG,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: DEFAULT_DEVICE_CONFIG,
  actions: createHostActions('RUNNING', false, true),
  releaseResponse: createDefaultReleaseResponse(),
};

/**
 * Returns the mock scenario for the local MTT host.
 */
export function scenarioHostLocalMtt(
  callCount?: number,
): MockHostScenario {
  return SCENARIO_HOST_LOCAL_MTT_DATA;
}
