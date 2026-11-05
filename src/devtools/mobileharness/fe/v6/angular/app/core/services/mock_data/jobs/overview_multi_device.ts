import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/common_models';
import {MockJobScenario} from '../models';

/** A mock job scenario representing a job with multiple devices. */
export const SCENARIO_JOB_MULTI_DEVICE: MockJobScenario = {
  id: 'job-multi-device-1',
  scenarioName: 'Multi-Device Job',
  overview: {
    executionDetails: {
      user: 'mobly-runner',
      actualUser: 'qa-engineer',
      createTime: '2025-07-10T10:00:00Z',
      startTime: '2025-07-10T10:01:00Z',
      endTime: '2025-07-10T10:05:00Z',
    },
    session: {
      id: '9f8e7d6c-5b4a-3210-9876-543210fedcba',
    },
    name: 'cross_device_connectivity_test',
    config: {
      devices: {
        device: [
          {
            deviceType: 'AndroidRealDevice',
            driver: 'MoblyTest',
            dimensions: {
              'label': 'phone_A',
              'model': 'Pixel 8',
            },
          },
          {
            deviceType: 'AndroidRealDevice',
            driver: 'MoblyTest',
            dimensions: {
              'label': 'phone_B',
              'model': 'Pixel 7',
            },
          },
        ],
      },
      settings: {
        totalTestCount: 2,
        priority: 'HIGH',
      },
      params: {
        'mobly_config': 'test_config.yaml',
      },
    },
    tests: {
      test: [
        {
          id: 'mobly-test-1',
          name: 'ConnectivityTest#testBluetoothPairing',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2025-07-10T10:01:10Z',
          endTime: '2025-07-10T10:03:00Z',
          devices: {
            device: [{id: 'Pixel-8-A'}, {id: 'Pixel-7-B'}],
          },
        },
        {
          id: 'mobly-test-2',
          name: 'ConnectivityTest#testWifiDirectTransfer',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2025-07-10T10:03:00Z',
          endTime: '2025-07-10T10:04:50Z',
          devices: {
            device: [{id: 'Pixel-8-A'}, {id: 'Pixel-7-B'}],
          },
        },
      ],
    },
    id: 'c0ffee-beef-d00d-face-1234567890ab',
    status: JobStatus.JOB_STATUS_DONE,
    result: JobResult.JOB_RESULT_PASS,
    spongeLink: 'http://sponge2/c0ffee-beef-d00d-face-1234567890ab',
    properties: {
      'mobly_suite_version': '1.0.2',
    },
    timingBreakdown: {
      summaryLines: [
        'Passed in 5m 0s: 2 passed across 2 test runs.',
        '1m 0s preparing, 10.0s waiting for devices, 3m 40s running, 10.0s finishing.',
      ],
      originTime: '2025-07-10T10:00:00Z',
      finishTime: '2025-07-10T10:05:00Z',
      axis: {
        tickStepPercent: 20,
        ticks: [
          {
            leftPercent: 0,
            label: '0s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_START',
          },
          {
            leftPercent: 40,
            label: '+2m',
            alignment: 'TIMELINE_TICK_ALIGNMENT_CENTER',
          },
          {
            leftPercent: 80,
            label: '+4m',
            alignment: 'TIMELINE_TICK_ALIGNMENT_CENTER',
          },
          {
            leftPercent: 100,
            label: '+5m',
            alignment: 'TIMELINE_TICK_ALIGNMENT_END',
          },
        ],
      },
      splitSegments: [
        {
          focusKey: 'preparing',
          label: 'Preparing',
          fill: 'TIMELINE_FILL_SETUP',
          flexWeight: 60000,
          durationText: '1m 0s',
          shareText: '20%',
        },
        {
          focusKey: 'waiting',
          label: 'Waiting for devices',
          fill: 'TIMELINE_FILL_WAIT',
          flexWeight: 10000,
          durationText: '10.0s',
          shareText: '3.3%',
        },
        {
          focusKey: 'running',
          label: 'Running tests',
          fill: 'TIMELINE_FILL_TEST',
          flexWeight: 220000,
          durationText: '3m 40s',
          shareText: '73%',
        },
        {
          focusKey: 'finishing',
          label: 'Finishing',
          fill: 'TIMELINE_FILL_TEARDOWN',
          flexWeight: 10000,
          durationText: '10.0s',
          shareText: '3.3%',
        },
      ],
      jobTimeline: {
        totalDurationText: '5m 0s',
        activityBars: [
          {
            focusKey: 'preparing',
            label: 'Preparing',
            fill: 'TIMELINE_FILL_SETUP',
            shareText: '20%',
            interval: {
              startTime: '2025-07-10T10:00:00Z',
              endTime: '2025-07-10T10:01:00Z',
              leftPercent: 0,
              widthPercent: 20,
              startOffsetText: '+0ms',
              endOffsetText: '+1m 0s',
              durationText: '1m 0s',
            },
          },
          {
            focusKey: 'waiting',
            label: 'Waiting for devices',
            fill: 'TIMELINE_FILL_WAIT',
            shareText: '3.3%',
            interval: {
              startTime: '2025-07-10T10:01:00Z',
              endTime: '2025-07-10T10:01:10Z',
              leftPercent: 20,
              widthPercent: 3.333,
              startOffsetText: '+1m 0s',
              endOffsetText: '+1m 10s',
              durationText: '10.0s',
            },
          },
          {
            focusKey: 'running',
            label: 'Running tests',
            fill: 'TIMELINE_FILL_TEST',
            shareText: '73%',
            interval: {
              startTime: '2025-07-10T10:01:10Z',
              endTime: '2025-07-10T10:04:50Z',
              leftPercent: 23.333,
              widthPercent: 73.333,
              startOffsetText: '+1m 10s',
              endOffsetText: '+4m 50s',
              durationText: '3m 40s',
            },
          },
          {
            focusKey: 'finishing',
            label: 'Finishing',
            fill: 'TIMELINE_FILL_TEARDOWN',
            shareText: '3.3%',
            interval: {
              startTime: '2025-07-10T10:04:50Z',
              endTime: '2025-07-10T10:05:00Z',
              leftPercent: 96.667,
              widthPercent: 3.333,
              startOffsetText: '+4m 50s',
              endOffsetText: '+5m 0s',
              durationText: '10.0s',
            },
          },
        ],
        lanes: [
          {
            testId: 'mobly-test-1',
            displayName: 'testBluetoothPairing',
            fullName: 'ConnectivityTest#testBluetoothPairing',
            hasRecord: true,
            deviceIds: ['Pixel-8-A', 'Pixel-7-B'],
            resultTone: 'TIMELINE_RESULT_TONE_PASS',
            resultLabel: 'Pass',
            waitedText: '10.0s',
            ranCell: {recorded: {durationText: '1m 50s'}},
            bars: [
              {
                focusKey: 'waiting',
                fill: 'TIMELINE_FILL_WAIT',
                leftPercent: 20,
                widthPercent: 3.333,
              },
              {
                focusKey: 'running',
                fill: 'TIMELINE_FILL_TEST',
                leftPercent: 23.333,
                widthPercent: 36.667,
              },
            ],
          },
          {
            testId: 'mobly-test-2',
            displayName: 'testWifiDirectTransfer',
            fullName: 'ConnectivityTest#testWifiDirectTransfer',
            hasRecord: true,
            deviceIds: ['Pixel-8-A', 'Pixel-7-B'],
            resultTone: 'TIMELINE_RESULT_TONE_PASS',
            resultLabel: 'Pass',
            waitedText: '2m 0s',
            ranCell: {recorded: {durationText: '1m 50s'}},
            bars: [
              {
                focusKey: 'waiting',
                fill: 'TIMELINE_FILL_WAIT',
                leftPercent: 20,
                widthPercent: 40,
              },
              {
                focusKey: 'running',
                fill: 'TIMELINE_FILL_TEST',
                leftPercent: 60,
                widthPercent: 36.667,
              },
            ],
          },
        ],
      },
    },
  },
  log: '[10:01:00] Mobly test started\n[10:01:05] Device 1 allocated: Pixel-8-A\n[10:01:10] Device 2 allocated: Pixel-7-B\n[10:01:15] Running Mobly test suite...\n[10:05:00] All tests passed.',
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=resource.type%3D%22mobileharness_job%22%20AND%20labels.job_id%3D%22c0ffee-beef-d00d-face-1234567890ab%22',
};
