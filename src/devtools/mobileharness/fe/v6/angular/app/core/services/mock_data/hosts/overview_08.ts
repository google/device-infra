import {HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {createDefaultUiStatus} from './ui_status_utils';

const overview: HostOverview = {
  hostName: 'host-h-8.example.com',
  ip: '192.168.7.107',
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
      state: 'DRAINING',
      title: 'Draining',
      tooltip:
        'The Lab Server is finishing its current tasks and will not accept new ones before stopping.',
    },
    version: 'R123.45.8',
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
  devices: [],
  properties: {},
};

/** Mock host overview data. */
export const OVERVIEW_08: MockHostScenario = {
  hostName: 'host-h-8.example.com',
  scenarioName: 'Overview 8: Online (Server Draining)',
  overview,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
};
