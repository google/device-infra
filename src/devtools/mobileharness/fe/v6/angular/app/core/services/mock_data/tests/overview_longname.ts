import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';
import {MockTestScenario} from '../models';

/** A mock test scenario representing a long name test. */
export const SCENARIO_TEST_LONG_NAME: MockTestScenario = {
  id: 'test-longname-1',
  scenarioName: 'Test with Long Name',
  overview: {
    id: 'test-longname-1',
    name: 'com.google.android.apps.someteam.super.long.package.name.indicating.deep.hierarchy.for.this.particular.test.suite.manifest.class.GmailInstrumentationTest#testSyncWithAVeryLongMethodNameThatExceedsNormalExpectationsForTestingWrappingAndTruncationBehaviorInTheUI',
    status: TestStatus.TEST_STATUS_DONE,
    result: TestResult.TEST_RESULT_PASS,
    job: {
      id: 'd789e532-0bc4-4d89-8260-8012626e2e4f',
      name: 'com.google.android.apps.someteam.super.long.package.name.indicating.deep.hierarchy.for.this.particular.test.suite.manifest.class',
      status: JobStatus.JOB_STATUS_DONE,
      result: JobResult.JOB_RESULT_PASS,
      spongeLink: 'http://sponge2/d789e532-0bc4-4d89-8260-8012626e2e4f',
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
      startTime: '2025-07-09T10:11:15Z',
      endTime: '2025-07-09T10:11:25Z',
      updateTime: '2025-07-09T10:11:25Z',
      user: 'dafeni',
      actualUser: 'dafeni@google.com',
    },
    properties: {},
    timingBreakdown: {
      createTime: '2025-07-09T10:11:15Z',
      startTime: '2025-07-09T10:11:15Z',
      endTime: '2025-07-09T10:11:25Z',
      stages: [],
    },
  },
  log: 'Passed.',
  cloudLogLink:
    'https://console.cloud.google.com/logs/query;query=resource.type%3D%22mobileharness_test%22%20AND%20labels.test_id%3D%22test-long-name%22',
};
