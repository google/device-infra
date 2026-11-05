import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/common_models';
import {MockJobScenario} from '../models';

/** A mock job scenario representing an in-progress job. */
export const SCENARIO_JOB_IN_PROGRESS: MockJobScenario = {
  id: 'job-inprogress-1',
  scenarioName: 'In Progress Job',
  overview: {
    executionDetails: {
      user: 'dafeni',
      actualUser: 'dafeni',
      createTime: '2025-07-09T11:30:00Z',
      startTime: '2025-07-09T11:30:05Z',
    },
    name: 'long_running_reliability_test',
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
        totalTestCount: 1,
      },
      params: {},
    },
    tests: {
      test: [
        {
          id: 'test-inprogress-1',
          name: 'com.google.devtools.mobileharness.infra.HarnessActiveTest#testRun',
          status: TestStatus.TEST_STATUS_RUNNING,
          result: TestResult.TEST_RESULT_UNSPECIFIED,
          startTime: '2025-07-09T11:30:10Z',
          devices: {
            device: [{id: '4D5A1FDAB001BB'}],
          },
        },
      ],
    },
    id: 'a1e8e532-0ac4-4d89-8260-8012626e2e4f',
    status: JobStatus.JOB_STATUS_RUNNING,
    result: JobResult.JOB_RESULT_UNSPECIFIED,
    spongeLink: 'http://sponge2/a1e8e532-0ac4-4d89-8260-8012626e2e4f',
    properties: {},
    timingBreakdown: {
      summaryLines: [
        'Running for 20.0s: 1 running across 1 test run.',
        '5.0s preparing, 5.0s waiting for devices, 10.0s running.',
      ],
      originTime: '2025-07-09T11:30:00Z',
      finishTime: '2025-07-09T11:30:20Z',
      axis: {
        tickStepPercent: 25,
        ticks: [
          {
            leftPercent: 0,
            label: '0s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_START',
          },
          {
            leftPercent: 50,
            label: '+10s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_CENTER',
          },
          {
            leftPercent: 100,
            label: '+20s',
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
          shareText: '25%',
        },
        {
          focusKey: 'waiting',
          label: 'Waiting for devices',
          fill: 'TIMELINE_FILL_WAIT',
          flexWeight: 5000,
          durationText: '5.0s',
          shareText: '25%',
        },
        {
          focusKey: 'running',
          label: 'Running tests',
          fill: 'TIMELINE_FILL_TEST',
          flexWeight: 10000,
          durationText: '10.0s',
          shareText: '50%',
        },
      ],
      jobTimeline: {
        totalDurationText: '20.0s',
        activityBars: [
          {
            focusKey: 'preparing',
            label: 'Preparing',
            fill: 'TIMELINE_FILL_SETUP',
            shareText: '25%',
            interval: {
              startTime: '2025-07-09T11:30:00Z',
              endTime: '2025-07-09T11:30:05Z',
              leftPercent: 0,
              widthPercent: 25,
              startOffsetText: '+0ms',
              endOffsetText: '+5.0s',
              durationText: '5.0s',
            },
          },
          {
            focusKey: 'waiting',
            label: 'Waiting for devices',
            fill: 'TIMELINE_FILL_WAIT',
            shareText: '25%',
            interval: {
              startTime: '2025-07-09T11:30:05Z',
              endTime: '2025-07-09T11:30:10Z',
              leftPercent: 25,
              widthPercent: 25,
              startOffsetText: '+5.0s',
              endOffsetText: '+10.0s',
              durationText: '5.0s',
            },
          },
          {
            focusKey: 'running',
            label: 'Running tests',
            fill: 'TIMELINE_FILL_TEST',
            shareText: '50%',
            interval: {
              startTime: '2025-07-09T11:30:10Z',
              endTime: '2025-07-09T11:30:20Z',
              leftPercent: 50,
              widthPercent: 50,
              startOffsetText: '+10.0s',
              endOffsetText: '+20.0s',
              durationText: '10.0s',
            },
          },
        ],
        lanes: [
          {
            testId: 'test-inprogress-1',
            displayName: 'testRun',
            fullName:
              'com.google.devtools.mobileharness.infra.HarnessActiveTest#testRun',
            hasRecord: true,
            deviceIds: ['4D5A1FDAB001BB'],
            resultTone: 'TIMELINE_RESULT_TONE_NEUTRAL',
            resultLabel: 'Running',
            waitedText: '5.0s',
            ranCell: {recorded: {durationText: '10.0s'}},
            bars: [
              {
                focusKey: 'waiting',
                fill: 'TIMELINE_FILL_WAIT',
                leftPercent: 25,
                widthPercent: 25,
              },
              {
                focusKey: 'running',
                fill: 'TIMELINE_FILL_TEST',
                leftPercent: 50,
                widthPercent: 50,
              },
            ],
          },
        ],
      },
    },
  },
  log: '[11:30:05] Job started\n[11:30:10] Allocated device 4D5A1FDAB001BB\n[11:30:15] Running test fdda90bf... ',
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=resource.type%3D%22mobileharness_job%22%20AND%20labels.job_id%3D%22a1e8e532-0ac4-4d89-8260-8012626e2e4f%22',
};
