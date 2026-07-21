import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';
import {MockTestScenario} from '../models';

/** A mock test scenario representing a skipped test. */
export const SCENARIO_TEST_SKIPPED: MockTestScenario = {
  id: 'test-skipped-1',
  scenarioName: 'Skipped Test',
  overview: {
    id: 'test-skipped-1',
    name: 'com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#ignoredTest',
    status: TestStatus.TEST_STATUS_DONE,
    result: TestResult.TEST_RESULT_SKIP,
    job: {
      id: 'b65cadd7-6ad6-440e-a3b7-bfe1948557e6',
      name: 'hello_mobile_harness_test_on_mh',
      status: JobStatus.JOB_STATUS_DONE,
      result: JobResult.JOB_RESULT_PASS,
      spongeLink: 'http://sponge2/b65cadd7-6ad6-440e-a3b7-bfe1948557e6',
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
      createTime: '2025-07-09T10:11:15Z',
      endTime: '2025-07-09T10:11:16Z',
      updateTime: '2025-07-09T10:11:16Z',
      user: 'dafeni',
      actualUser: 'dafeni@google.com',
    },
    properties: {},
    timingBreakdown: {
      createTime: '2025-07-09T10:11:15Z',
      endTime: '2025-07-09T10:11:16Z',
      stages: [],
    },
  },
  log: 'Test skipped due to @Ignore annotation.',
  cloudLogLink:
    'https://console.cloud.google.com/logs/query;query=resource.type%3D%22mobileharness_test%22%20AND%20labels.test_id%3D%22test-skipped-1%22',
};
