import {JobStatus} from '@deviceinfra/app/core/models/job_overview';
import {MockJobScenario} from '../models';
import {SCENARIO_JOB_ABORTED} from './overview_aborted';
import {SCENARIO_JOB_ASSIGNED} from './overview_assigned';
import {SCENARIO_JOB_ERRORED} from './overview_errored';
import {SCENARIO_JOB_FAILED} from './overview_failed';
import {SCENARIO_JOB_IN_PROGRESS} from './overview_inprogress';
import {SCENARIO_JOB_MANY_TESTS} from './overview_many_tests';
import {SCENARIO_JOB_MULTI_DEVICE} from './overview_multi_device';
import {SCENARIO_JOB_MULTIPLE_DECORATORS} from './overview_multiple_decorators';
import {SCENARIO_JOB_PASSED} from './overview_passed';
import {SCENARIO_JOB_QUEUED} from './overview_queued';

const RAW_MOCK_JOB_SCENARIOS: MockJobScenario[] = [
  SCENARIO_JOB_FAILED,
  SCENARIO_JOB_PASSED,
  SCENARIO_JOB_MULTIPLE_DECORATORS,
  SCENARIO_JOB_IN_PROGRESS,
  SCENARIO_JOB_MANY_TESTS,
  SCENARIO_JOB_MULTI_DEVICE,
  SCENARIO_JOB_ERRORED,
  SCENARIO_JOB_ABORTED,
  SCENARIO_JOB_QUEUED,
  SCENARIO_JOB_ASSIGNED,
];

/** Central registry of all mock job scenarios. */
export const MOCK_JOB_SCENARIOS: MockJobScenario[] = RAW_MOCK_JOB_SCENARIOS.map(
  (scenario) => {
    const isKillableStatus =
      scenario.overview.status === JobStatus.JOB_STATUS_RUNNING ||
      scenario.overview.status === JobStatus.JOB_STATUS_NEW ||
      scenario.overview.status === JobStatus.JOB_STATUS_ASSIGNED;
    const defaultKillAction = {
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
        kill: defaultKillAction,
      },
      overview: {
        ...scenario.overview,
      },
    };
  },
);
