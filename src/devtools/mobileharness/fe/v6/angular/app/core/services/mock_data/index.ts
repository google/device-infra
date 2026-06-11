/**
 * @fileoverview Main registry for all mock data scenarios.
 * This file serves as the single source of truth for importing and exporting
 * mock data arrays used by fake services throughout the application.
 */

export {MOCK_DEVICE_SCENARIOS} from './devices';
export {MOCK_HOST_SCENARIOS} from './hosts';
export {MOCK_TEST_SCENARIOS} from './tests';

export type {
  MockDeviceScenario,
  MockHostScenario,
  MockTestScenario,
} from './models';
