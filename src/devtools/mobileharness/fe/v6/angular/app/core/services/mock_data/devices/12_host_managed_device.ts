/**
 * @fileoverview Mock data for the "Host Managed Device" device scenario. This
 * scenario represents a device that is managed by the host and is not
 * accessible to the lab server.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'HOST-DEVICE-001-ineligible-no-acid';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-x.example.com', ip: '192.168.24.124'},
  healthAndActivity: {
    title: 'In Service (Idle)',
    subtitle: 'The device is healthy and ready for new tasks.',
    state: 'IN_SERVICE_IDLE',
    deviceStatus: {status: 'IDLE', isCritical: false},
    deviceTypes: [{type: 'AndroidDevice', isAbnormal: false}],
    lastInServiceTime: new Date().toISOString(),
  },
  basicInfo: {
    model: 'Pixel 8 Pro',
    version: '14',
    form: 'physical',
    os: 'Android',
    batteryLevel: 99,
    network: {wifiRssi: -55, hasInternet: true},
    hardware: 'g/2345a',
    build: 'AP1A.240405.002',
  },
  permissions: {owners: ['host-admin'], executors: ['host-runner']},
  capabilities: {
    supportedDrivers: ['HostBin', 'ManekiTest'],
    supportedDecorators: ['AndroidFilePusherDecorator'],
  },
  dimensions: {
    supported: {
      'From Host Config': {
        dimensions: [
          {name: 'location', value: 'SH-ABC-123'},
          {name: 'rack', value: '42'},
        ],
      },
    },
    required: {
      'From Host Config': {
        dimensions: [{name: 'power', value: 'dedicated'}],
      },
    },
  },
  properties: {'host-os': 'gLinux', 'maint-window': '2am-4am PST'},
};

const CONFIG: DeviceConfig = {
  permissions: {owners: [], executors: []},
  wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
  dimensions: {
    supported: [],
    required: [],
  },
  settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
};

/**
 * Represents a mock device scenario where the device is managed by the host
 * and is not accessible to the lab server. This scenario is used for testing
 * and development purposes to simulate a device in a specific state.
 */
export const SCENARIO_HOST_MANAGED_DEVICE: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: 'Host Managed Device',
  overview: OVERVIEW,
  config: CONFIG,
  isQuarantined: false,
  actionVisibility: {
    screenshot: true,
    logcat: true,
    flash: true,
    remoteControl: true,
    quarantine: true,
  },
};
