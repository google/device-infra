import {DeviceSummary, HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {createDefaultUiStatus} from './ui_status_utils';

const overview: HostOverview = {
  hostName: 'at1-ab7.atc.google.com',
  ip: '2001:4860:1016:3:8aae:ddff:fe0b:a998',
  os: 'Linux',
  labTypeDisplayNames: ['Satellite Lab', 'ATE Lab'],
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
    version:
      'semantic_version \t {\n  major: "4"\n  minor: "353"\n  patch: "0"\n}\n',
    passThroughFlags: '',
  },
  daemonServer: {
    status: {
      state: 'RUNNING',
      title: 'Running',
      tooltip: 'The Daemon Server is running.',
    },
    version:
      'semantic_version \t {\n  major: "5"\n  minor: "2"\n  patch: "9"\n}\n',
  },
  properties: {
    'lab': 'atc',
    'host_group': 'atc:ate-main-high-concurrency',
    'owner': 'user:android-test',
    'host_login_name': 'android-test',
    'host_config_mode': 'config_pusher',
    'config_pusher_mode': 'DEFAULT',
    'device_config_mode': 'host',
    'lab_type': 'satellite',
    'dm_type': 'mh',
    'host_os': 'Linux',
    'host_os_version': 'Ubuntu 22.04.4 LTS',
    'host_version': '4.353.0',
    'lab_location': 'atc',
    'location_type': 'not_in_china',
    'total_mem': '62.5GB',
    'java_version': '25.0.1',
    'root_disk_space': '455G',
    'tradefed_version': '14824683',
  },
};

