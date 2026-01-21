/** @fileoverview Mock host scenarios for Multi-Remote Control validation. */

import {
  DeviceSummary,
  HostConnectivityState,
  HostOverview,
} from '../../../models/host_overview';
import {MockHostScenario} from '../models';
import {createDefaultUiStatus} from './ui_status_utils';

// Helper to create a basic HostOverview
function createHostOverview(
  name: string,
  connectivityState: HostConnectivityState = 'RUNNING',
): HostOverview {
  return {
    hostName: name,
    ip: '192.168.1.1',
    os: 'gLinux',
    labTypeDisplayNames: ['Satellite Lab'],
    labServer: {
      connectivity: {
        state: connectivityState,
        title: connectivityState,
        tooltip: 'Connectivity status',
      },
      activity: {
        state: 'STARTED',
        title: 'Started',
        tooltip: 'Activity status',
      },
      version: 'R123.45.6',
      passThroughFlags: '',
    },
    daemonServer: {
      status: {
        state: 'RUNNING',
        title: 'Running',
        tooltip: 'Daemon running',
      },
      version: '24.08.01',
    },
    properties: {},
  };
}

// 1. All Valid (All devices support RC and are IDLE)
const DEVICES_ALL_VALID: DeviceSummary[] = [
  {
    id: 'RC-VALID-1',
    healthState: {health: 'IN_SERVICE_IDLE', title: 'Idle', tooltip: 'Idle'},
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'Valid Device 1',
    requiredDims: '',
    model: 'Pixel 8',
    version: '14',
  },
  {
    id: 'RC-VALID-2',
    healthState: {health: 'IN_SERVICE_IDLE', title: 'Idle', tooltip: 'Idle'},
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'Valid Device 2',
    requiredDims: '',
    model: 'Pixel 8 Pro',
    version: '14',
  },
  {
    id: 'RC-TESTBED-VALID-1',
    healthState: {health: 'IN_SERVICE_IDLE', title: 'Idle', tooltip: 'Idle'},
    types: [{type: 'TestbedDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'Testbed Valid Device 1',
    requiredDims: '',
    model: 'Testbed',
    version: 'N/A',
    subDevices: [
      {
        id: 'sub-device-tb-valid-1',
        types: [{type: 'AndroidRealDevice', isAbnormal: false}],
        dimensions: [
          {name: 'label', value: 'valid-device'},
          {name: 'battery_level', value: '100'},
          {name: 'wifi_rssi', value: '-55'},
        ],
        model: 'Pixel 8',
        version: '14',
        batteryLevel: 100,
        network: {wifiRssi: -55, hasInternet: true},
        remoteControl: {isSupported: true},
      },
      {
        id: 'sub-device-tb-valid-2',
        types: [{type: 'AndroidRealDevice', isAbnormal: false}],
        dimensions: [
          {name: 'label', value: 'valid-device'},
          {name: 'battery_level', value: '88'},
          {name: 'wifi_rssi', value: '-70'},
        ],
        model: 'Pixel 7',
        version: '13',
        batteryLevel: 88,
        network: {wifiRssi: -70, hasInternet: false},
        remoteControl: {isSupported: true},
      },
      {
        id: 'sub-device-tb-valid-3',
        types: [{type: 'AndroidRealDevice', isAbnormal: false}],
        dimensions: [
          {name: 'label', value: 'valid-device'},
          {name: 'battery_level', value: '5'},
          {name: 'wifi_rssi', value: '-80'},
        ],
        model: 'Pixel 6',
        version: '12',
        batteryLevel: 5,
        network: {wifiRssi: -80, hasInternet: true},
        remoteControl: {isSupported: false, unsupportedReason: 'Low battery'},
      },
      {
        id: 'sub-device-tb-valid-4',
        types: [{type: 'AndroidRealDevice', isAbnormal: false}],
        dimensions: [
          {name: 'label', value: 'valid-device'},
          {name: 'battery_level', value: '0'},
          {name: 'wifi_rssi', value: '-90'},
        ],
        model: 'Pixel 5',
        version: '11',
        batteryLevel: 0,
        network: {wifiRssi: -90, hasInternet: false},
        remoteControl: {isSupported: true},
      },
      {
        id: 'sub-device-tb-valid-5',
        types: [{type: 'AndroidRealDevice', isAbnormal: false}],
        dimensions: [],
        model: 'Pixel 4',
        version: '10',
        batteryLevel: null,
        network: {wifiRssi: undefined, hasInternet: undefined},
        remoteControl: {isSupported: false, unsupportedReason: 'Not supported'},
      },
    ],
  },
];

/** Scenario where all devices are eligible for remote control. */
export const SCENARIO_RC_ALL_VALID: MockHostScenario = {
  hostName: 'host-rc-all-valid.example.com',
  scenarioName: 'RC: All Devices Valid',
  overview: createHostOverview('host-rc-all-valid.example.com'),
  deviceSummaries: DEVICES_ALL_VALID,
  hostConfigResult: {hostConfig: undefined, uiStatus: createDefaultUiStatus()},
  defaultDeviceConfig: null,
};

// 2. Mixed Status & Capabilities (Some IDLE, some BUSY, some ERROR, some RC-unsupported)
const DEVICES_MIXED_ALL: DeviceSummary[] = [
  {
    id: 'RC-IDLE-1',
    healthState: {health: 'IN_SERVICE_IDLE', title: 'Idle', tooltip: 'Idle'},
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'Idle Device',
    requiredDims: '',
    model: 'Pixel 7',
    version: '13',
  },
  {
    id: 'RC-ineligible-busy',
    healthState: {health: 'IN_SERVICE_BUSY', title: 'Busy', tooltip: 'Busy'},
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'BUSY'},
    label: 'Busy Device',
    requiredDims: '',
    model: 'Pixel 7',
    version: '13',
  },
  {
    id: 'RC--ineligible-failed',
    healthState: {
      health: 'OUT_OF_SERVICE_NEEDS_FIXING',
      title: 'Error',
      tooltip: 'Error',
    },
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: true, status: 'FAILED'},
    label: 'Error Device',
    requiredDims: '',
    model: 'Pixel 6',
    version: '12',
  },
  {
    id: 'RC-ineligible-no-acid',
    healthState: {health: 'IN_SERVICE_IDLE', title: 'Idle', tooltip: 'Idle'},
    types: [{type: 'LinuxDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'No RC Support',
    requiredDims: '',
    model: 'Linux PC',
    version: 'Ubuntu',
  },
  {
    id: 'RC-TESTBED-1',
    healthState: {health: 'IN_SERVICE_IDLE', title: 'Idle', tooltip: 'Idle'},
    types: [{type: 'TestbedDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'Testbed Device',
    requiredDims: '',
    model: 'Testbed',
    version: 'N/A',
    subDevices: [
      {
        id: 'sub-device-1',
        types: [{type: 'AndroidRealDevice', isAbnormal: false}],
        dimensions: [
          {name: 'host_name', value: 'host-rc-mixed-status.example.com'},
          {name: 'host_ip', value: '192.168.1.1'},
          {name: 'host_os', value: 'gLinux'},
          {name: 'host_os_version', value: '22.04'},
          {name: 'host_ip', value: '192.168.1.1'},
          {name: 'host_name', value: 'host-rc-mixed-status.example.com'},
        ],
        model: 'Pixel 8',
        version: '14',
      },
    ],
  },
  {
    id: 'RC-TESTBED-ineligible-no-acid',
    healthState: {health: 'IN_SERVICE_IDLE', title: 'Idle', tooltip: 'Idle'},
    types: [{type: 'TestbedDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'Testbed No ACID',
    requiredDims: '',
    model: 'Testbed',
    version: 'N/A',
    subDevices: [
      {
        id: 'sub-device-2',
        types: [{type: 'SomeOtherDevice', isAbnormal: false}],
        dimensions: [{name: 'label', value: 'no-acid'}],
        model: 'Pixel 8',
        version: '14',
      },
    ],
  },
];

/** Scenario with a mix of eligible and ineligible devices. */
export const SCENARIO_RC_MIXED_ALL: MockHostScenario = {
  hostName: 'host-rc-mixed-status.example.com',
  scenarioName: 'RC: Mixed Status & Capabilities',
  overview: createHostOverview('host-rc-mixed-status.example.com'),
  deviceSummaries: DEVICES_MIXED_ALL,
  hostConfigResult: {hostConfig: undefined, uiStatus: createDefaultUiStatus()},
  defaultDeviceConfig: null,
};

// 4. Proxy Mismatch (To test error handling when proxy does not match)
const DEVICES_PROXY_MISMATCH: DeviceSummary[] = [
  {
    id: 'RC-PROXY-MISMATCH-1',
    healthState: {
      health: 'IN_SERVICE_IDLE',
      title: 'Idle',
      tooltip: 'Idle',
    },
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'Device for Proxy Mismatch',
    requiredDims: '',
    model: 'Pixel 8',
    version: '14',
  },
  {
    id: 'RC-PROXY-MISMATCH-2',
    healthState: {
      health: 'IN_SERVICE_IDLE',
      title: 'Idle',
      tooltip: 'Idle',
    },
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'Proxy Mismatch 2',
    requiredDims: '',
    model: 'Pixel 8 Pro',
    version: '21',
  },
  {
    id: 'RC-NO-PROXY',
    healthState: {health: 'IN_SERVICE_IDLE', title: 'Idle', tooltip: 'Idle'},
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'No Proxy',
    requiredDims: '',
    model: 'Pixel 8 Pro',
    version: '21',
  },
  {
    id: 'RC-TESTBED-NO-PROXY',
    healthState: {health: 'IN_SERVICE_IDLE', title: 'Idle', tooltip: 'Idle'},
    types: [{type: 'TestbedDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'Testbed No Common Proxy',
    requiredDims: '',
    model: 'Testbed',
    version: 'N/A',
    subDevices: [
      {
        id: 'sub-device-tb-no-proxy',
        types: [{type: 'SomeDevice', isAbnormal: false}],
        dimensions: [{name: 'label', value: 'no-proxy'}],
        model: 'Pixel 8 Pro',
        version: '21',
      },
    ],
  },
];

/** Scenario where proxy types do not match across selected devices. */
export const SCENARIO_RC_PROXY_MISMATCH: MockHostScenario = {
  hostName: 'host-rc-proxy-mismatch.example.com',
  scenarioName: 'RC: Proxy Mismatch',
  overview: createHostOverview('host-rc-proxy-mismatch.example.com'), // Running host
  deviceSummaries: DEVICES_PROXY_MISMATCH,
  hostConfigResult: {hostConfig: undefined, uiStatus: createDefaultUiStatus()},
  defaultDeviceConfig: null,
};
