import {
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
} from '@deviceinfra/app/core/models/common_models';
import {MockJobScenario} from '../models';

/** A mock job scenario representing a queued job. */
export const SCENARIO_JOB_QUEUED: MockJobScenario = {
  id: 'job-queued-1',
  scenarioName: 'Queued Job',
  overview: {
    executionDetails: {
      user: 'dafeni',
      actualUser: 'dafeni',
      createTime: '2025-07-10T03:00:00Z',
    },
    session: {
      id: 'a1b2c3d4-e5f6-7890-abcd-ef0123456789',
      name: 'Nightly Pixel 8 Pro Tests',
      status: SessionStatus.SESSION_STATUS_DONE,
      result: SessionResult.SESSION_RESULT_PASS,
    },
    name: 'scheduled_daily_smoke_tests',
    config: {
      devices: {
        device: [
          {
            deviceType: 'AndroidRealDevice',
            driver: 'AndroidInstrumentation',
            dimensions: {
              'sdk_version': '34',
            },
          },
        ],
      },
      settings: {
        totalTestCount: 5,
        priority: 'LOW',
      },
      params: {},
    },
    tests: {
      test: [],
    },
    id: '99e8e532-0ac4-4d89-8260-8012626e2e4f',
    status: JobStatus.JOB_STATUS_NEW,
    result: JobResult.JOB_RESULT_UNSPECIFIED,
    spongeLink: 'http://sponge2/99e8e532-0ac4-4d89-8260-8012626e2e4f',
    properties: {
      'trigger': 'cron',
    },
    timingBreakdown: {
      createTime: '2025-07-10T03:00:00Z',
      stages: [],
    },
  },
  log: 'Waiting in queue for device allocation...',
  cloudLogLink:
    'https://console.cloud.google.com/logs/query;query=resource.type%3D%22mobileharness_job%22%20AND%20labels.job_id%3D%2299e8e532-0ac4-4d89-8260-8012626e2e4f%22',
};
