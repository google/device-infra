import {MockDeviceScenario} from '../models';
import {SCENARIO_IN_SERVICE_IDLE} from './01_in_service_idle';

/** Mock scenario for a device that triggers a Permission Denied error. */
export const SCENARIO_ERROR_PERMISSION_DENIED: MockDeviceScenario = {
  ...SCENARIO_IN_SERVICE_IDLE,
  id: 'android-permission-denied-01',
  scenarioName: '27. Error: Permission Denied (Demo)',
  overview: {
    ...SCENARIO_IN_SERVICE_IDLE.overview,
    id: 'android-permission-denied-01',
    healthAndActivity: {
      ...SCENARIO_IN_SERVICE_IDLE.overview.healthAndActivity,
      subtitle:
        'This device is configured to trigger errors for demo: Permission Denied',
    },
  },
};

/** Mock scenario for a device that triggers a logical action error. */
export const SCENARIO_ERROR_LOGICAL: MockDeviceScenario = {
  ...SCENARIO_IN_SERVICE_IDLE,
  id: 'android-logical-error-01',
  scenarioName: '28. Error: Logical Action Failed (Demo)',
  overview: {
    ...SCENARIO_IN_SERVICE_IDLE.overview,
    id: 'android-logical-error-01',
    healthAndActivity: {
      ...SCENARIO_IN_SERVICE_IDLE.overview.healthAndActivity,
      subtitle:
        'This device is configured to trigger errors for demo: Logical Error',
    },
  },
};

/** Mock scenario for a device that triggers an RPC error. */
export const SCENARIO_ERROR_RPC: MockDeviceScenario = {
  ...SCENARIO_IN_SERVICE_IDLE,
  id: 'android-rpc-error-01',
  scenarioName: '29. Error: RPC Exception (Demo)',
  overview: {
    ...SCENARIO_IN_SERVICE_IDLE.overview,
    id: 'android-rpc-error-01',
    healthAndActivity: {
      ...SCENARIO_IN_SERVICE_IDLE.overview.healthAndActivity,
      subtitle:
        'This device is configured to trigger errors for demo: RPC Exception',
    },
  },
};
