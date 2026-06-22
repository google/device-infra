import {
  JobResult,
  JobStatus,
} from '@deviceinfra/app/core/models/common_models';
import {MockJobScenario} from '../models';

/** A mock job scenario representing an aborted job. */
export const SCENARIO_JOB_ABORTED: MockJobScenario = {
  id: 'job-aborted-1',
  scenarioName: 'Aborted Job',
  overview: {
    executionDetails: {
      user: 'blaze-user',
      actualUser: 'g3-ci-system',
      createTime: '2025-07-09T12:00:00Z',
      startTime: '2025-07-09T12:00:05Z',
      endTime: '2025-07-09T12:02:00Z',
    },
    name: 'adhoc_g3_ci_cancellation_run',
    config: {
      devices: {
        device: [
          {
            deviceType: 'AndroidRealDevice',
            driver: 'AndroidInstrumentation',
          },
        ],
      },
      params: {},
    },
    tests: {
      test: [],
    },
    id: 'f1a8e532-0ac4-4d89-8260-8012626e2e4f',
    status: JobStatus.JOB_STATUS_DONE,
    result: JobResult.JOB_RESULT_ABORT,
    spongeLink: 'http://sponge2/f1a8e532-0ac4-4d89-8260-8012626e2e4f',
    properties: {
      'aborted_by': 'g3-ci-scheduler',
    },
    timingBreakdown: {
      createTime: '2025-07-09T12:00:00Z',
      startTime: '2025-07-09T12:00:05Z',
      endTime: '2025-07-09T12:02:00Z',
      stages: [],
    },
  },
  log: '[12:00:05] Job started\n[12:01:00] Allocating devices...\n[12:02:00] ABORT: Cancelled by user or upstream scheduler.\n[12:02:00] Job aborted.',
  cloudLogLink:
    'https://console.cloud.google.com/logs/query;query=resource.type%3D%22mobileharness_job%22%20AND%20labels.job_id%3D%22f1a8e532-0ac4-4d89-8260-8012626e2e4f%22',
};
