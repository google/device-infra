import {DeviceConfig} from '../../../models/device_config_models';
import {HostConfig} from '../../../models/host_config_models';
import {DeviceSummary, HostOverview} from '../../../models/host_overview';
import {MockHostScenario} from '../models';

import {createDefaultUiStatus} from './ui_status_utils';

const DEFAULT_DEVICE_CONFIG: DeviceConfig = {
  permissions: {owners: ['admin1'], executors: ['user1']},
  wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
  dimensions: {supported: [], required: []},
  settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
};

const HOST_CONFIG: HostConfig = {
  permissions: {
    hostAdmins: ['admin1', 'derekchen'],
    sshAccess: [],
  },
  deviceConfigMode: 'PER_DEVICE',
  deviceConfig: DEFAULT_DEVICE_CONFIG,
  hostProperties: [
    {key: 'cpu-arch', value: 'x86_64'},
    {key: 'gpu-type', value: 'NVIDIA-A100'},
    {key: 'ram-gb', value: '256'},
    {key: 'storage-type', value: 'SSD'},
  ],
  deviceDiscovery: {
    monitoredDeviceUuids: [],
    testbedUuids: [],
    miscDeviceUuids: [],
    overTcpIps: [],
    overSshDevices: [],
    manekiSpecs: [],
  },
};

const overview: HostOverview = {
  hostName: 'host-a-1.example.com',
  ip: '192.168.1.101',
  os: 'gLinux',
  labTypeDisplayNames: [
    'Satellite Lab',
    'SLaaS',
    'Core Lab',
    'Mobly',
    'Android',
  ],
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
  {
    id: 'TESTBED-01',
    healthState: {
      health: 'IN_SERVICE_IDLE',
      title: 'In Service (Idle)',
      tooltip: 'Device is healthy and ready for tasks.',
    },
    types: [
      {type: 'TestbedDevice', isAbnormal: false},
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
    subDevices: [
      {
        id: 'sub-device-01',
        types: [
          {type: 'NormalTestbedDevice', isAbnormal: false},
          {type: 'TestbedDevice', isAbnormal: false},
          {type: 'MoblyDevice', isAbnormal: false},
        ],
        dimensions: [
          {name: 'mh_device_type', value: 'MiscDevice'},
          {name: 'mh_device_type', value: 'MiscTestbedSubDevice'},
          {name: 'host_ip', value: '100.95.189.25'},
          {name: 'host_os', value: 'Linux'},
          {name: 'lab_type', value: 'satellite'},
          {name: 'supports_adhoc', value: 'true'},
          {name: 'lab_location', value: 'sjc'},
          {name: 'location_type', value: 'not_in_china'},
          {name: 'dm_type', value: 'mh'},
          {name: 'lab_supports_container', value: 'true'},
          {name: 'host_os_version', value: 'Ubuntu 20.04.6 LTS'},
          {name: 'host_version', value: '4.342.0'},
          {name: 'mobly_type', value: 'Attenuator'},
          {name: 'lab_supports_sandbox', value: 'true'},
          {name: 'host_name', value: 'aw-zo3.sjc.corp.example.com'},
        ],
      },
      {
        id: '41081FDAS000YB',
        types: [
          {type: 'AbnormalAndroidFlashableDevice', isAbnormal: true},
          {type: 'AndroidFlashableDevice', isAbnormal: false},
          {type: 'AndroidOnlineDevice', isAbnormal: false},
          {type: 'AndroidDevice', isAbnormal: false},
        ],
        dimensions: [
          {name: 'model', value: 'Pixel 8'},
          {name: 'version', value: '15'},
          {name: 'pool', value: 'staging'},
          {name: 'label', value: 'golden-pixel'},
          {name: 'container', value: 'docker'},
          {name: 'host_name', value: 'host-a-1.example.com'},
          {name: 'host_ip', value: '192.168.1.101'},
          {name: 'host_os', value: 'gLinux'},
          {name: 'host_os_version', value: '22.04'},
          {name: 'host_ip', value: '192.168.1.101'},
          {name: 'host_name', value: 'host-a-1.example.com'},
        ],
      },
    ],
  },
];

/** Mock host overview data. */
export const OVERVIEW_01: MockHostScenario = {
  hostName: 'host-a-1.example.com',
  scenarioName: 'Overview 1: Online, SLaaS, Running',
  overview,
  deviceSummaries,
  hostConfigResult: {
    hostConfig: HOST_CONFIG,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: DEFAULT_DEVICE_CONFIG,
};
