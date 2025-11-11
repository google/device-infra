import {HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {createDefaultUiStatus} from './ui_status_utils';

const overview: HostOverview = {
  hostName: 'pusher-host-k-11.example.com',
  ip: '192.168.11.111',
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
      state: 'STARTED',
      title: 'Started',
      tooltip:
        'The Lab Server process was started by the release system, and OmniLab is receiving heartbeats.',
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
  },
  devices: [],
  properties: {'pushed-by': 'config-pusher-service', 'user-editable': 'true'},
};

/** Mock host overview data. */
export const OVERVIEW_11: MockHostScenario = {
  hostName: 'pusher-host-k-11.example.com',
  scenarioName: 'Overview 11: Partially Managed by Pusher',
  overview,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
};
