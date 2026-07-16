/** @fileoverview Mock host scenario in SHARED device config mode. */

import {DeviceConfig} from '../../../models/device_config_models';
import {PreflightLabServerReleaseResponse} from '../../../models/host_action';
import {HostConfig} from '../../../models/host_config_models';
import {MockHostScenario} from '../models';
import {
  createDefaultHostOverview,
  createDefaultUiStatus,
  createHostActions,
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

const PREFLIGHT_RESPONSE: PreflightLabServerReleaseResponse = {
  ready: {
    versions: [
      {
        name: '[RELEASE] 4.358.0 mobileharness_lab_server',
        version: '4.358.0',
        status: 'LATEST_AND_CURRENT',
        buildTime: new Date(Date.now() - 3600000 * 24).toISOString(),
      },
      {
        name: '[RELEASE] 4.357.0 mobileharness_lab_server',
        version: '4.357.0',
        status: '',
        buildTime: new Date(Date.now() - 3600000 * 48).toISOString(),
      },
    ],
  },
};

export const SCENARIO_HOST_SHARED_MODE: MockHostScenario = {
  hostName: 'host-shared-mode.example.com',
  scenarioName: '3. Shared Mode',
  overview: {
    ...createDefaultHostOverview('host-shared-mode.example.com'),
    uiLabTypes: ['FUSION'],
  },
  deviceSummaries: [],
  hostConfigResult: {
    hostConfig: HOST_CONFIG,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null, // No default in SHARED mode
  actions: createHostActions('RUNNING', true, true),
  releaseResponse: PREFLIGHT_RESPONSE,
  troubleshootScriptsResponse: {
    actions: [
      {
        script: 'RESET_USB_HUB' as const,
        displayName: 'Reset USB Hub',
        description:
          'Power cycle smart USB hub ports to recover missing devices.',
        enabled: false,
        constraintTooltip:
          'USB hub reset is disabled while critical shared mode tasks are operating.',
      },
    ],
  },
};
