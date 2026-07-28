import {MockDeviceScenario} from '../models';
import {SCENARIO_IN_SERVICE_IDLE as SCENARIO_IN_SERVICE_IDLE_FN} from './01_in_service_idle';

const SCENARIO_IN_SERVICE_IDLE = SCENARIO_IN_SERVICE_IDLE_FN();

/** Mock scenario for a device that triggers a Permission Denied error. */
const SCENARIO_ERROR_PERMISSION_DENIED_DATA: MockDeviceScenario = {
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
const SCENARIO_ERROR_LOGICAL_DATA: MockDeviceScenario = {
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
const SCENARIO_ERROR_RPC_DATA: MockDeviceScenario = {
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

/** Scenario where device action fails with permission denied error. */
export function scenarioErrorPermissionDenied(
  callCount?: number,
): MockDeviceScenario {
  return SCENARIO_ERROR_PERMISSION_DENIED_DATA;
}

/** Scenario where device action fails with logical error (e.g. invalid state). */
export function scenarioErrorLogical(callCount?: number): MockDeviceScenario {
  return SCENARIO_ERROR_LOGICAL_DATA;
}

/** Scenario where device action fails with RPC error. */
export function scenarioErrorRpc(callCount?: number): MockDeviceScenario {
  return SCENARIO_ERROR_RPC_DATA;
}
