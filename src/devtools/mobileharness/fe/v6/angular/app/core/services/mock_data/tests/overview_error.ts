import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';
import {MockTestScenario} from '../models';

/** A mock test scenario representing a error test. */
export const SCENARIO_TEST_ERROR: MockTestScenario = {
  id: 'test-error-1',
  scenarioName: 'Test with Infrastructure Error',
  overview: {
    id: 'test-error-1',
    name: 'com.google.devtools.mobileharness.infra.HarnessErrorTest#testSetup',
    status: TestStatus.TEST_STATUS_DONE,
    result: TestResult.TEST_RESULT_ERROR,
    job: {
      id: 'c3578d2a-776d-49e3-b065-c66624b9d665',
      name: 'com.google.android.gm.GmailInstrumentationTest',
      status: JobStatus.JOB_STATUS_DONE,
      result: JobResult.JOB_RESULT_ERROR,
      spongeLink: 'http://sponge2/c3578d2a-776d-49e3-b065-c66624b9d665',
    },
    devices: {
      device: [
        {
          id: '43021FDAQ000UM',
          type: 'AndroidRealDevice',
        },
      ],
    },
    host: {
      name: 'mt31-dm01-a-x04.moma.google.com',
      ip: '100.107.201.12',
    },
    executionDetails: {
      createTime: '2025-07-09T10:15:00Z',
      startTime: '2025-07-09T10:15:05Z',
      endTime: '2025-07-09T10:15:10Z',
      updateTime: '2025-07-09T10:15:10Z',
      user: 'dafeni',
      actualUser: 'dafeni@google.com',
    },
    properties: {
      'infra_version': 'v6.2.1',
    },
    troubleshooting: {
      resultCause: {
        error: [
          {
            message: 'DEVICE_DETECTION_ERROR: Failed to detect USB connection',
            trace:
              'HarnessException: Failed to detect USB connection\\n  at com.google.devtools.mobileharness.infra.detector.UsbDetector.detect(UsbDetector.java:45)',
          },
        ],
      },
    },
    timingBreakdown: {
      createTime: '2025-07-09T10:15:00Z',
      startTime: '2025-07-09T10:15:05Z',
      endTime: '2025-07-09T10:15:10Z',
      stages: [],
    },
  },
  log: 'Infrastructure error occurred.',
  cloudLogLink:
    'https://console.cloud.google.com/logs/query;query=resource.type%3D%22mobileharness_test%22%20AND%20labels.test_id%3D%22test-error-1%22',
};
