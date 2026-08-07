import {
  JobStatus,
  SessionResult,
  SessionStatus,
} from '../../../models/common_models';
import {MockSessionScenario} from '../models';

/** A mock session scenario representing a queued session. */
export const SCENARIO_SESSION_QUEUED: MockSessionScenario = {
  id: 'session-queued',
  scenarioName: 'Queued Session',
  overview: {
    id: 'd4e5f6a7-8901-2345-bcde-f0123456789a',
    name: 'Scheduled Weekly Android Smoke Suite',
    status: SessionStatus.SESSION_STATUS_NEW,
    result: SessionResult.SESSION_RESULT_UNSPECIFIED,
    client: 'weekly-cron',
    version: '4.257.0',
    executor: '/bns/...',
    labels: ['weekly', 'android', 'smoke'],
    spongeLink: undefined,
    executionDetails: {
      user: 'nightly-scheduler',
      actualUser: 'nightly-scheduler',
      createTime: '2025-07-10T15:00:00Z',
    },
    properties: {
      'trigger_type': 'weekly_cron',
      'priority': 'LOW',
    },
    jobs: [
      {
        id: 'job-queued-2',
        name: 'QueuedSmokeTests',
        status: JobStatus.JOB_STATUS_NEW,
      },
    ],
  },
  log: '[15:00:00] Session queued, waiting for runner allocation...\n',
};
