/**
 * @fileoverview Mock data for the "Config: Empty Config Device" scenario.
 * This represents a device that is healthy but has no configuration set.
 * This state should trigger the "Guided Setup" flow in the UI.
 */

import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'EMPTY-CONFIG-DEVICE';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-y.example.com', ip: '192.168.25.125'},
  healthAndActivity: {
    title: 'In Service (Idle)',
    subtitle: 'The device is healthy and ready for new tasks.',
    state: 'IN_SERVICE_IDLE',
    deviceStatus: {status: 'IDLE', isCritical: false},
    deviceTypes: [{type: 'AndroidDevice', isAbnormal: false}],
    lastInServiceTime: new Date().toISOString(),
  },
  basicInfo: {
    model: 'Pixel 7a',
    version: '13',
    form: 'physical',
    os: 'Android',
    batteryLevel: 91,
    network: {wifiRssi: -62, hasInternet: true},
    hardware: 'g/22def',
    build: 'TQ3A.230805.001',
  },
  permissions: {owners: [], executors: []},
  capabilities: {supportedDrivers: [], supportedDecorators: []},
  dimensions: {supported: {}, required: {}},
  properties: {},
};

/**
 * Represents a mock device scenario with an empty configuration. This scenario
 * is used for testing and development purposes to simulate a device in a
 * specific state.
 */
export const SCENARIO_EMPTY_CONFIG: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: 'Empty Config',
  overview: OVERVIEW,
  config: null,
};
