import {MockJobScenario} from '../models';

/** A mock job scenario representing a passed job. */
export const SCENARIO_JOB_PASSED: MockJobScenario = {
  id: 'job-passed-1',
  scenarioName: 'Passed Job',
  overview: {
    id: 'b65cadd7-6ad6-440e-a3b7-bfe1948557e6',
    title: 'hello_mobile_harness_test_on_mh',
    status: 'DONE',
    result: 'PASS',
    user: 'dafeni',
    actualUser: 'dafeni',
    spongeLink: '#',
    sessionId: '4f1e3f2e-64ac-48be-adfb-c01a5ece8a58',
    sessionTitle: 'Gateway Run: MobileHarness',
    sessionStatus: 'DONE',
    sessionResult: 'FAIL',
    tests: [
      {
        id: 'c0a4b01d-b7f9-4008-adf9-26d1a70e5f61',
        title: 'HelloMobileHarnessTest#buttonText',
        status: 'Passed',
        duration: '10s',
        device: '99061FFAZ004AA',
      },
      {
        id: 'f52aeb45-f80d-4072-870e-4d01b90f09ec',
        title: 'HelloMobileHarnessTest#customTestArgs',
        status: 'Passed',
        duration: '10s',
        device: '99061FFAZ004AA',
      },
    ],
    createTime: '2025-07-09T10:10:30Z',
    startTime: '2025-07-09T10:11:05Z',
    endTime: '2025-07-09T10:11:40Z',
    config: {
      core: {
        'Device Type': 'AndroidRealDevice',
        'Driver': 'AndroidInstrumentation',
        'Decorator(s)': 'AndroidLogcatDecorator',
        'Total Test Count': 2,
        'Job Priority': 'DEFAULT',
      },
      retry: {
        'Job Timeout(s)': 891,
        'Test Timeout(s)': 600,
        'Start Timeout(s)': 300,
        'Test Attempts': 2,
        'Force Retry': false,
        'Retry Level': 'ERROR',
      },
      dimensions: {
        'sdk_version': '33',
        'rooted': 'true',
      },
      params: {
        'param_a': 'value_a',
        'long_param_name_for_testing_wrapping':
          'a_very_long_value_that_should_wrap_properly',
      },
    },
    properties: {
      'job_prop_1': 'job_value_1',
    },
    timingBreakdown: {
      createTime: '2025-07-09T10:10:30Z',
      startTime: '2025-07-09T10:11:05Z',
      endTime: '2025-07-09T10:11:40Z',
      stages: [
        {
          name: 'Allocation',
          startTime: '2025-07-09T10:10:30Z',
          endTime: '2025-07-09T10:11:05Z',
        },
        {
          name: 'Pre-run Job',
          startTime: '2025-07-09T10:11:05Z',
          endTime: '2025-07-09T10:11:10Z',
        },
        {
          name: 'Running Tests',
          startTime: '2025-07-09T10:11:10Z',
          endTime: '2025-07-09T10:11:40Z',
        },
      ],
    },
  },
  log: '[10:11:05] Job started\n[10:11:10] Allocated device 99061FFAZ004AA\n[10:11:15] Running test-1\n[10:11:25] test-1 passed\n[10:11:30] Running test-2\n[10:11:40] test-2 passed\n[10:11:40] Job finished successfully.',
  cloudLogLink: '#',
};
