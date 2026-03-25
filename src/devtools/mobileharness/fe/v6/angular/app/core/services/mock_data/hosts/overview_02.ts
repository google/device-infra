import {DeviceSummary, HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {createDefaultUiStatus} from './ui_status_utils';

const overview: HostOverview = {
  hostName: 'host-b-2.example.com',
  ip: '192.168.2.102',
  os: 'gLinux',
  canUpgrade: true,
  labTypeDisplayNames: ['Satellite Lab'],
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
  properties: {},
};

const deviceSummaries: DeviceSummary[] = [
  {
    id: 'ANDROID-FAILED-DEVICE',
    healthState: {
      health: 'OUT_OF_SERVICE_NEEDS_FIXING',
      title: 'Out of Service (Needs Fixing)',
      tooltip: 'Device needs fixing.',
    },
    types: [{type: 'AndroidDevice', isAbnormal: false}],
    deviceStatus: {isCritical: true, status: 'FAILED'},
    label: '',
    requiredDims: 'pool:staging',
    model: 'Pixel 6',
    version: '13',
  },
];

/** Mock host overview data. */
export const OVERVIEW_02: MockHostScenario = {
  hostName: 'host-b-2.example.com',
  scenarioName: 'Overview 2: Unconfigured Satellite Host (Update Pending)',
  overview,
  deviceSummaries,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
  actions: {
    configuration: {enabled: true, visible: true, tooltip: 'Open configuration'},
    debug: {enabled: true, visible: true, tooltip: 'Open debug terminal'},
    deploy: {enabled: true, visible: true, tooltip: 'Deploy new release'},
    start: {enabled: false, visible: false, tooltip: 'Server already started'},
    restart: {enabled: true, visible: true, tooltip: 'Restart lab server'},
    stop: {enabled: true, visible: true, tooltip: 'Stop lab server'},
    decommission: {enabled: false, visible: true, tooltip: 'Host is not missing'},
    updatePassThroughFlags: {enabled: true, visible: true, tooltip: 'Edit flags'},
    release: {enabled: true, visible: true, tooltip: 'View release notes'},
  },
};
