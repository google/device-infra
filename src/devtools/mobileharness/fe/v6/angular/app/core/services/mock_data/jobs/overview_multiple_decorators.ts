import {MockJobScenario} from '../models';

/** A mock job scenario representing a job with multiple decorators. */
export const SCENARIO_JOB_MULTIPLE_DECORATORS: MockJobScenario = {
  id: 'job-multiple-decorators',
  scenarioName: 'Multiple Decorators',
  overview: {
    id: 'f98a2c1e-7bc6-42ad-a81d-e01b5ecea921',
    title: 'hello_multiple_decorators_test_on_mh',
    status: 'DONE',
    result: 'PASS',
    user: 'qiupingf',
    actualUser: 'qiupingf',
    spongeLink: '#',
    sessionId: '5b2d1c8e-34ba-46bc-ad9f-c02a7ece9a81',
    sessionTitle: 'Gateway Run: Multiple Decorators',
    sessionStatus: 'DONE',
    sessionResult: 'PASS',
    tests: [
      {
        id: 'd8c4e02d-a7f9-4108-adf9-17d1a70e6f62',
        title: 'MultipleDecoratorsTest#verifyListing',
        status: 'Passed',
        duration: '12s',
        device: '99061FFAZ004AA',
      },
    ],
    createTime: '2025-07-15T10:10:30Z',
    startTime: '2025-07-15T10:11:05Z',
    endTime: '2025-07-15T10:11:40Z',
    config: {
      core: {
        'Device Type': 'AndroidRealDevice',
        'Driver': 'AndroidInstrumentation',
        'Decorator(s)': 'AndroidLogcatDecorator,NoOpDecorator,ScreenshotDecorator,AdbDecorator,WifiDecorator,ExtraCustomDecorator',
        'Total Test Count': 1,
        'Job Priority': 'HIGH',
      },
      retry: {
        'Job Timeout(s)': 900,
        'Test Timeout(s)': 600,
        'Start Timeout(s)': 300,
        'Test Attempts': 1,
        'Force Retry': false,
        'Retry Level': 'ERROR',
      },
      dimensions: {
        'sdk_version': '34',
        'rooted': 'true',
        'brand': 'Google',
        'model': 'Pixel 8',
      },
      params: {
        'param_x': 'value_x',
      },
    },
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
  cloudLogLink: '#',
};
