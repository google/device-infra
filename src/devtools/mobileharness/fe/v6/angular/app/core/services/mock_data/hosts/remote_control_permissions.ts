/** @fileoverview Mock host scenarios for Remote Control Permission validation. */

import {
  DeviceSummary,
  HostConnectivityState,
  HostOverview,
} from '../../../models/host_overview';
import {MockHostScenario} from '../models';
import {
  createDefaultUiStatus,
  createDeviceActions,
  createHostActions,
} from './ui_status_utils';

// Helper to create a basic HostOverview
function createHostOverview(
  name: string,
  connectivityState: HostConnectivityState = 'RUNNING',
): HostOverview {
  return {
    hostName: name,
    ip: '192.168.1.1',
    os: 'gLinux',
    canUpgrade: false,
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

// Single scenario containing devices with various permission states
const DEVICES_PERMISSIONS_ALL: DeviceSummary[] = [
  // 1. Devices with NO permissions
  {
    id: 'RC-NO-PERM-1',
    healthState: {health: 'IN_SERVICE_IDLE', title: 'Idle', tooltip: 'Idle'},
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'No Permission 1',
    requiredDims: '',
    model: 'Pixel 8',
    version: '14',
    actions: createDeviceActions('NO_PERMISSION'),
  },
  {
    id: 'RC-NO-PERM-2',
    healthState: {health: 'IN_SERVICE_IDLE', title: 'Idle', tooltip: 'Idle'},
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'No Permission 2',
    requiredDims: '',
    model: 'Pixel 8 Pro',
    version: '14',
    actions: createDeviceActions('NO_PERMISSION'),
  },

  // 2. Device with USER permission only
  {
    id: 'RC-VALID-USER-1',
    healthState: {health: 'IN_SERVICE_IDLE', title: 'Idle', tooltip: 'Idle'},
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'User Permission Only',
    requiredDims: '',
    model: 'Pixel 7',
    version: '13',
    actions: createDeviceActions('USER_PERMISSION'),
  },

  // 3. Device with GROUP permission only
  {
    id: 'RC-VALID-GROUP-1',
    healthState: {health: 'IN_SERVICE_IDLE', title: 'Idle', tooltip: 'Idle'},
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'Group Permission Only',
    requiredDims: '',
    model: 'Pixel 7 Pro',
    version: '13',
    actions: createDeviceActions('GROUP_PERMISSION'),
  },

  // 4. Device with BOTH/ALL permissions
  {
    id: 'RC-VALID-1',
    healthState: {health: 'IN_SERVICE_IDLE', title: 'Idle', tooltip: 'Idle'},
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: false, status: 'IDLE'},
    label: 'All Permissions',
    requiredDims: '',
    model: 'Pixel 6',
    version: '12',
    actions: createDeviceActions('ALL_PERMISSIONS'),
  },
  {
    id: 'RC-INVALID-ineligible-busy',
    healthState: {
      health: 'IN_SERVICE_BUSY',
      title: 'In Service (Busy)',
      tooltip: 'The device is healthy and currently running a task.',
    },
    types: [{type: 'AndroidRealDevice', isAbnormal: false}],
    deviceStatus: {isCritical: true, status: 'BUSY'},
    label: 'All Permissions 2',
    requiredDims: '',
    model: 'Pixel 6 Pro',
    version: '12',
  },
];

/** Scenario covering various permission states for remote control. */
export const SCENARIO_RC_PERMISSIONS_ALL: MockHostScenario = {
  hostName: 'host-rc-permissions.example.com',
  scenarioName: 'RC: Permissions Validation (All Cases)',
  overview: createHostOverview('host-rc-permissions.example.com'),
  deviceSummaries: DEVICES_PERMISSIONS_ALL,
  hostConfigResult: {hostConfig: undefined, uiStatus: createDefaultUiStatus()},
  defaultDeviceConfig: null,
  actions: createHostActions(),
};
