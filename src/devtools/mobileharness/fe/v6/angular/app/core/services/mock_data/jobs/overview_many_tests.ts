import {
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/common_models';
import {MockJobScenario} from '../models';

/** A mock job scenario representing a job with many child tests. */
export const SCENARIO_JOB_MANY_TESTS: MockJobScenario = {
  id: 'job-many-tests-1',
  scenarioName: 'Many Tests Job',
  overview: {
    executionDetails: {
      user: 'test-runner-service',
      actualUser: 'g3-ci-system',
      createTime: '2025-07-10T02:00:00Z',
      startTime: '2025-07-10T02:00:05Z',
      endTime: '2025-07-10T02:30:10Z',
    },
    session: {
      id: 'a1b2c3d4-e5f6-7890-abcd-ef0123456789',
      name: 'Nightly Pixel 8 Pro Tests',
      status: SessionStatus.SESSION_STATUS_DONE,
      result: SessionResult.SESSION_RESULT_PASS,
    },
    name: 'Full Regression Suite',
    config: {
      devices: {
        device: [
          {
            deviceType: 'AndroidRealDevice',
            driver: 'AndroidInstrumentation',
          },
        ],
      },
      settings: {
        totalTestCount: 10,
      },
      params: {},
    },
    tests: {
      test: [
        {
          id: 'test-1',
          name: 'LoginTest#successfulLogin',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2025-07-10T02:00:05Z',
          endTime: '2025-07-10T02:02:15Z',
          devices: {
            device: [{id: 'Pixel8-A', type: 'AndroidRealDevice'}],
          },
        },
        {
          id: 'test-2',
          name: 'LoginTest#invalidPassword',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2025-07-10T02:02:15Z',
          endTime: '2025-07-10T02:04:10Z',
          devices: {
            device: [{id: 'Pixel8-B', type: 'AndroidRealDevice'}],
          },
        },
        {
          id: 'test-3',
          name: 'SettingsTest#profileUpdate',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2025-07-10T02:04:10Z',
          endTime: '2025-07-10T02:07:30Z',
          devices: {
            device: [{id: 'Pixel8-C', type: 'AndroidRealDevice'}],
          },
        },
        {
          id: 'test-4',
          name: 'CheckoutTest#cartAddItem',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2025-07-10T02:07:30Z',
          endTime: '2025-07-10T02:10:45Z',
          devices: {
            device: [{id: 'Pixel8-A', type: 'AndroidRealDevice'}],
          },
        },
        {
          id: 'test-5',
          name: 'CheckoutTest#paymentGatewayTimeout',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2025-07-10T02:10:45Z',
          endTime: '2025-07-10T02:15:00Z',
          devices: {
            device: [{id: 'Pixel8-B', type: 'AndroidRealDevice'}],
          },
        },
        {
          id: 'test-6',
          name: 'SearchTest#filterByPrice',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2025-07-10T02:15:00Z',
          endTime: '2025-07-10T02:18:20Z',
          devices: {
            device: [{id: 'Pixel8-C', type: 'AndroidRealDevice'}],
          },
        },
        {
          id: 'test-7',
          name: 'SearchTest#paginationLoadsNextPage',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2025-07-10T02:18:20Z',
          endTime: '2025-07-10T02:22:15Z',
          devices: {
            device: [{id: 'Pixel8-A', type: 'AndroidRealDevice'}],
          },
        },
        {
          id: 'test-8',
          name: 'NotificationsTest#receivePushNotificationBackground',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2025-07-10T02:22:15Z',
          endTime: '2025-07-10T02:26:00Z',
          devices: {
            device: [{id: 'Pixel8-B', type: 'AndroidRealDevice'}],
          },
        },
        {
          id: 'test-9',
          name: 'ProfileTest#avatarUploadLargeImage',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2025-07-10T02:26:00Z',
          endTime: '2025-07-10T02:28:40Z',
          devices: {
            device: [{id: 'Pixel8-C', type: 'AndroidRealDevice'}],
          },
        },
        {
          id: 'test-10',
          name: 'ProfileTest#deleteAccountConfirmationFlow',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2025-07-10T02:28:40Z',
          endTime: '2025-07-10T02:30:10Z',
          devices: {
            device: [{id: 'Pixel8-A', type: 'AndroidRealDevice'}],
          },
        },
      ],
    },
    id: 'beef-cafe-d00d-face-feed',
    status: JobStatus.JOB_STATUS_DONE,
    result: JobResult.JOB_RESULT_PASS,
    spongeLink: 'http://sponge2/beef-cafe-d00d-face-feed',
    properties: {},
    timingBreakdown: {
      createTime: '2025-07-10T02:00:00Z',
      startTime: '2025-07-10T02:00:05Z',
      endTime: '2025-07-10T02:30:10Z',
      stages: [
        {
          name: 'Allocation',
          startTime: '2025-07-10T02:00:00Z',
          endTime: '2025-07-10T02:00:05Z',
        },
        {
          name: 'Running 8 Tests',
          startTime: '2025-07-10T02:00:05Z',
          endTime: '2025-07-10T02:30:10Z',
        },
      ],
    },
  },
  log: 'Completed all 10 tests successfully.',
  cloudLogLink:
    'https://console.cloud.google.com/logs/query;query=resource.type%3D%22mobileharness_job%22%20AND%20labels.job_id%3D%22beef-cafe-d00d-face-feed%22',
};
