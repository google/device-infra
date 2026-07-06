import {MockTestScenario} from '../models';
import {SCENARIO_TEST_ERROR} from './overview_error';
import {SCENARIO_TEST_FAILED} from './overview_failed';
import {SCENARIO_TEST_IN_PROGRESS} from './overview_inprogress';
import {SCENARIO_TEST_LONG_NAME} from './overview_longname';
import {SCENARIO_TEST_MULTIPLE_ERRORS_WARNINGS} from './overview_multiple_errors_warnings';
import {SCENARIO_TEST_PASSED} from './overview_passed';
import {SCENARIO_TEST_SKIPPED} from './overview_skipped';
import {SCENARIO_TEST_SUSPENDED} from './overview_suspended';
import {SCENARIO_TEST_TIMEOUT} from './overview_timeout';
import {SCENARIO_TEST_WARNING} from './overview_warning';

/** Central registry of all mock test scenarios. */
export const MOCK_TEST_SCENARIOS: MockTestScenario[] = [
  SCENARIO_TEST_FAILED,
  SCENARIO_TEST_PASSED,
  SCENARIO_TEST_IN_PROGRESS,
  SCENARIO_TEST_WARNING,
  SCENARIO_TEST_MULTIPLE_ERRORS_WARNINGS,
  SCENARIO_TEST_LONG_NAME,
  SCENARIO_TEST_ERROR,
  SCENARIO_TEST_TIMEOUT,
  SCENARIO_TEST_SKIPPED,
  SCENARIO_TEST_SUSPENDED,
];
