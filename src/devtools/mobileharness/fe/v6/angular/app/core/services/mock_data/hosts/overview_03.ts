import {DeviceSummary, HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {createDefaultUiStatus} from './ui_status_utils';

const overview: HostOverview = {
  hostName: 'core-host-c-3.example.com',
  ip: '10.0.1.50',
  os: 'gLinux',
  labTypeDisplayNames: ['Core Lab'],
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
  properties: {
    'cpu-arch': 'x86_64',
    'gpu-type': 'none',
    'ram-gb': '512',
    'storage-type': 'HDD',
  },
};

const deviceSummaries: DeviceSummary[] = [
  {
    id: 'CORE-DEV-001',
    healthState: {
      health: 'IN_SERVICE_IDLE',
      title: 'In Service (Idle)',
      tooltip: 'Device is healthy and ready for tasks.',
    },
    types: [
      {type: 'LinuxDevice', isAbnormal: false},
      {type: 'VirtualDevice', isAbnormal: false},
      {type: 'CoreLabDevice', isAbnormal: false},
    ],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'core-testing',
    requiredDims: 'lab:core',
    model: 'Generic VM',
    version: 'Debian 11',
  },
  {
    id: 'CORE-DEV-002',
    healthState: {
      health: 'OUT_OF_SERVICE_TEMP_MAINT',
      title: 'Out of Service (Temp Maint)',
      tooltip: 'Device is under maintenance.',
    },
    types: [
      {type: 'LinuxDevice', isAbnormal: false},
      {type: 'VirtualDevice', isAbnormal: false},
    ],
    deviceStatus: {isCritical: true, status: 'INIT'},
    label: '',
    requiredDims: 'lab:core',
    model: 'Generic VM',
    version: 'Debian 11',
  },
];

/** Mock host overview data. */
export const OVERVIEW_03: MockHostScenario = {
  hostName: 'core-host-c-3.example.com',
  scenarioName: 'Overview 3: Unconfigured Host (Core Lab)',
  overview,
  deviceSummaries,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
};
