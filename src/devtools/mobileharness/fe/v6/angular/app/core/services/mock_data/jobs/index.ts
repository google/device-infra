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

function createClonedJobScenario(
  base: MockJobScenario,
  opts: {
    id: string;
    scenarioName: string;
    name?: string;
    user?: string;
  },
): MockJobScenario {
  return {
    ...base,
    id: opts.id,
    scenarioName: opts.scenarioName,
    overview: {
      ...base.overview,
      id: opts.id,
      name: opts.name ?? base.overview.name,
      executionDetails: base.overview.executionDetails
        ? {
            ...base.overview.executionDetails,
            user: opts.user ?? base.overview.executionDetails.user,
            actualUser: opts.user ?? base.overview.executionDetails.actualUser,
          }
        : undefined,
    },
  };
}

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
  createClonedJobScenario(SCENARIO_JOB_PASSED, {
    id: 'job-id-for-1-hit-1',
    scenarioName: 'All-Site 1-Hit Job #1 (Passed)',
    name: 'passed_regression_suite',
    user: 'qiupingf',
  }),
  createClonedJobScenario(SCENARIO_JOB_IN_PROGRESS, {
    id: 'job-id-for-1-hit-2',
    scenarioName: 'All-Site 1-Hit Job #2 (In Progress)',
    name: 'long_running_reliability_test',
    user: 'qiupingf',
  }),
  createClonedJobScenario(SCENARIO_JOB_PASSED, {
    id: 'tjs-id-for-ambiguous-1',
    scenarioName: 'All-Site Ambiguous TJS: Job Match',
    name: 'passed_regression_suite',
    user: 'qiupingf',
  }),
  createClonedJobScenario(SCENARIO_JOB_IN_PROGRESS, {
    id: 'j_987654',
    scenarioName: 'TJS Search Job 1: MobileHarness Core Tests',
    name: 'MobileHarness Core Tests',
    user: 'qiupingf',
  }),
  createClonedJobScenario(SCENARIO_JOB_PASSED, {
    id: 'j_765432',
    scenarioName: 'TJS Search Job 2: OmniLab Smoke Tests',
    name: 'OmniLab Smoke Tests',
    user: 'qiupingf',
  }),
  createClonedJobScenario(SCENARIO_JOB_PASSED, {
    id: 'j_876543',
    scenarioName: 'TJS Search Job 3: Device Infra Integration',
    name: 'Device Infra Integration',
    user: 'dev_user',
  }),
  createClonedJobScenario(SCENARIO_JOB_PASSED, {
    id: 'd29b84c4-9e7f-4104-8cc8-c5d70bc9d481',
    scenarioName: 'Passed Test Parent Job',
    name: 'com.google.android.apps.photos.BackupAndSyncJob',
    user: 'dafeni',
  }),
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
