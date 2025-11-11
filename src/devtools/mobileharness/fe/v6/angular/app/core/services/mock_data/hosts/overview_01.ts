import {HostOverview} from '../../../models/host_overview';
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
  devices: [],
  properties: {
    'cpu-arch': 'x86_64',
    'gpu-type': 'NVIDIA-A100',
    'ram-gb': '256',
    'storage-type': 'SSD',
  },
};

/** Mock host overview data. */
export const OVERVIEW_01: MockHostScenario = {
  hostName: 'host-a-1.example.com',
  scenarioName: 'Overview 1: Online, SLaaS, Running',
  overview,
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
};
