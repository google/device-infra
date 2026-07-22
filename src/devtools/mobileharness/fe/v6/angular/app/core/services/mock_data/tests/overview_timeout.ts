import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';
import {MockTestScenario} from '../models';

/** A mock test scenario representing a timeout test. */
export const SCENARIO_TEST_TIMEOUT: MockTestScenario = {
  id: 'test-timeout-1',
  scenarioName: 'Timeout Test',
  overview: {
    id: 'test-timeout-1',
    name: 'com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#hangTest',
    status: TestStatus.TEST_STATUS_DONE,
    result: TestResult.TEST_RESULT_TIMEOUT,
    job: {
      id: 'b65cadd7-6ad6-440e-a3b7-bfe1948557e6',
      name: 'hello_mobile_harness_test_on_mh',
      status: JobStatus.JOB_STATUS_DONE,
      result: JobResult.JOB_RESULT_FAIL,
      spongeLink: 'http://sponge/mock-job-link',
    },
    devices: {
      device: [
        {
          id: '99061FFAZ004AA',
          type: 'AndroidRealDevice',
        },
      ],
    },
    host: {
      name: '3a-cm-10-18-01.acs.google.com',
      ip: '100.107.200.155',
    },
    executionDetails: {
      createTime: '2025-07-09T10:20:00Z',
      startTime: '2025-07-09T10:20:05Z',
      endTime: '2025-07-09T10:25:05Z',
      updateTime: '2025-07-09T10:25:05Z',
      user: 'dafeni',
      actualUser: 'dafeni@google.com',
    },
    properties: {},
    timingBreakdown: {
      createTime: '2025-07-09T10:20:00Z',
      startTime: '2025-07-09T10:20:05Z',
      endTime: '2025-07-09T10:25:05Z',
      stages: [],
    },
  },
  log: 'Test execution timed out after 5 minutes.',
  cloudLogLink: '#',
};
