import {DeviceSummary, HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {createDefaultUiStatus} from './ui_status_utils';

const overview: HostOverview = {
  hostName: 'host-a-1.example.com',
  ip: '192.168.1.101',
  os: 'gLinux',
  labTypeDisplayName: 'Satellite Lab (SLaaS)',
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
    passThroughFlags:
      '--nomute_android --noandroid_device_daemon --enable_linux_device --flashstation_cache_dir=/tmp/flashstation_cache --adb=/usr/bin/adb --fastboot=/usr/bin/fastboot',
  },
  daemonServer: {
    status: {
      state: 'RUNNING',
      title: 'Running',
      tooltip: 'The Daemon Server is running.',
    },
    version: '24.08.01',
  },
  properties: {
    'cpu-arch': 'x86_64',
    'gpu-type': 'NVIDIA-A100',
    'ram-gb': '256',
    'storage-type': 'SSD',
  },
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
  {
    id: 'LNX-STABLE-A4B1C2',
    healthState: {
      health: 'IN_SERVICE_BUSY',
      title: 'In Service (Busy)',
      tooltip: 'Device is healthy and running tasks.',
    },
    types: [{type: 'LinuxDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'BUSY'},
    label: '',
    requiredDims: 'container:docker',
    model: 'N/A',
    version: '22.04',
  },
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
  {
    id: 'RECOVERING-DEVICE',
    healthState: {
      health: 'OUT_OF_SERVICE_RECOVERING',
      title: 'Out of Service (Recovering)',
      tooltip: 'Device is recovering.',
    },
    types: [
      {type: 'AndroidDevice', isAbnormal: false},
      {type: 'SomeAbnormalType', isAbnormal: true},
    ],
    deviceStatus: {isCritical: false, status: 'BUSY'},
    label: 'needs-help',
    requiredDims: '',
    model: 'Pixel 8',
    version: '14',
  },
  {
    id: 'NO-TYPE-DEVICE',
    healthState: {
      health: 'OUT_OF_SERVICE_NEEDS_FIXING',
      title: 'Out of Service (Needs Fixing)',
      tooltip: 'Device needs fixing.',
    },
    types: [],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'fix-me',
    requiredDims: '',
    model: 'Unknown',
    version: 'Unknown',
  },
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
export const OVERVIEW_01: MockHostScenario = {
  hostName: 'host-a-1.example.com',
  scenarioName: 'Overview 1: Online, SLaaS, Running',
  overview,
  deviceSummaries,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
};
