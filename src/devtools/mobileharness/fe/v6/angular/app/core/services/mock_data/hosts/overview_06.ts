import {DeviceSummary, HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {
  createDefaultHostOverview,
  createDefaultUiStatus,
  createHostActions,
} from './ui_status_utils';

const overview: HostOverview = {
  ...createDefaultHostOverview('host-f-6.example.com'),
  hostName: 'host-f-6.example.com',
  ip: '192.168.5.105',
  os: 'gLinux',
  canUpgrade: false,
  uiLabTypes: ['SATELLITE'],
  labServer: {
    connectivity: {
      state: 'RUNNING',
      title: 'Running',
      tooltip:
        'Host is running and connected. OmniLab is receiving heartbeats.',
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
    labServerReleaseStatus: {
      state: 'LAB_SERVER_RELEASE_STATE_ERROR' as const,
      title: 'Error',
      tooltip:
        'The release system encountered an error attempting to manage the Lab Server process on this host.',
    },
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
const OVERVIEW_06_DATA: MockHostScenario = {
  hostName: 'host-f-6.example.com',
  scenarioName: 'Overview 6: Online (Server Error)',
  overview,
  deviceSummaries,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
  actions: createHostActions('ERROR', false),
};

/**
 * Returns the mock scenario for 06.
 */
export function OVERVIEW_06(callCount?: number): MockHostScenario {
  return OVERVIEW_06_DATA;
}
