/**
 * @fileoverview Mock device scenarios matching local MTT development environment.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const HOST_NAME = 'mtt-host.example.com';

function createLocalMttDeviceScenario(index: number): MockDeviceScenario {
  const id = `${HOST_NAME}:NoOpDevice-${index}`;
  const overview: DeviceOverview = {
    id,
    host: {name: HOST_NAME, ip: '127.0.0.1'},
    healthAndActivity: {
      title: 'In Service (Idle)',
      subtitle: 'The device is healthy and ready for new tasks.',
      state: 'IN_SERVICE_IDLE',
      deviceStatus: {status: 'IDLE', isCritical: false},
      deviceTypes: [{type: 'NoOpDevice', isAbnormal: false}],
      lastInServiceTime: new Date().toISOString(),
    },
    basicInfo: {
      model: 'NoOpDevice',
      version: '1.0',
      form: 'virtual',
      os: 'NoOp',
      batteryLevel: null,
      network: {},
    },
    permissions: {
      owners: ['tianch'],
      executors: ['tianch'],
    },
    capabilities: {
      supportedDrivers: ['NoOpDriver'],
      supportedDecorators: [],
    },
    dimensions: {
      supported: {
        'Detected by OmniLab': {
          dimensions: [{name: 'label', value: `NoOpDevice-${index}`}],
        },
      },
      required: {},
    },
    properties: {},
  };

  const config: DeviceConfig = {
    permissions: {
      owners: ['tianch'],
      executors: ['tianch'],
    },
    wifi: {
      type: 'none',
      ssid: '',
      psk: '',
      scanSsid: false,
    },
    dimensions: {
      supported: [],
      required: [],
    },
    settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
  };

  return {
    id,
    scenarioName: `Local MTT NoOpDevice-${index}`,
    overview,
    config,
    isQuarantined: false,
    actionVisibility: {
      screenshot: false,
      logcat: false,
      flash: false,
      remoteControl: false,
      quarantine: false,
    },
  };
}

/**
 * List of mock device scenario factories for local MTT devices.
 */
export const SCENARIOS_LOCAL_MTT_DEVICES: Array<() => MockDeviceScenario> = [
  () => createLocalMttDeviceScenario(0),
  () => createLocalMttDeviceScenario(1),
  () => createLocalMttDeviceScenario(2),
  () => createLocalMttDeviceScenario(3),
  () => createLocalMttDeviceScenario(4),
];
