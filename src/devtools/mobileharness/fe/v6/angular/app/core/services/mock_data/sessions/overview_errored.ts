import {
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
} from '../../../models/common_models';
import {MockSessionScenario} from '../models';

/** A mock session scenario representing an errored session. */
export const SCENARIO_SESSION_ERRORED: MockSessionScenario = {
  id: 'session-errored',
  scenarioName: 'Errored Session',
  overview: {
    id: 'e5f6a7b8-9012-3456-cdef-0123456789ab',
    name: 'Infrastructure Sanity Check',
    status: SessionStatus.SESSION_STATUS_DONE,
    result: SessionResult.SESSION_RESULT_ERROR,
    client: 'manual-trigger',
    version: 'dev-build',
    executor: '/bns/...',
    labels: ['diag', 'infra'],
    spongeLink: 'http://sponge',
    troubleshooting: {
      resultCause: {
        error: [
          {
            message:
              'LAB_SERVER_OFFLINE: Failed to establish communication with Lab Server.',
            trace:
              'MobileHarnessException: Lab Server mt31-dm01-a-x04 is offline.\n  at com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.RemoteAllocator.allocate(RemoteAllocator.java:102)',
          },
        ],
      },
    },
    executionDetails: {
      user: 'dafeni',
      actualUser: 'dafeni',
      createTime: '2025-07-10T15:05:00Z',
      startTime: '2025-07-10T15:05:05Z',
      endTime: '2025-07-10T15:05:10Z',
    },
    properties: {
      'diagnostics_run': 'true',
    },
    jobs: [
      {
        id: 'job-errored-2',
        name: 'ErroredInfraJob',
        status: JobStatus.JOB_STATUS_DONE,
        result: JobResult.JOB_RESULT_ERROR,
        startTime: '2025-07-10T15:05:05Z',
        endTime: '2025-07-10T15:05:10Z',
      },
    ],
  },
  log: '[15:05:00] Session created\n[15:05:05] Session started\n[15:05:10] FATAL: Lab Server mt31-dm01-a-x04 is offline, unable to allocate devices or compile target jobs.\n[15:05:10] Session errored.',
};
