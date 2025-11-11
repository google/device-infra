import {HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {createDefaultUiStatus} from './ui_status_utils';

const overview: HostOverview = {
  hostName: 'core-host-c-3.example.com',
  ip: '10.0.1.50',
  os: 'gLinux',
  labTypeDisplayName: 'Core Lab',
  labServer: {
    connectivity: {
      state: 'RUNNING',
      title: 'Running',
      tooltip:
        'Host is running and connected. OmniLab is receiving heartbeats.',
    },
    activity: undefined, // release_status is null for Core Lab
    version: 'R120.10.2',
    passThroughFlags: '',
  },
  daemonServer: {
    status: {
      state: 'RUNNING',
      title: 'Running',
      tooltip: 'The Daemon Server is running.',
    },
    version: 'N/A',
  },
  devices: [],
  properties: {
    'cpu-arch': 'x86_64',
    'gpu-type': 'none',
    'ram-gb': '512',
    'storage-type': 'HDD',
  },
};

/** Mock host overview data. */
export const OVERVIEW_03: MockHostScenario = {
  hostName: 'core-host-c-3.example.com',
  scenarioName: 'Overview 3: Unconfigured Host (Core Lab)',
  overview,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
};
