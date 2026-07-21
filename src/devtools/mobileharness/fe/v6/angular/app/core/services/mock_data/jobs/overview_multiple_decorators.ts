import {
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/common_models';
import {MockJobScenario} from '../models';

/** A mock job scenario representing a job with multiple decorators. */
export const SCENARIO_JOB_MULTIPLE_DECORATORS: MockJobScenario = {
  id: 'job-multiple-decorators-1',
  scenarioName: 'Multiple Decorators Job',
  overview: {
    executionDetails: {
      user: 'dafeni',
      actualUser: 'dafeni',
      createTime: '2025-07-15T10:10:30Z',
      startTime: '2025-07-15T10:11:05Z',
      endTime: '2025-07-15T10:11:40Z',
    },
    session: {
      id: '5f2d4e3a-75bd-49cf-beec-d12b6fdf9b69',
      name: 'Gateway Run: Multiple Decorators Test',
      status: SessionStatus.SESSION_STATUS_DONE,
      result: SessionResult.SESSION_RESULT_PASS,
    },
    name: 'multiple_decorators_test_on_mh',
    config: {
      devices: {
        device: [
          {
            deviceType: 'AndroidRealDevice',
            driver: 'AndroidInstrumentation',
            dimensions: {
              'sdk_version': '34',
              'rooted': 'true',
              'brand': 'Google',
              'model': 'Pixel 8',
            },
            decorators: [
              'AndroidSetupDeviceDecorator',
              'ManekiAndroidWebdriverPortForwardDecorator',
            ],
          },
        ],
      },
      settings: {
        totalTestCount: 1,
        priority: 'HIGH',
        testAttempts: 1,
        forceRetry: false,
        retryLevel: 'ERROR',
      },
      params: {
        'param_x': 'value_x',
      },
    },
    tests: {
      test: [
        {
          id: 'd8c4e02d-a7f9-4108-adf9-17d1a70e6f62',
          name: 'MultipleDecoratorsTest#verifyListing',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2025-07-15T10:11:09Z',
          endTime: '2025-07-15T10:11:27Z',
          devices: {
            device: [{id: '99061FFAZ004AA', type: 'AndroidRealDevice'}],
          },
        },
      ],
    },
    id: 'f98a2c1e-7bc6-42ad-a81d-e01b5ecea921',
    status: JobStatus.JOB_STATUS_DONE,
    result: JobResult.JOB_RESULT_PASS,
    spongeLink: 'http://sponge2/f98a2c1e-7bc6-42ad-a81d-e01b5ecea921',
    properties: {
      'test_property': 'multiple_decorators_val',
    },
    timingBreakdown: {
      createTime: '2025-07-15T10:10:30Z',
      startTime: '2025-07-15T10:11:05Z',
      endTime: '2025-07-15T10:11:40Z',
      stages: [
        {
          name: 'Allocation',
          startTime: '2025-07-15T10:10:30Z',
          endTime: '2025-07-15T10:11:05Z',
        },
        {
          name: 'Pre-run Job',
          startTime: '2025-07-15T10:11:05Z',
          endTime: '2025-07-15T10:11:09Z',
        },
        {
          name: 'Running Tests',
          startTime: '2025-07-15T10:11:09Z',
          endTime: '2025-07-15T10:11:40Z',
        },
      ],
    },
  },
  log: '[10:11:05] Job started\n[10:11:09] Allocated device 99061FFAZ004AA\n[10:11:15] Running test-1\n[10:11:27] test-1 passed\n[10:11:40] Job finished successfully.',
  cloudLogLink:
    'https://console.cloud.google.com/logs/query;query=resource.type%3D%22mobileharness_job%22%20AND%20labels.job_id%3D%22f00d-beef-1234-5678-abcde%22',
};
