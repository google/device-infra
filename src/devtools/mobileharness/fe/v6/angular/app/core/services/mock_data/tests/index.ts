import {JobStatus} from '@deviceinfra/app/core/models/test_overview';
import {MockTestScenario} from '../models';
import {SCENARIO_TEST_ERROR} from './overview_error';
import {SCENARIO_TEST_FAILED} from './overview_failed';
import {SCENARIO_TEST_FILES} from './overview_files';
import {SCENARIO_TEST_IN_PROGRESS} from './overview_inprogress';
import {SCENARIO_TEST_LONG_NAME} from './overview_longname';
import {SCENARIO_TEST_MULTIPLE_ERRORS_WARNINGS} from './overview_multiple_errors_warnings';
import {SCENARIO_TEST_PASSED} from './overview_passed';
import {SCENARIO_TEST_SKIPPED} from './overview_skipped';
import {SCENARIO_TEST_SUSPENDED} from './overview_suspended';
import {SCENARIO_TEST_TIMEOUT} from './overview_timeout';
import {SCENARIO_TEST_WARNING} from './overview_warning';

const RAW_MOCK_TEST_SCENARIOS: MockTestScenario[] = [
  SCENARIO_TEST_FILES,
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

/** Central registry of all mock test scenarios. */
export const MOCK_TEST_SCENARIOS: MockTestScenario[] =
  RAW_MOCK_TEST_SCENARIOS.map((scenario) => {
    const isKillableStatus =
      scenario.overview.job?.status === JobStatus.JOB_STATUS_RUNNING ||
      scenario.overview.job?.status === JobStatus.JOB_STATUS_NEW ||
      scenario.overview.job?.status === JobStatus.JOB_STATUS_ASSIGNED;
    const defaultKillJobAction = {
      enabled: isKillableStatus,
      visible: isKillableStatus,
      tooltip: isKillableStatus
        ? 'Click to terminate this running job immediately.'
        : `Permission denied. Only the owner (${scenario.overview.executionDetails?.user || 'unknown'}) or admins can kill this job.`,
      isReady: true,
    };
    return {
      ...scenario,
      actions: scenario.actions || {
        killJob: defaultKillJobAction,
      },
      overview: {
        ...scenario.overview,
      },
    };
  });
