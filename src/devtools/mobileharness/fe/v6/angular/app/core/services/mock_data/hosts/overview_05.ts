import {HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {createDefaultUiStatus} from './ui_status_utils';

const overview: HostOverview = {
  hostName: 'host-e-5.example.com',
  ip: '192.168.4.104',
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
      state: 'STOPPING',
      title: 'Stopping',
      tooltip:
        'The release system is attempting to stop the Lab Server process.',
    },
    version: 'R123.45.6',
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
export const OVERVIEW_05: MockHostScenario = {
  hostName: 'host-e-5.example.com',
  scenarioName: 'Overview 5: Online (Server Stopping)',
  overview,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
};
