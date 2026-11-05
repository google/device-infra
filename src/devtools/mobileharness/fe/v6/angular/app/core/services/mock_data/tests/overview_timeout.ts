import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';
import {MockTestScenario} from '../models';

/** A mock test scenario representing a timeout test. */
export const SCENARIO_TEST_TIMEOUT: MockTestScenario = {
  id: 'test-timeout-1',
  scenarioName: 'Timeout Test',
  overview: {
    id: 'test-timeout-1',
    name: 'com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#hangTest',
    status: TestStatus.TEST_STATUS_DONE,
    result: TestResult.TEST_RESULT_TIMEOUT,
    job: {
      id: 'b65cadd7-6ad6-440e-a3b7-bfe1948557e6',
      name: 'hello_mobile_harness_test_on_mh',
      status: JobStatus.JOB_STATUS_DONE,
      result: JobResult.JOB_RESULT_FAIL,
      spongeLink: 'http://sponge2/b65cadd7-6ad6-440e-a3b7-bfe1948557e6',
    },
    devices: {
      device: [
        {
          id: '99061FFAZ004AA',
        },
      ],
    },
    host: {
      name: '3a-cm-10-18-01.acs.example.com',
      ip: '100.107.200.155',
    },
    executionDetails: {
      createTime: '2025-07-09T10:20:00Z',
      startTime: '2025-07-09T10:20:05Z',
      endTime: '2025-07-09T10:25:05Z',
      updateTime: '2025-07-09T10:25:05Z',
      user: 'dafeni',
      actualUser: 'dafeni@example.com',
    },
    properties: {},
    timingBreakdown: {
      summaryLines: [
        'Timed out after 5m 5s, during AndroidInstrumentation (+5m 0s from start).',
        '5.0s waiting to start, 10.0s setup, 4m 45s test run, 5.0s teardown.',
      ],
      originTime: '2025-07-09T10:20:00Z',
      finishTime: '2025-07-09T10:25:05Z',
      axis: {
        tickStepPercent: 19.672,
        ticks: [
          {
            leftPercent: 0,
            label: '0s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_START',
          },
          {
            leftPercent: 39.344,
            label: '+2m',
            alignment: 'TIMELINE_TICK_ALIGNMENT_CENTER',
          },
          {
            leftPercent: 78.689,
            label: '+4m',
            alignment: 'TIMELINE_TICK_ALIGNMENT_CENTER',
          },
          {
            leftPercent: 100,
            label: '+5m 5s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_END',
          },
        ],
      },
      splitSegments: [
        {
          focusKey: 'wait',
          label: 'Waiting to start',
          fill: 'TIMELINE_FILL_WAIT',
          flexWeight: 5000,
          durationText: '5.0s',
          shareText: '1.6%',
        },
        {
          focusKey: 'setup',
          label: 'Setup',
          fill: 'TIMELINE_FILL_SETUP',
          flexWeight: 10000,
          durationText: '10.0s',
          shareText: '3.3%',
        },
        {
          focusKey: 'test',
          label: 'Test run',
          fill: 'TIMELINE_FILL_TEST',
          flexWeight: 285000,
          durationText: '4m 45s',
          shareText: '93%',
        },
        {
          focusKey: 'teardown',
          label: 'Teardown',
          fill: 'TIMELINE_FILL_TEARDOWN',
          flexWeight: 5000,
          durationText: '5.0s',
          shareText: '1.6%',
        },
      ],
      testTimeline: {
        phases: [
          {
            phaseId: 'wait',
            focusKey: 'wait',
            label: 'Waiting to start',
            fill: 'TIMELINE_FILL_WAIT',
            bracketLeftPercent: 0,
            bracketWidthPercent: 1.639,
            durationText: '5.0s',
            shareText: '1.6%',
            rows: [
              {
                label: 'Waiting for a device',
                focusKey: 'wait',
                fill: 'TIMELINE_FILL_WAIT',
                durationText: '5.0s',
                shareText: '1.6%',
                bars: [
                  {
                    startTime: '2025-07-09T10:20:00Z',
                    endTime: '2025-07-09T10:20:05Z',
                    leftPercent: 0,
                    widthPercent: 1.639,
                    startOffsetText: '+0ms',
                    endOffsetText: '+5.0s',
                    durationText: '5.0s',
                  },
                ],
              },
            ],
          },
          {
            phaseId: 'setup',
            focusKey: 'setup',
            label: 'Setup',
            fill: 'TIMELINE_FILL_SETUP',
            bracketLeftPercent: 1.639,
            bracketWidthPercent: 3.279,
            durationText: '10.0s',
            shareText: '3.3%',
            rows: [
              {
                label: 'Sending files to the lab',
                focusKey: 'setup',
                fill: 'TIMELINE_FILL_SETUP',
                durationText: '10.0s',
                shareText: '3.3%',
                bars: [
                  {
                    startTime: '2025-07-09T10:20:05Z',
                    endTime: '2025-07-09T10:20:15Z',
                    leftPercent: 1.639,
                    widthPercent: 3.279,
                    startOffsetText: '+5.0s',
                    endOffsetText: '+15.0s',
                    durationText: '10.0s',
                  },
                ],
              },
            ],
          },
          {
            phaseId: 'test',
            focusKey: 'test',
            label: 'Test run',
            fill: 'TIMELINE_FILL_TEST',
            bracketLeftPercent: 4.918,
            bracketWidthPercent: 93.443,
            durationText: '4m 45s',
            shareText: '93%',
            rows: [
              {
                label: 'AndroidInstrumentation',
                focusKey: 'test',
                fill: 'TIMELINE_FILL_TEST',
                durationText: '4m 45s',
                shareText: '93%',
                isEndedStep: true,
                resultTone: 'TIMELINE_RESULT_TONE_TIMEOUT',
                resultLabel: 'Timeout',
                endMarkLeftPercent: 98.361,
                bars: [
                  {
                    startTime: '2025-07-09T10:20:15Z',
                    endTime: '2025-07-09T10:25:00Z',
                    leftPercent: 4.918,
                    widthPercent: 93.443,
                    startOffsetText: '+15.0s',
                    endOffsetText: '+5m 0s',
                    durationText: '4m 45s',
                  },
                ],
              },
            ],
          },
          {
            phaseId: 'teardown',
            focusKey: 'teardown',
            label: 'Teardown',
            fill: 'TIMELINE_FILL_TEARDOWN',
            bracketLeftPercent: 98.361,
            bracketWidthPercent: 1.639,
            durationText: '5.0s',
            shareText: '1.6%',
            rows: [
              {
                label: 'Downloading generated files',
                focusKey: 'teardown',
                fill: 'TIMELINE_FILL_TEARDOWN',
                durationText: '5.0s',
                shareText: '1.6%',
                bars: [
                  {
                    startTime: '2025-07-09T10:25:00Z',
                    endTime: '2025-07-09T10:25:05Z',
                    leftPercent: 98.361,
                    widthPercent: 1.639,
                    startOffsetText: '+5m 0s',
                    endOffsetText: '+5m 5s',
                    durationText: '5.0s',
                  },
                ],
              },
            ],
          },
        ],
      },
    },
  },
  log: 'Test execution timed out after 5 minutes.',
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=resource.type%3D%22mobileharness_test%22%20AND%20labels.test_id%3D%22test-timeout-1%22',
};
