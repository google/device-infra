import {DeviceSummary, HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {createDefaultUiStatus} from './ui_status_utils';

const overview: HostOverview = {
  hostName: 'decommission-test-host.prod.example.com',
  ip: '192.168.13.113',
  os: 'gLinux',
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
    version: 'R126.0.0',
    passThroughFlags: '',
  },
  daemonServer: {
    status: {
      state: 'RUNNING',
      title: 'Running',
      tooltip: 'The Daemon Server is running.',
    },
    version: '24.09.01',
  },
  properties: {},
};

const deviceSummaries: DeviceSummary[] = [
  {
    id: 'MISSING-DEV-1',
    healthState: {
      health: 'OUT_OF_SERVICE_NEEDS_FIXING',
      title: 'Out of Service (Needs Fixing)',
      tooltip: 'Device is missing.',
    },
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: true, status: 'MISSING'},
    label: 'old-device-1',
    requiredDims: 'pool:missing',
    model: 'Pixel 6',
    version: '13',
  },
  {
    id: 'MISSING-DEV-2',
    healthState: {
      health: 'OUT_OF_SERVICE_NEEDS_FIXING',
      title: 'Out of Service (Needs Fixing)',
      tooltip: 'Device is missing.',
    },
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: true, status: 'MISSING'},
    label: 'old-device-2',
    requiredDims: 'pool:missing',
    model: 'Pixel 6 Pro',
    version: '13',
  },
  {
    id: 'MISSING-DEV-3',
    healthState: {
      health: 'OUT_OF_SERVICE_NEEDS_FIXING',
      title: 'Out of Service (Needs Fixing)',
      tooltip: 'Device is missing.',
    },
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: true, status: 'MISSING'},
    label: 'old-device-3',
    requiredDims: 'pool:missing',
    model: 'Pixel 7',
    version: '14',
  },
  {
    id: 'ACTIVE-DEV-4',
    healthState: {
      health: 'IN_SERVICE_IDLE',
      title: 'In Service (Idle)',
      tooltip: 'Device is healthy and ready for tasks.',
    },
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'active-device',
    requiredDims: 'pool:active',
    model: 'Pixel 8',
    version: '14',
  },
];

/** Mock host overview data. */
export const OVERVIEW_13: MockHostScenario = {
  hostName: 'decommission-test-host.prod.example.com',
  scenarioName: 'Overview 13: Decommission Devices',
  overview,
  deviceSummaries,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
};
