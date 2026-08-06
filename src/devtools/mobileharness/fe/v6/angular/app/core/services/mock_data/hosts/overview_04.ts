import {DeviceSummary, HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {
  createDefaultHostOverview,
  createDefaultUiStatus,
  createHostActions,
} from './ui_status_utils';

const overview: HostOverview = {
  ...createDefaultHostOverview('host-d-4.example.com'),
  hostName: 'host-d-4.example.com',
  ip: '192.168.3.103',
  os: 'gLinux',
  canUpgrade: false,

  uiLabTypes: ['SATELLITE'],
  labServer: {
    connectivity: {
      state: 'MISSING',
      title: 'Missing',
      missingStartTime: '2025-11-04T22:00:00.000Z',
      tooltip:
        'Heartbeat is missing. OmniLab has not received a heartbeat from this host since Nov 4, 2025, 10:00 PM PST.',
    },

    version: 'R122.0.5',
    passThroughFlags: '',
  },
  daemonServer: {
    status: {
      state: 'MISSING',
      title: 'Missing',
      missingStartTime: '2025-11-04T22:00:00.000Z',
      tooltip:
        'The Daemon Server is missing. No heartbeat received since Nov 4, 2025, 10:00 PM PST.',
    },
    version: '24.07.05',
    labServerReleaseStatus: {
      state: 'LAB_SERVER_RELEASE_STATE_STOPPED' as const,
      title: 'Stopped',
      tooltip:
        'The Lab Server process is reported as stopped by the release system.',
    },
  },
  properties: {'cpu-arch': 'ARM64', 'gpu-type': 'none', 'ram-gb': '128'},
};

const deviceSummaries: DeviceSummary[] = [
  {
    id: 'ARM-DEVICE-01',
    healthState: {
      health: 'OUT_OF_SERVICE_NEEDS_FIXING',
      title: 'Out of Service (Needs Fixing)',
      tooltip: 'Device needs fixing.',
    },
    types: [
      {type: 'LinuxDevice', isAbnormal: false},
      {type: 'PhysicalDevice', isAbnormal: false},
    ],
    deviceStatus: {isCritical: true, status: 'MISSING'},
    label: '',
    requiredDims: 'pool:arm',
    model: 'Ampere Altra',
    version: 'Ubuntu 22.04',
  },
];

/** Mock host overview data. */
const OVERVIEW_04_DATA: MockHostScenario = {
  hostName: 'host-d-4.example.com',
  scenarioName: 'Overview 4: Offline (Server Stopped, Decommission Enabled)',
  overview,
  deviceSummaries,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
  actions: createHostActions('MISSING', false),
};

/**
 * Returns the mock scenario for 04.
 */
export function OVERVIEW_04(callCount?: number): MockHostScenario {
  return OVERVIEW_04_DATA;
}
