import {DeviceSummary, HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {createDefaultUiStatus} from './ui_status_utils';

const overview: HostOverview = {
  hostName: 'host-g-7.example.com',
  ip: '192.168.6.106',
  os: 'gLinux',
  labTypeDisplayName: 'Satellite Lab',
  labServer: {
    connectivity: {
      state: 'MISSING',
      title: 'Missing',
      missingStartTime: '2025-11-04T23:30:00.000Z',
      tooltip:
        'Heartbeat is missing. OmniLab has not received a heartbeat from this host since Nov 4, 2025, 11:30 PM PST.',
    },
    activity: {
      state: 'STARTED_BUT_DISCONNECTED',
      title: 'Started (but disconnected)',
      tooltip:
        'The Lab Server process was started by the release system, but OmniLab is NOT receiving heartbeats from this host.',
    },
    version: 'R123.45.7',
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
export const OVERVIEW_07: MockHostScenario = {
  hostName: 'host-g-7.example.com',
  scenarioName: 'Overview 7: Offline (But Server Running)',
  overview,
  deviceSummaries,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
};
