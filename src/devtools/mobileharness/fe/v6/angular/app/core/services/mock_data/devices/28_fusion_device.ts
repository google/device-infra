import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'FUSION_DEVICE_001';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-fusion-1.example.com', ip: '192.168.1.102'},
  healthAndActivity: {
    title: 'In Service (Idle)',
    subtitle: 'Fusion device ready for testing.',
    state: 'IN_SERVICE_IDLE',
    deviceStatus: {status: 'IDLE', isCritical: false},
    deviceTypes: ['AndroidDevice', 'AndroidRealDevice'].map((type) => ({
      type,
      isAbnormal: false,
    })),
    lastInServiceTime: new Date().toISOString(),
  },
  basicInfo: {
    model: 'Pixel Fusion',
    version: '15',
    form: 'physical',
    os: 'Android',
    batteryLevel: 100,
    network: {wifiRssi: -50, hasInternet: true},
    hardware: 'fusion-hw',
    build: 'FUSION.2026',
  },
  permissions: {
    owners: ['fusion-user'],
    executors: ['fusion-executor'],
  },
  capabilities: {
    supportedDrivers: ['AndroidInstrumentation'],
    supportedDecorators: ['AndroidLogCatDecorator'],
  },
  dimensions: {
    supported: {
      'Detected by OmniLab': {
        dimensions: [
          {name: 'dm_type', value: 'fusion'},
          {name: 'pool', value: 'fusion-pool'},
        ],
      },
    },
    required: {},
  },
  properties: {},
};

const CONFIG: DeviceConfig = {
  permissions: {
    owners: ['fusion-user'],
    executors: ['fusion-executor'],
  },
  wifi: {
    type: 'pre-configured',
    ssid: 'FusionGuest',
    psk: 'secure',
    scanSsid: true,
  },
  dimensions: {
    supported: [{name: 'pool', value: 'fusion-pool'}],
    required: [],
  },
  settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 1000},
};

const SCENARIO_FUSION_DEVICE_DATA: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: 'Fusion Device',
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

/**
 * Returns the mock scenario for a Fusion device.
 *
 * @param callCount Optional parameter indicating how many times this has been called (not used in this mock).
 * @returns The MockDeviceScenario representing a Fusion device with metrics URL and fusion dimensions.
 */
export function scenarioFusionDevice(callCount?: number): MockDeviceScenario {
  return SCENARIO_FUSION_DEVICE_DATA;
}
