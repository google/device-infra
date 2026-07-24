/**
 * @fileoverview Mock data for the "Coming Soon" device scenario.
 * This scenario represents a device where all action bar items are not ready.
 */

import {MockDeviceScenario} from '../models';
import {SCENARIO_IN_SERVICE_IDLE} from './01_in_service_idle';

/**
 * Represents a mock device scenario where all action bar items are not ready.
 * This scenario is used for testing and development purposes to simulate a
 * device in a specific state.
 */
const SCENARIO_COMING_SOON_DATA: MockDeviceScenario = {
  ...SCENARIO_IN_SERVICE_IDLE(),
  id: 'buttons-not-implemented.example.com',
  overview: {
    ...SCENARIO_IN_SERVICE_IDLE().overview,
    id: 'buttons-not-implemented.example.com',
    host: {
      name: 'buttons-not-implemented.host.example.com',
      ip: '192.168.1.1',
    },
  },
  scenarioName: 'Coming Soon - All Actions Not Ready',
  allActionsNotReady: true,
};

/**
 * Returns the mock scenario for coming soon.
 */
export function SCENARIO_COMING_SOON(callCount?: number): MockDeviceScenario {
  return SCENARIO_COMING_SOON_DATA;
}
