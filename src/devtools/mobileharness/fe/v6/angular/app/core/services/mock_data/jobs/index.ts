import {MockJobScenario} from '../models';
import {SCENARIO_JOB_ABORTED} from './overview_aborted';
import {SCENARIO_JOB_ERRORED} from './overview_errored';
import {SCENARIO_JOB_FAILED} from './overview_failed';
import {SCENARIO_JOB_IN_PROGRESS} from './overview_inprogress';
import {SCENARIO_JOB_MANY_TESTS} from './overview_many_tests';
import {SCENARIO_JOB_MULTI_DEVICE} from './overview_multi_device';
import {SCENARIO_JOB_PASSED} from './overview_passed';
import {SCENARIO_JOB_QUEUED} from './overview_queued';
import {SCENARIO_JOB_MULTIPLE_DECORATORS} from './overview_multiple_decorators';

/** Central registry of all mock job scenarios. */
export const MOCK_JOB_SCENARIOS: MockJobScenario[] = [
  SCENARIO_JOB_FAILED,
  SCENARIO_JOB_PASSED,
  SCENARIO_JOB_MULTIPLE_DECORATORS,
  SCENARIO_JOB_IN_PROGRESS,
  SCENARIO_JOB_MANY_TESTS,
  SCENARIO_JOB_MULTI_DEVICE,
  SCENARIO_JOB_ERRORED,
  SCENARIO_JOB_ABORTED,
  SCENARIO_JOB_QUEUED,
];
