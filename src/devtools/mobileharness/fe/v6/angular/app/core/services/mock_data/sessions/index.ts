import {SCENARIO_SESSION_ABORTED} from './overview_aborted';
import {SCENARIO_SESSION_ERRORED} from './overview_errored';
import {SCENARIO_SESSION_FAILED} from './overview_failed';
import {SCENARIO_SESSION_INPROGRESS} from './overview_inprogress';
import {SCENARIO_SESSION_MANYJOBS} from './overview_manyjobs';
import {SCENARIO_SESSION_PASSED} from './overview_passed';
import {SCENARIO_SESSION_QUEUED} from './overview_queued';

/** Mock session scenario datasets for local development and testing. */
export const MOCK_SESSION_SCENARIOS = [
  SCENARIO_SESSION_FAILED,
  SCENARIO_SESSION_PASSED,
  SCENARIO_SESSION_INPROGRESS,
  SCENARIO_SESSION_ABORTED,
  SCENARIO_SESSION_QUEUED,
  SCENARIO_SESSION_ERRORED,
  SCENARIO_SESSION_MANYJOBS,
];
