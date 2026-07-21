import {MockDeviceScenario} from '../models';
import {SCENARIO_IN_SERVICE_IDLE} from './01_in_service_idle';

/** Scenario for testing device refresh/failure retention logic. */
export const SCENARIO_DEVICE_REFRESH: MockDeviceScenario = {
  ...SCENARIO_IN_SERVICE_IDLE,
  scenarioName: 'Device Refresh Test Scenario',
  id: 'refresh-device-id',
  overview: {
    ...SCENARIO_IN_SERVICE_IDLE.overview,
    id: 'refresh-device-id',
  },
};