const deviceSummaries: DeviceSummary[] = [
  {
    id: '21211FDH30002F',
    healthState: {
      health: 'OUT_OF_SERVICE_TEMP_MAINT',
      title: 'Out of Service (may be temporary)',
      tooltip:
        'The device is temporarily unavailable due to routine maintenance.',
    },
    types: [
      {
        type: 'AndroidRealDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidFlashableDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidOnlineDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidDevice',
        isAbnormal: false,
      },
    ],
    deviceStatus: {
      status: 'INIT',
      isCritical: false,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: 'pixel 7 pro',
    version: '36',
  },
  {
    id: '2A311FDH300A2H',
    healthState: {
      health: 'IN_SERVICE_BUSY',
      title: 'In Service (Busy)',
      tooltip: 'The device is healthy and currently running a task.',
    },
    types: [
      {
        type: 'AndroidRealDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidFlashableDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidOnlineDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidDevice',
        isAbnormal: false,
      },
    ],
    deviceStatus: {
      status: 'BUSY',
      isCritical: false,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: 'pixel 7 pro',
    version: '36',
  },
  {
    id: '2A311FDH300AAZ',
    healthState: {
      health: 'IN_SERVICE_BUSY',
      title: 'In Service (Busy)',
      tooltip: 'The device is healthy and currently running a task.',
    },
    types: [
      {
        type: 'AndroidRealDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidFlashableDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidOnlineDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidDevice',
        isAbnormal: false,
      },
    ],
    deviceStatus: {
      status: 'BUSY',
      isCritical: false,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: 'pixel 7 pro',
    version: '36',
  },
  {
    id: '2B011FDH300169',
    healthState: {
      health: 'IN_SERVICE_BUSY',
      title: 'In Service (Busy)',
      tooltip: 'The device is healthy and currently running a task.',
    },
    types: [
      {
        type: 'AndroidRealDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidFlashableDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidOnlineDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidDevice',
        isAbnormal: false,
      },
    ],
    deviceStatus: {
      status: 'BUSY',
      isCritical: false,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: 'pixel 7 pro',
    version: '36',
  },
  {
    id: '2B011FDH300FDY',
    healthState: {
      health: 'IN_SERVICE_BUSY',
      title: 'In Service (Busy)',
      tooltip: 'The device is healthy and currently running a task.',
    },
    types: [
      {
        type: 'AndroidRealDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidFlashableDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidOnlineDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidDevice',
        isAbnormal: false,
      },
    ],
    deviceStatus: {
      status: 'BUSY',
      isCritical: false,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: 'pixel 7 pro',
    version: '36',
  },
  {
    id: '2B011FDH300FFC',
    healthState: {
      health: 'OUT_OF_SERVICE_NEEDS_FIXING',
      title: 'Out of Service (Needs Fixing)',
      tooltip: 'The device is in an error state and requires attention.',
    },
    types: [
      {
        type: 'AndroidRealDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidFlashableDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidOnlineDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidDevice',
        isAbnormal: false,
      },
    ],
    deviceStatus: {
      status: 'DYING',
      isCritical: true,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: 'pixel 7 pro',
    version: '36',
  },
  {
    id: '2B021FDH30015J',
    healthState: {
      health: 'IN_SERVICE_BUSY',
      title: 'In Service (Busy)',
      tooltip: 'The device is healthy and currently running a task.',
    },
    types: [
      {
        type: 'AndroidRealDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidFlashableDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidOnlineDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidDevice',
        isAbnormal: false,
      },
    ],
    deviceStatus: {
      status: 'BUSY',
      isCritical: false,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: 'pixel 7 pro',
    version: '36',
  },
  {
    id: '2B021FDH3007X2',
    healthState: {
      health: 'IN_SERVICE_BUSY',
      title: 'In Service (Busy)',
      tooltip: 'The device is healthy and currently running a task.',
    },
    types: [
      {
        type: 'AndroidRealDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidFlashableDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidOnlineDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidDevice',
        isAbnormal: false,
      },
    ],
    deviceStatus: {
      status: 'BUSY',
      isCritical: false,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: 'pixel 7 pro',
    version: '36',
  },
  {
    id: '2B021FDH300833',
    healthState: {
      health: 'IN_SERVICE_BUSY',
      title: 'In Service (Busy)',
      tooltip: 'The device is healthy and currently running a task.',
    },
    types: [
      {
        type: 'AndroidRealDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidFlashableDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidOnlineDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidDevice',
        isAbnormal: false,
      },
    ],
    deviceStatus: {
      status: 'BUSY',
      isCritical: false,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: 'pixel 7 pro',
    version: '36',
  },
  {
    id: '2B021FDH30087D',
    healthState: {
      health: 'IN_SERVICE_BUSY',
      title: 'In Service (Busy)',
      tooltip: 'The device is healthy and currently running a task.',
    },
    types: [
      {
        type: 'AndroidRealDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidFlashableDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidOnlineDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidDevice',
        isAbnormal: false,
      },
    ],
    deviceStatus: {
      status: 'BUSY',
      isCritical: false,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: 'pixel 7 pro',
    version: '36',
  },
  {
    id: '2B021FDH300891',
    healthState: {
      health: 'IN_SERVICE_BUSY',
      title: 'In Service (Busy)',
      tooltip: 'The device is healthy and currently running a task.',
    },
    types: [
      {
        type: 'AndroidRealDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidFlashableDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidOnlineDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidDevice',
        isAbnormal: false,
      },
    ],
    deviceStatus: {
      status: 'BUSY',
      isCritical: false,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: 'pixel 7 pro',
    version: '36',
  },
  {
    id: '2B021FDH3008EP',
    healthState: {
      health: 'IN_SERVICE_BUSY',
      title: 'In Service (Busy)',
      tooltip: 'The device is healthy and currently running a task.',
    },
    types: [
      {
        type: 'AndroidRealDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidFlashableDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidOnlineDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidDevice',
        isAbnormal: false,
      },
    ],
    deviceStatus: {
      status: 'BUSY',
      isCritical: false,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: 'pixel 7 pro',
    version: '36',
  },
  {
    id: '2B021FDH3008GN',
    healthState: {
      health: 'OUT_OF_SERVICE_NEEDS_FIXING',
      title: 'Out of Service (Needs Fixing)',
      tooltip: 'The device is in an error state and requires attention.',
    },
    types: [],
    deviceStatus: {
      status: 'BUSY',
      isCritical: true,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: '',
    version: '',
  },
  {
    id: '2B021FDH3008GP',
    healthState: {
      health: 'IN_SERVICE_BUSY',
      title: 'In Service (Busy)',
      tooltip: 'The device is healthy and currently running a task.',
    },
    types: [
      {
        type: 'AndroidRealDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidFlashableDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidOnlineDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidDevice',
        isAbnormal: false,
      },
    ],
    deviceStatus: {
      status: 'BUSY',
      isCritical: false,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: 'pixel 7 pro',
    version: '36',
  },
  {
    id: '2B021FDH3008MN',
    healthState: {
      health: 'IN_SERVICE_BUSY',
      title: 'In Service (Busy)',
      tooltip: 'The device is healthy and currently running a task.',
    },
    types: [
      {
        type: 'AndroidRealDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidFlashableDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidOnlineDevice',
        isAbnormal: false,
      },
      {
        type: 'AndroidDevice',
        isAbnormal: false,
      },
    ],
    deviceStatus: {
      status: 'BUSY',
      isCritical: false,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: 'pixel 7 pro',
    version: '36',
  },
  {
    id: 'at1-ab7.atc.google.com:33487',
    healthState: {
      health: 'OUT_OF_SERVICE_NEEDS_FIXING',
      title: 'Out of Service (Needs Fixing)',
      tooltip: 'The device is in an error state and requires attention.',
    },
    types: [],
    deviceStatus: {
      status: 'MISSING',
      isCritical: true,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: '',
    version: '',
  },
  {
    id: 'at1-ab7.atc.google.com:35137',
    healthState: {
      health: 'OUT_OF_SERVICE_NEEDS_FIXING',
      title: 'Out of Service (Needs Fixing)',
      tooltip: 'The device is in an error state and requires attention.',
    },
    types: [],
    deviceStatus: {
      status: 'MISSING',
      isCritical: true,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: '',
    version: '',
  },
  {
    id: 'at1-ab7.atc.google.com:35555',
    healthState: {
      health: 'OUT_OF_SERVICE_NEEDS_FIXING',
      title: 'Out of Service (Needs Fixing)',
      tooltip: 'The device is in an error state and requires attention.',
    },
    types: [],
    deviceStatus: {
      status: 'MISSING',
      isCritical: true,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: '',
    version: '',
  },
  {
    id: 'at1-ab7.atc.google.com:36735',
    healthState: {
      health: 'OUT_OF_SERVICE_NEEDS_FIXING',
      title: 'Out of Service (Needs Fixing)',
      tooltip: 'The device is in an error state and requires attention.',
    },
    types: [],
    deviceStatus: {
      status: 'MISSING',
      isCritical: true,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: '',
    version: '',
  },
  {
    id: 'at1-ab7.atc.google.com:38191',
    healthState: {
      health: 'OUT_OF_SERVICE_NEEDS_FIXING',
      title: 'Out of Service (Needs Fixing)',
      tooltip: 'The device is in an error state and requires attention.',
    },
    types: [],
    deviceStatus: {
      status: 'MISSING',
      isCritical: true,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: '',
    version: '',
  },
  {
    id: 'at1-ab7.atc.google.com:46857',
    healthState: {
      health: 'OUT_OF_SERVICE_NEEDS_FIXING',
      title: 'Out of Service (Needs Fixing)',
      tooltip: 'The device is in an error state and requires attention.',
    },
    types: [],
    deviceStatus: {
      status: 'MISSING',
      isCritical: true,
    },
    label: '',
    requiredDims: 'pool:android-test-executor',
    model: '',
    version: '',
  },
];

/** Mock host overview data. */
export const OVERVIEW_14: MockHostScenario = {
  hostName: 'at1-ab7.atc.google.com',
  scenarioName: 'Overview 14: Sub Devices',
  overview,
  deviceSummaries,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
};
