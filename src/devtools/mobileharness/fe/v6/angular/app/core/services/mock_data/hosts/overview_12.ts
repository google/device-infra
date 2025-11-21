import {DeviceSummary, HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {createDefaultUiStatus} from './ui_status_utils';

const overview: HostOverview = {
  hostName: 'long-text-host.example.com',
  ip: '192.168.12.112',
  os: 'gLinux',
  labTypeDisplayName: 'Satellite Lab',
  labServer: {
    connectivity: {
      state: 'RUNNING',
      title: 'Running',
      tooltip:
        'Host is running and connected. OmniLab is receiving heartbeats.',
    },
    activity: {
      state: 'STARTED',
      title: 'Started',
      tooltip:
        'The Lab Server process was started by the release system, and OmniLab is receiving heartbeats.',
    },
    version: 'R124.0.0',
    passThroughFlags: '',
  },
  daemonServer: {
    status: {
      state: 'RUNNING',
      title: 'Running',
      tooltip: 'The Daemon Server is running.',
    },
    version: '24.08.01',
  },
  properties: {'pushed-by': 'config-pusher-service', 'user-editable': 'true'},
};

const deviceSummaries: DeviceSummary[] = [
  {
    id: 'VERY-LONG-DEVICE-ID-ANDROID-0123456789-ABCDEF-XYZ-NEEDS-TRUNCATION-TO-SHOW-PROPERLY',
    healthState: {
      health: 'IN_SERVICE_IDLE',
      title: 'In Service (Idle)',
      tooltip: 'Device is healthy and ready for tasks.',
    },
    types: [{type: 'AndroidDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label:
      'This is an extremely long device label created for the purpose of testing text truncation and tooltip functionality in the device table.',
    requiredDims:
      'pool:long_pool_name_for_testing,another_very_long_dimension_key:with_an_equally_long_value',
    model:
      'This is a very long model name that should definitely be truncated by the UI to avoid breaking the layout',
    version:
      'Android 14 QPR3 with a very long build number string attached to it for testing purposes',
  },
];

/** Mock host overview data. */
export const OVERVIEW_12: MockHostScenario = {
  hostName: 'long-text-host.example.com',
  scenarioName: 'Overview 12: Devices with Long Text Columns',
  overview,
  deviceSummaries,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
};
