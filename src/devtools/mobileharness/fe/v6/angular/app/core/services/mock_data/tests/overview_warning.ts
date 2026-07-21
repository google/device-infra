import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';
import {MockTestScenario} from '../models';

/** A mock test scenario representing a warning test. */
export const SCENARIO_TEST_WARNING: MockTestScenario = {
  id: 'test-warning-1',
  scenarioName: 'Warning Test',
  overview: {
    id: 'test-warning-1',
    name: 'com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#warningTest',
    status: TestStatus.TEST_STATUS_DONE,
    result: TestResult.TEST_RESULT_PASS,
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
      startTime: '2025-07-09T10:11:15Z',
      endTime: '2025-07-09T10:11:25Z',
      updateTime: '2025-07-09T10:11:25Z',
      user: 'dafeni',
      actualUser: 'dafeni@google.com',
    },
    properties: {
      'dimension_rooted': 'false',
    },
    troubleshooting: {
      warnings: {
        warning: [
          {message: 'Logcat daemon restarted during test execution'},
          {
            message:
              'WARNING [10:11:20] ADB connection lost temporarily, re-established.',
          },
          {
            message:
              'WARNING [10:11:22] Logcat parser skipped 12 lines due to buffer overflow.',
          },
        ],
      },
    },
    timingBreakdown: {
      createTime: '2025-07-09T10:11:15Z',
      startTime: '2025-07-09T10:11:15Z',
      endTime: '2025-07-09T10:11:25Z',
      stages: [
        {
          name: 'Pre-run Test',
          tag: 'client',
          startTime: '2025-07-09T10:11:15Z',
          endTime: '2025-07-09T10:11:18Z',
        },
        {
          name: 'Pre-run Test',
          tag: 'lab',
          startTime: '2025-07-09T10:11:17Z',
          endTime: '2025-07-09T10:11:20Z',
        },
        {
          name: 'Run Test',
          tag: 'client',
          startTime: '2025-07-09T10:11:19Z',
          endTime: '2025-07-09T10:11:23Z',
        },
        {
          name: 'Run Test',
          tag: 'lab',
          startTime: '2025-07-09T10:11:21Z',
          endTime: '2025-07-09T10:11:25Z',
        },
        {
          name: 'Clean Up',
          startTime: '2025-07-09T10:11:24Z',
          endTime: '2025-07-09T10:11:25Z',
        },
      ],
    },
  },
  log: 'Test passed with warnings.',
  cloudLogLink:
    'https://console.cloud.google.com/logs/query;query=resource.type%3D%22mobileharness_test%22%20AND%20labels.test_id%3D%22test-warning-1%22',
};
