import {DeviceSummary, HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {createDefaultUiStatus} from './ui_status_utils';

const overview: HostOverview = {
  hostName: 'host-i-9.example.com',
  ip: '192.168.8.108',
  os: 'gLinux',
  labTypeDisplayNames: ['Satellite Lab'],
  labServer: {
    connectivity: {
      state: 'RUNNING',
      title: 'Running',
      tooltip:
        'Host is running and connected. OmniLab is receiving heartbeats.',
    },
    activity: {
      state: 'DRAINED',
      title: 'Drained',
      tooltip:
        'The Lab Server has finished all tasks and is not accepting new ones.',
    },
    version: 'R123.45.9',
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
  properties: {},
};

const deviceSummaries: DeviceSummary[] = [
  {
    id: '43021FDAQ000UM',
    healthState: {
      health: 'IN_SERVICE_IDLE',
      title: 'In Service (Idle)',
      tooltip: 'Device is healthy and ready for tasks.',
    },
    types: [
      {type: 'AndroidDevice', isAbnormal: false},
      {type: 'AndroidFlashableDevice', isAbnormal: false},
      {type: 'AndroidOnlineDevice', isAbnormal: false},
      {type: 'AndroidRealDevice', isAbnormal: false},
    ],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'golden-pixel',
    requiredDims: 'pool:prod',
    model: 'Pixel 8 Pro',
    version: '14',
  },
];

/** Mock host overview data. */
export const OVERVIEW_09: MockHostScenario = {
  hostName: 'host-i-9.example.com',
  scenarioName: 'Overview 9: Online (Server Drained)',
  overview,
  deviceSummaries,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
};
