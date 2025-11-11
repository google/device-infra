import {HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {createDefaultUiStatus} from './ui_status_utils';

const overview: HostOverview = {
  hostName: 'host-d-4.example.com',
  ip: '192.168.3.103',
  os: 'gLinux',
  labTypeDisplayName: 'Satellite Lab',
  labServer: {
    connectivity: {
      state: 'MISSING',
      title: 'Missing',
      missingStartTime: '2025-11-04T22:00:00.000Z',
      tooltip:
        'Heartbeat is missing. OmniLab has not received a heartbeat from this host since Nov 4, 2025, 10:00 PM PST.',
    },
    activity: {
      state: 'STOPPED',
      title: 'Stopped',
      tooltip:
        'The Lab Server process is reported as stopped by the release system.',
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
  },
  devices: [],
  properties: {'cpu-arch': 'ARM64', 'gpu-type': 'none', 'ram-gb': '128'},
};

/** Mock host overview data. */
export const OVERVIEW_04: MockHostScenario = {
  hostName: 'host-d-4.example.com',
  scenarioName: 'Overview 4: Offline (Server Stopped)',
  overview,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
};
