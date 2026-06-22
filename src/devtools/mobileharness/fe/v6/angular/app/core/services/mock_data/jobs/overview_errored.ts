import {
  JobResult,
  JobStatus,
} from '@deviceinfra/app/core/models/common_models';
import {MockJobScenario} from '../models';

/** A mock job scenario representing an errored job. */
export const SCENARIO_JOB_ERRORED: MockJobScenario = {
  id: 'job-errored-1',
  scenarioName: 'Errored Job',
  overview: {
    name: 'errored_job_run',
    executionDetails: {
      user: 'dafeni',
      actualUser: 'dafeni',
      createTime: '2025-07-09T10:15:00Z',
      startTime: '2025-07-09T10:15:05Z',
      endTime: '2025-07-09T10:15:10Z',
    },
    config: {
      params: {},
    },
    tests: {
      test: [],
    },
    id: 'e9a8e532-0ac4-4d89-8260-8012626e2e4f',
    status: JobStatus.JOB_STATUS_DONE,
    result: JobResult.JOB_RESULT_ERROR,
    spongeLink: 'http://sponge2/e9a8e532-0ac4-4d89-8260-8012626e2e4f',
    properties: {
      'infra_error_code': 'LAB_SERVER_OFFLINE',
    },
    timingBreakdown: {
      createTime: '2025-07-09T10:15:00Z',
      startTime: '2025-07-09T10:15:05Z',
      endTime: '2025-07-09T10:15:10Z',
      stages: [],
    },
  },
  log: '[10:15:05] Job started\n[10:15:05] Initializing lab server connection...\n[10:15:10] ERROR: Lab server is offline, unable to allocate devices.\n[10:15:10] Job errored.',
  cloudLogLink:
    'https://console.cloud.google.com/logs/query;query=resource.type%3D%22mobileharness_job%22%20AND%20labels.job_id%3D%22e9a8e532-0ac4-4d89-8260-8012626e2e4f%22',
};
