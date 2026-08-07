import {
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
} from '../../../models/common_models';
import {MockSessionScenario} from '../models';

/** A mock session scenario representing an aborted/cancelled session. */
export const SCENARIO_SESSION_ABORTED: MockSessionScenario = {
  id: 'session-aborted',
  scenarioName: 'Aborted Session',
  overview: {
    id: 'c3d4e5f6-a7b8-9012-ijkl-mnopqrstuvwxy',
    name: 'Cancelled Staging Rollout',
    status: SessionStatus.SESSION_STATUS_DONE,
    result: SessionResult.SESSION_RESULT_ABORT,
    client: 'staging-deployer',
    version: '5.1.0-rc1',
    executor:
      '/bns/pf/borg/pf/bns/mobileharness-gateway/mobileharness-gateway.server/17',
    labels: ['staging', 'release'],
    spongeLink: 'http://sponge',
    troubleshooting: {
      resultCause: {
        error: [
          {
            message: 'Session was manually aborted by release-manager.',
            trace: '',
          },
        ],
      },
    },
    executionDetails: {
      user: 'release-manager',
      actualUser: 'release-manager',
      createTime: '2025-07-10T15:00:00Z',
      startTime: '2025-07-10T15:00:05Z',
      endTime: '2025-07-10T15:05:00Z',
    },
    properties: {
      'reason_for_abort': 'Manual user intervention.',
    },
    jobs: [
      {
        id: 'job-passed-5',
        name: 'StagingHealthCheck',
        status: JobStatus.JOB_STATUS_DONE,
        result: JobResult.JOB_RESULT_PASS,
        startTime: '2025-07-10T15:00:10Z',
        endTime: '2025-07-10T15:04:00Z',
      },
    ],
  },
  log: 'Session aborted by user.',
};
