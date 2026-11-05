import {
  JobResult,
  JobStatus,
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
            device: [{id: 'Pixel8-A'}],
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
            device: [{id: 'Pixel8-B'}],
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
            device: [{id: 'Pixel8-C'}],
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
            device: [{id: 'Pixel8-A'}],
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
            device: [{id: 'Pixel8-B'}],
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
            device: [{id: 'Pixel8-C'}],
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
            device: [{id: 'Pixel8-A'}],
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
            device: [{id: 'Pixel8-B'}],
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
            device: [{id: 'Pixel8-C'}],
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
            device: [{id: 'Pixel8-A'}],
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
      summaryLines: [
        'Passed in 30m 10s: 10 passed across 10 test runs.',
        '5.0s preparing, 30m 5s running.',
      ],
      originTime: '2025-07-10T02:00:00Z',
      finishTime: '2025-07-10T02:30:10Z',
      axis: {
        tickStepPercent: 16.575,
        ticks: [
          {
            leftPercent: 0,
            label: '0s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_START',
          },
          {
            leftPercent: 33.149,
            label: '+10m',
            alignment: 'TIMELINE_TICK_ALIGNMENT_CENTER',
          },
          {
            leftPercent: 66.298,
            label: '+20m',
            alignment: 'TIMELINE_TICK_ALIGNMENT_CENTER',
          },
          {
            leftPercent: 100,
            label: '+30m 10s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_END',
          },
        ],
      },
      splitSegments: [
        {
          focusKey: 'preparing',
          label: 'Preparing',
          fill: 'TIMELINE_FILL_SETUP',
          flexWeight: 5000,
          durationText: '5.0s',
          shareText: '<1%',
        },
        {
          focusKey: 'running',
          label: 'Running tests',
          fill: 'TIMELINE_FILL_TEST',
          flexWeight: 1805000,
          durationText: '30m 5s',
          shareText: '>99%',
        },
      ],
      jobTimeline: {
        totalDurationText: '30m 10s',
        activityBars: [
          {
            focusKey: 'preparing',
            label: 'Preparing',
            fill: 'TIMELINE_FILL_SETUP',
            shareText: '<1%',
            interval: {
              startTime: '2025-07-10T02:00:00Z',
              endTime: '2025-07-10T02:00:05Z',
              leftPercent: 0,
              widthPercent: 0.276,
              startOffsetText: '+0ms',
              endOffsetText: '+5.0s',
              durationText: '5.0s',
            },
          },
          {
            focusKey: 'running',
            label: 'Running tests',
            fill: 'TIMELINE_FILL_TEST',
            shareText: '>99%',
            interval: {
              startTime: '2025-07-10T02:00:05Z',
              endTime: '2025-07-10T02:30:10Z',
              leftPercent: 0.276,
              widthPercent: 99.724,
              startOffsetText: '+5.0s',
              endOffsetText: '+30m 10s',
              durationText: '30m 5s',
            },
          },
        ],
        lanes: [
          {
            testId: 'test-1',
            displayName: 'successfulLogin',
            fullName: 'LoginTest#successfulLogin',
            hasRecord: true,
            deviceIds: ['Pixel8-A'],
            resultTone: 'TIMELINE_RESULT_TONE_PASS',
            resultLabel: 'Pass',
            waitedText: '0ms',
            ranCell: {recorded: {durationText: '2m 10s'}},
            bars: [
              {
                focusKey: 'running',
                fill: 'TIMELINE_FILL_SETUP',
                leftPercent: 0.276,
                widthPercent: 1.5,
              },
              {
                focusKey: 'running',
                fill: 'TIMELINE_FILL_TEST',
                leftPercent: 1.776,
                widthPercent: 4.5,
              },
              {
                focusKey: 'running',
                fill: 'TIMELINE_FILL_TEARDOWN',
                leftPercent: 6.276,
                widthPercent: 1.182,
              },
            ],
          },
          {
            testId: 'test-2',
            displayName: 'invalidPassword',
            fullName: 'LoginTest#invalidPassword',
            hasRecord: true,
            deviceIds: ['Pixel8-B'],
            resultTone: 'TIMELINE_RESULT_TONE_PASS',
            resultLabel: 'Pass',
            waitedText: '2m 10s',
            ranCell: {recorded: {durationText: '1m 55s'}},
            bars: [
              {
                focusKey: 'waiting',
                fill: 'TIMELINE_FILL_WAIT',
                leftPercent: 0.276,
                widthPercent: 7.182,
              },
              {
                focusKey: 'running',
                fill: 'TIMELINE_FILL_TEST',
                leftPercent: 7.459,
                widthPercent: 6.354,
              },
            ],
          },
          {
            testId: 'test-3',
            displayName: 'profileUpdate',
            fullName: 'SettingsTest#profileUpdate',
            hasRecord: true,
            deviceIds: ['Pixel8-C'],
            resultTone: 'TIMELINE_RESULT_TONE_PASS',
            resultLabel: 'Pass',
            waitedText: '4m 5s',
            ranCell: {recorded: {durationText: '3m 20s'}},
            bars: [
              {
                focusKey: 'waiting',
                fill: 'TIMELINE_FILL_WAIT',
                leftPercent: 0.276,
                widthPercent: 13.536,
              },
              {
                focusKey: 'running',
                fill: 'TIMELINE_FILL_TEST',
                leftPercent: 13.812,
                widthPercent: 11.05,
              },
            ],
          },
        ],
      },
    },
  },
  log: 'Completed all 10 tests successfully.',
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=resource.type%3D%22mobileharness_job%22%20AND%20labels.job_id%3D%22beef-cafe-d00d-face-feed%22',
};
