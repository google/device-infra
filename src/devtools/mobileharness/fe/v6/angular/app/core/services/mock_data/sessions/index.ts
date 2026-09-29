import {MockSessionScenario} from '../models';
import {SCENARIO_SESSION_ABORTED} from './overview_aborted';
import {SCENARIO_SESSION_ERRORED} from './overview_errored';
import {SCENARIO_SESSION_FAILED} from './overview_failed';
import {SCENARIO_SESSION_INPROGRESS} from './overview_inprogress';
import {SCENARIO_SESSION_MANYJOBS} from './overview_manyjobs';
import {SCENARIO_SESSION_PASSED} from './overview_passed';
import {SCENARIO_SESSION_QUEUED} from './overview_queued';

function createClonedSessionScenario(
  base: MockSessionScenario,
  opts: {
    id: string;
    scenarioName: string;
    name?: string;
    user?: string;
  },
): MockSessionScenario {
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

/** Mock session scenario datasets for local development and testing. */
export const MOCK_SESSION_SCENARIOS: MockSessionScenario[] = [
  SCENARIO_SESSION_FAILED,
  SCENARIO_SESSION_PASSED,
  SCENARIO_SESSION_INPROGRESS,
  SCENARIO_SESSION_ABORTED,
  SCENARIO_SESSION_QUEUED,
  SCENARIO_SESSION_ERRORED,
  SCENARIO_SESSION_MANYJOBS,
  createClonedSessionScenario(SCENARIO_SESSION_PASSED, {
    id: 'session-id-for-1-hit-1',
    scenarioName: 'All-Site 1-Hit Session #1 (Passed)',
    name: 'Daily Presubmit Verification',
    user: 'qiupingf',
  }),
  createClonedSessionScenario(SCENARIO_SESSION_INPROGRESS, {
    id: 'session-id-for-1-hit-2',
    scenarioName: 'All-Site 1-Hit Session #2 (In Progress)',
    name: 'Manual Dev Build Validation',
    user: 'qiupingf',
  }),
  createClonedSessionScenario(SCENARIO_SESSION_PASSED, {
    id: 'tjs-id-for-ambiguous-1',
    scenarioName: 'All-Site Ambiguous TJS: Session Match',
    name: 'Daily Presubmit Verification',
    user: 'qiupingf',
  }),
  createClonedSessionScenario(SCENARIO_SESSION_PASSED, {
    id: 's_abcdef',
    scenarioName: 'TJS Search Session 1: s_abcdef',
    name: 'Daily Presubmit Verification',
    user: 'qiupingf',
  }),
  createClonedSessionScenario(SCENARIO_SESSION_INPROGRESS, {
    id: 's_fedcba',
    scenarioName: 'TJS Search Session 2: s_fedcba',
    name: 'Manual Dev Build Validation',
    user: 'qiupingf',
  }),
  createClonedSessionScenario(SCENARIO_SESSION_INPROGRESS, {
    id: 's_123456',
    scenarioName: 'TJS Search Session 3: s_123456',
    name: 'Manual Dev Build Validation',
    user: 'dev_user',
  }),
];
