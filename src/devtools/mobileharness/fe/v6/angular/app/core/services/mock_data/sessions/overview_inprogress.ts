import {
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
} from '../../../models/common_models';
import {MockSessionScenario} from '../models';

/** A mock session scenario representing an in-progress session. */
export const SCENARIO_SESSION_INPROGRESS: MockSessionScenario = {
  id: 'session-inprogress',
  scenarioName: 'In Progress Session',
  overview: {
    id: 'b2c3d4e5-f6a7-8901-hijk-lmnopqrstuvwx',
    name: 'Manual Dev Build Validation',
    status: SessionStatus.SESSION_STATUS_RUNNING,
    result: SessionResult.SESSION_RESULT_UNSPECIFIED,
    client: 'manual-trigger',
    version: 'dev-branch',
    executor: '/bns/...',
    labels: ['manual', 'validation'],
    spongeLink: 'http://sponge',
    executionDetails: {
      user: 'some-developer',
      actualUser: 'some-developer',
      createTime: '2025-07-10T11:00:00Z',
      startTime: '2025-07-10T11:00:05Z',
    },
    properties: {},
    jobs: [
      {
        id: 'job-passed-4',
        name: 'BuildCompilationTest',
        status: JobStatus.JOB_STATUS_DONE,
        result: JobResult.JOB_RESULT_PASS,
        startTime: '2025-07-10T11:00:10Z',
        endTime: '2025-07-10T11:05:00Z',
      },
      {
        id: 'job-inprogress-1',
        name: 'IntegrationSuite',
        status: JobStatus.JOB_STATUS_RUNNING,
        startTime: '2025-07-10T11:05:05Z',
      },
    ],
  },
  log: '[11:00:05] Session started...\n[11:00:10] job-passed-4 started.\n[11:05:00] job-passed-4 finished.\n[11:05:05] job-inprogress-1 started...',
};
