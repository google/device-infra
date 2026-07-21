import {
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/common_models';
import {JobFile} from '@deviceinfra/app/core/models/job_overview';
import {MockJobScenario} from '../models';

/** A mock job scenario representing a failed job. */
export const SCENARIO_JOB_FAILED: MockJobScenario = {
  id: 'job-failed-1',
  scenarioName: 'Failed Job',
  overview: {
    executionDetails: {
      user: 'dafeni',
      actualUser: 'dafeni',
      createTime: '2025-07-09T10:10:35Z',
      startTime: '2025-07-09T10:12:00Z',
      endTime: '2025-07-09T10:12:40Z',
    },
    session: {
      id: '4f1e3f2e-64ac-48be-adfb-c01a5ece8a58',
      name: 'Gateway Run: MobileHarness',
      status: SessionStatus.SESSION_STATUS_DONE,
      result: SessionResult.SESSION_RESULT_FAIL,
    },
    name: 'hello_mobile_harness_test_on_mh',
    config: {
      devices: {
        device: [
          {
            deviceType: 'AndroidRealDevice',
            driver: 'AndroidInstrumentation',
            dimensions: {
              'sdk_version': '33',
              'rooted': 'true',
            },
          },
        ],
      },
      settings: {
        totalTestCount: 3,
        priority: 'DEFAULT',
        testAttempts: 2,
        forceRetry: false,
        retryLevel: 'ERROR',
      },
      params: {
        'param_a': 'value_a',
      },
    },
    tests: {
      test: [
        {
          id: '29226bb4-6e61-455a-aad2-b68a06ffb841',
          name: 'HelloMobileHarnessTest#buttonText',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_FAIL,
          startTime: '2025-07-09T10:12:00Z',
          endTime: '2025-07-09T10:12:40Z',
          devices: {
            device: [{id: '43021FDAQ000UM', type: 'AndroidRealDevice'}],
          },
        },
      ],
    },
    id: 'c3578d2a-776d-49e3-b065-c66624b9d665',
    status: JobStatus.JOB_STATUS_DONE,
    result: JobResult.JOB_RESULT_FAIL,
    spongeLink: 'http://sponge2/c3578d2a-776d-49e3-b065-c66624b9d665',
    properties: {
      'job_failure_reason': 'test_failed',
    },
    timingBreakdown: {
      createTime: '2025-07-09T10:10:35Z',
      startTime: '2025-07-09T10:12:00Z',
      endTime: '2025-07-09T10:12:40Z',
      stages: [
        {
          name: 'Allocation',
          startTime: '2025-07-09T10:10:35Z',
          endTime: '2025-07-09T10:12:00Z',
        },
        {
          name: 'Pre-run Job',
          startTime: '2025-07-09T10:12:00Z',
          endTime: '2025-07-09T10:12:10Z',
        },
        {
          name: 'Running Tests',
          startTime: '2025-07-09T10:12:10Z',
          endTime: '2025-07-09T10:12:40Z',
        },
      ],
    },
    fileExplorer: {
      cnsPath:
        '/cnsexample/sa-d/home/mobileharness/gateway/20260712/s_726e8c21-7e1c-4576-aa0f-51e0d4c3d86a/j_1845eb94-459b-4028-a9b4-0f13558fdc61/',
      files: [
        {
          path: 'job_output.txt',
          size: 16384,
          type: 'text/plain',
          content: 'test output log\nJob errored or test failed.',
        },
        {
          path: 'undeclared_outputs.zip',
          size: 140288,
          type: 'application/zip',
          content: '',
        },
      ] as unknown as JobFile[],
    },
  },
  log: '[10:12:00] Job started\n[10:12:05] Allocated device 43021FDAQ000UM\n[10:12:10] Running test-3\n[10:12:40] test-3 failed.\n[10:12:40] Job failed.',
  cloudLogLink:
    'https://console.cloud.google.com/logs/query;query=resource.type%3D%22mobileharness_job%22%20AND%20labels.job_id%3D%22c3578d2a-776d-49e3-b065-c66624b9d665%22',
};
