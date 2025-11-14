import {DeviceSummary, HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {createDefaultUiStatus} from './ui_status_utils';

const overview: HostOverview = {
  hostName: 'host-f-6.example.com',
  ip: '192.168.5.105',
  os: 'gLinux',
  labTypeDisplayName: 'Satellite Lab (SLaaS)',
  labServer: {
    connectivity: {
      state: 'RUNNING',
      title: 'Running',
      tooltip:
        'Host is running and connected. OmniLab is receiving heartbeats.',
    },
    activity: {
      state: 'ERROR',
      title: 'Error',
      tooltip:
        'The release system encountered an error attempting to manage the Lab Server process on this host.',
    },
    version: 'R123.45.6',
    passThroughFlags: '',
  },
  daemonServer: {
    status: {
      state: 'MISSING',
      title: 'Missing',
      missingStartTime: '2025-11-04T22:30:00.000Z',
      tooltip:
        'The Daemon Server is missing. No heartbeat received since Nov 4, 2025, 10:30 PM PST.',
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
export const OVERVIEW_06: MockHostScenario = {
  hostName: 'host-f-6.example.com',
  scenarioName: 'Overview 6: Online (Server Error)',
  overview,
  deviceSummaries,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
};
