import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/common_models';
import {MockJobScenario} from '../models';

/** A mock job scenario representing an in-progress job. */
export const SCENARIO_JOB_IN_PROGRESS: MockJobScenario = {
  id: 'job-inprogress-1',
  scenarioName: 'In Progress Job',
  overview: {
    executionDetails: {
      user: 'dafeni',
      actualUser: 'dafeni',
      createTime: '2025-07-09T11:30:00Z',
      startTime: '2025-07-09T11:30:05Z',
    },
    name: 'long_running_reliability_test',
    config: {
      devices: {
        device: [
          {
            deviceType: 'AndroidRealDevice',
            driver: 'AndroidInstrumentation',
          },
        ],
      },
      settings: {
        totalTestCount: 1,
      },
      params: {},
    },
    tests: {
      test: [
        {
          id: 'test-inprogress-1',
          name: 'com.google.devtools.mobileharness.infra.HarnessActiveTest#testRun',
          status: TestStatus.TEST_STATUS_RUNNING,
          result: TestResult.TEST_RESULT_UNSPECIFIED,
          startTime: '2025-07-09T11:30:10Z',
          devices: {
            device: [{id: '4D5A1FDAB001BB', type: 'AndroidRealDevice'}],
          },
        },
      ],
    },
    id: 'a1e8e532-0ac4-4d89-8260-8012626e2e4f',
    status: JobStatus.JOB_STATUS_RUNNING,
    result: JobResult.JOB_RESULT_UNSPECIFIED,
    spongeLink: 'http://sponge2/a1e8e532-0ac4-4d89-8260-8012626e2e4f',
    properties: {},
    timingBreakdown: {
      createTime: '2025-07-09T11:30:00Z',
      startTime: '2025-07-09T11:30:05Z',
      stages: [
        {
          name: 'Allocation',
          startTime: '2025-07-09T11:30:00Z',
          endTime: '2025-07-09T11:30:05Z',
        },
        {
          name: 'Pre-run Job',
          startTime: '2025-07-09T11:30:05Z',
          endTime: '2025-07-09T11:30:10Z',
        },
        {
          name: 'Running Tests',
          startTime: '2025-07-09T11:30:10Z',
        },
      ],
    },
  },
  log: '[11:30:05] Job started\n[11:30:10] Allocated device 4D5A1FDAB001BB\n[11:30:15] Running test fdda90bf... ',
  cloudLogLink:
    'https://console.cloud.google.com/logs/query;query=resource.type%3D%22mobileharness_job%22%20AND%20labels.job_id%3D%22a1e8e532-0ac4-4d89-8260-8012626e2e4f%22',
};
