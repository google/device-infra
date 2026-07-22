import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';
import {MockTestScenario} from '../models';

/** A mock test scenario representing a passed test. */
export const SCENARIO_TEST_PASSED: MockTestScenario = {
  id: 'test-passed-1',
  scenarioName: 'Passed Test',
  overview: {
    id: 'test-passed-1',
    name: 'com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#buttonText',
    status: TestStatus.TEST_STATUS_DONE,
    result: TestResult.TEST_RESULT_PASS,
    job: {
      id: 'b65cadd7-6ad6-440e-a3b7-bfe1948557e6',
      name: 'hello_mobile_harness_test_on_mh',
      status: JobStatus.JOB_STATUS_DONE,
      result: JobResult.JOB_RESULT_PASS,
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
      createTime: '2025-07-09T10:11:15Z',
      startTime: '2025-07-09T10:11:15Z',
      endTime: '2025-07-09T10:11:25Z',
      updateTime: '2025-07-09T10:11:25Z',
      user: 'dafeni',
      actualUser: 'dafeni@google.com',
    },
    properties: {
      'dimension_rooted': 'false',
      'abibaseband_version': '0c-250327-250401-b-13296697,g5400c-25032',
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
          endTime: '2025-07-09T10:11:17Z',
        },
        {
          name: 'Pre-run Test',
          tag: 'lab',
          startTime: '2025-07-09T10:11:17Z',
          endTime: '2025-07-09T10:11:19Z',
        },
        {
          name: 'Run Test',
          tag: 'client',
          startTime: '2025-07-09T10:11:19Z',
          endTime: '2025-07-09T10:11:21Z',
        },
        {
          name: 'Run Test',
          tag: 'lab',
          startTime: '2025-07-09T10:11:21Z',
          endTime: '2025-07-09T10:11:25Z',
        },
      ],
    },
  },
  log: 'Test passed successfully.',
  cloudLogLink: '#',
};
