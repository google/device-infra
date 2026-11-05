import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/common_models';
import {MockJobScenario} from '../models';

/** A mock job scenario representing a job with multiple decorators. */
export const SCENARIO_JOB_MULTIPLE_DECORATORS: MockJobScenario = {
  id: 'job-multiple-decorators-1',
  scenarioName: 'Multiple Decorators Job',
  overview: {
    executionDetails: {
      user: 'dafeni',
      actualUser: 'dafeni',
      createTime: '2025-07-15T10:10:30Z',
      startTime: '2025-07-15T10:11:05Z',
      endTime: '2025-07-15T10:11:40Z',
    },
    session: {
      id: '5f2d4e3a-75bd-49cf-beec-d12b6fdf9b69',
    },
    name: 'multiple_decorators_test_on_mh',
    config: {
      devices: {
        device: [
          {
            deviceType: 'AndroidRealDevice',
            driver: 'AndroidInstrumentation',
            dimensions: {
              'sdk_version': '34',
              'rooted': 'true',
              'brand': 'Google',
              'model': 'Pixel 8',
            },
            decorators: [
              'AndroidSetupDeviceDecorator',
              'ManekiAndroidWebdriverPortForwardDecorator',
            ],
          },
        ],
      },
      settings: {
        totalTestCount: 1,
        priority: 'HIGH',
        testAttempts: 1,
        forceRetry: false,
        retryLevel: 'ERROR',
      },
      params: {
        'param_x': 'value_x',
      },
    },
    tests: {
      test: [
        {
          id: 'd8c4e02d-a7f9-4108-adf9-17d1a70e6f62',
          name: 'MultipleDecoratorsTest#verifyListing',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2025-07-15T10:11:09Z',
          endTime: '2025-07-15T10:11:27Z',
          devices: {
            device: [{id: '99061FFAZ004AA'}],
          },
        },
      ],
    },
    id: 'f98a2c1e-7bc6-42ad-a81d-e01b5ecea921',
    status: JobStatus.JOB_STATUS_DONE,
    result: JobResult.JOB_RESULT_PASS,
    spongeLink: 'http://sponge2/f98a2c1e-7bc6-42ad-a81d-e01b5ecea921',
    properties: {
      'test_property': 'multiple_decorators_val',
    },
    timingBreakdown: {
      summaryLines: [
        'Passed in 1m 10s: 1 passed across 1 test run.',
        '4.0s preparing, 35.0s waiting for devices, 18.0s running, 13.0s finishing.',
      ],
      originTime: '2025-07-15T10:10:30Z',
      finishTime: '2025-07-15T10:11:40Z',
      axis: {
        tickStepPercent: 21.429,
        ticks: [
          {
            leftPercent: 0,
            label: '0s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_START',
          },
          {
            leftPercent: 42.857,
            label: '+30s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_CENTER',
          },
          {
            leftPercent: 100,
            label: '+1m 10s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_END',
          },
        ],
      },
      splitSegments: [
        {
          focusKey: 'preparing',
          label: 'Preparing',
          fill: 'TIMELINE_FILL_SETUP',
          flexWeight: 4000,
          durationText: '4.0s',
          shareText: '5.7%',
        },
        {
          focusKey: 'waiting',
          label: 'Waiting for devices',
          fill: 'TIMELINE_FILL_WAIT',
          flexWeight: 35000,
          durationText: '35.0s',
          shareText: '50%',
        },
        {
          focusKey: 'running',
          label: 'Running tests',
          fill: 'TIMELINE_FILL_TEST',
          flexWeight: 18000,
          durationText: '18.0s',
          shareText: '26%',
        },
        {
          focusKey: 'finishing',
          label: 'Finishing',
          fill: 'TIMELINE_FILL_TEARDOWN',
          flexWeight: 13000,
          durationText: '13.0s',
          shareText: '19%',
        },
      ],
      jobTimeline: {
        totalDurationText: '1m 10s',
        activityBars: [
          {
            focusKey: 'preparing',
            label: 'Preparing',
            fill: 'TIMELINE_FILL_SETUP',
            shareText: '5.7%',
            interval: {
              startTime: '2025-07-15T10:10:30Z',
              endTime: '2025-07-15T10:10:34Z',
              leftPercent: 0,
              widthPercent: 5.714,
              startOffsetText: '+0ms',
              endOffsetText: '+4.0s',
              durationText: '4.0s',
            },
          },
          {
            focusKey: 'waiting',
            label: 'Waiting for devices',
            fill: 'TIMELINE_FILL_WAIT',
            shareText: '50%',
            interval: {
              startTime: '2025-07-15T10:10:34Z',
              endTime: '2025-07-15T10:11:09Z',
              leftPercent: 5.714,
              widthPercent: 50,
              startOffsetText: '+4.0s',
              endOffsetText: '+39.0s',
              durationText: '35.0s',
            },
          },
          {
            focusKey: 'running',
            label: 'Running tests',
            fill: 'TIMELINE_FILL_TEST',
            shareText: '26%',
            interval: {
              startTime: '2025-07-15T10:11:09Z',
              endTime: '2025-07-15T10:11:27Z',
              leftPercent: 55.714,
              widthPercent: 25.714,
              startOffsetText: '+39.0s',
              endOffsetText: '+57.0s',
              durationText: '18.0s',
            },
          },
          {
            focusKey: 'finishing',
            label: 'Finishing',
            fill: 'TIMELINE_FILL_TEARDOWN',
            shareText: '19%',
            interval: {
              startTime: '2025-07-15T10:11:27Z',
              endTime: '2025-07-15T10:11:40Z',
              leftPercent: 81.429,
              widthPercent: 18.571,
              startOffsetText: '+57.0s',
              endOffsetText: '+1m 10s',
              durationText: '13.0s',
            },
          },
        ],
        lanes: [
          {
            testId: 'd8c4e02d-a7f9-4108-adf9-17d1a70e6f62',
            displayName: 'verifyListing',
            fullName: 'MultipleDecoratorsTest#verifyListing',
            hasRecord: true,
            deviceIds: ['99061FFAZ004AA'],
            resultTone: 'TIMELINE_RESULT_TONE_PASS',
            resultLabel: 'Pass',
            waitedText: '35.0s',
            ranCell: {recorded: {durationText: '18.0s'}},
            bars: [
              {
                focusKey: 'waiting',
                fill: 'TIMELINE_FILL_WAIT',
                leftPercent: 5.714,
                widthPercent: 50,
              },
              {
                focusKey: 'running',
                fill: 'TIMELINE_FILL_SETUP',
                leftPercent: 55.714,
                widthPercent: 8.571,
              },
              {
                focusKey: 'running',
                fill: 'TIMELINE_FILL_TEST',
                leftPercent: 64.286,
                widthPercent: 12.857,
              },
              {
                focusKey: 'running',
                fill: 'TIMELINE_FILL_TEARDOWN',
                leftPercent: 77.143,
                widthPercent: 4.286,
              },
            ],
          },
        ],
      },
    },
  },
  log: '[10:11:05] Job started\n[10:11:09] Allocated device 99061FFAZ004AA\n[10:11:15] Running test-1\n[10:11:27] test-1 passed\n[10:11:40] Job finished successfully.',
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=resource.type%3D%22mobileharness_job%22%20AND%20labels.job_id%3D%22f00d-beef-1234-5678-abcde%22',
};
