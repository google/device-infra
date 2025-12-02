/**
 * @fileoverview Mock data for the "In Service, IDLE, Android" device scenario.
 * This scenario represents a healthy, fully configured Android device that is
 * ready to accept new tasks.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = '43021FDAQ000UM';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-a-1.prod.example.com', ip: '192.168.1.101'},
  healthAndActivity: {
    title: 'In Service (Idle)',
    subtitle: 'The device is healthy and ready for new tasks.',
    state: 'IN_SERVICE_IDLE',
    deviceStatus: {status: 'IDLE', isCritical: false},
    deviceTypes: [
      'AndroidDevice',
      'AndroidFlashableDevice',
      'AndroidOnlineDevice',
      'AndroidRealDevice',
    ].map((type) => ({type, isAbnormal: false})),
    lastInServiceTime: new Date().toISOString(),
  },
  basicInfo: {
    model: 'Pixel 8 Pro',
    version: '14',
    form: 'physical',
    os: 'Android',
    batteryLevel: 95,
    network: {wifiRssi: -58, hasInternet: true},
    hardware: 'g/2345a',
    build: 'AP1A.240405.002',
  },
  permissions: {
    owners: ['user-a', 'group-infra-team', 'derekchen'],
    executors: ['test-runner-service-account', 'auto-recovery-service'],
  },
  capabilities: {
    supportedDrivers: [
      'AndroidGUnit',
      'AndroidInstrumentation',
      'AndroidMonkey',
      'AndroidRoboTest',
      'AndroidTradefedTest',
      'FlutterDriver',
      'MoblyAospTest',
      'MoblyTest',
    ],
    supportedDecorators: [
      'AndroidBugreportDecorator',
      'AndroidCrashMonitorDecorator',
      'AndroidFilePullerDecorator',
      'AndroidLogCatDecorator',
      'AndroidScreenshotDecorator',
    ],
  },
  dimensions: {
    supported: {
      'From Device Config': {
        dimensions: [
          {name: 'pool', value: 'pixel-prod'},
          {name: 'os', value: '14'},
        ],
      },
      'Detected by OmniLab': {dimensions: []},
    },
    required: {
      'From Device Config': {
        dimensions: [{name: 'min-sdk', value: '33'}],
      },
    },
  },
  properties: {
    'test-type': 'instrumentation',
    'max-run-time': '3600',
    'network-requirement': 'full',
    'encryption-state': 'encrypted',
  },
};

const CONFIG: DeviceConfig = {
  permissions: {
    owners: ['user-a', 'group-infra-team', 'derekchen'],
    executors: ['test-runner-service-account', 'auto-recovery-service'],
  },
  wifi: {
    type: 'pre-configured',
    ssid: 'GoogleGuest',
    psk: 'some-secure-password',
    scanSsid: true,
  },
  dimensions: {
    supported: [
      {name: 'pool', value: 'pixel-prod'},
      {name: 'os', value: '14'},
    ],
    required: [{name: 'min-sdk', value: '33'}],
  },
  settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
};

/**
 * Represents a mock device scenario where the device is in service and idle.
 * This scenario is used for testing and development purposes to simulate a
 * device in a specific state.
 */
export const SCENARIO_IN_SERVICE_IDLE: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: 'In Service - Idle',
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
