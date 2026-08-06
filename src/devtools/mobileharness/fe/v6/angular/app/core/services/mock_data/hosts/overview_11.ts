import {DeviceSummary, HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {
  createDefaultHostOverview,
  createDefaultUiStatus,
  createHostActions,
} from './ui_status_utils';

const overview: HostOverview = {
  ...createDefaultHostOverview('pusher-host-k-11.example.com'),
  hostName: 'pusher-host-k-11.example.com',
  ip: '192.168.11.111',
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
    labServerReleaseStatus: {
      state: 'LAB_SERVER_RELEASE_STATE_RUNNING' as const,
      title: 'Running',
      tooltip:
        'The Lab Server process was started by the release system, and OmniLab is receiving heartbeats.',
    },
  },
  properties: {'pushed-by': 'config-pusher-service', 'user-editable': 'true'},
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
const OVERVIEW_11_DATA: MockHostScenario = {
  hostName: 'pusher-host-k-11.example.com',
  scenarioName: 'Overview 11: Partially Managed by Pusher',
  overview,
  deviceSummaries,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
  actions: createHostActions('RUNNING', false),
};

/**
 * Returns the mock scenario for 11.
 */
export function OVERVIEW_11(callCount?: number): MockHostScenario {
  return OVERVIEW_11_DATA;
}
