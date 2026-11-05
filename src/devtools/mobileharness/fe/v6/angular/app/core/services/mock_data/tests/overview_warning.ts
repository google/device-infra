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
        },
      ],
    },
    host: {
      name: '3a-cm-10-18-01.acs.example.com',
      ip: '100.107.200.155',
    },
    executionDetails: {
      createTime: '2025-07-09T10:11:15Z',
      startTime: '2025-07-09T10:11:15Z',
      endTime: '2025-07-09T10:11:25Z',
      updateTime: '2025-07-09T10:11:25Z',
      user: 'dafeni',
      actualUser: 'dafeni@example.com',
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
      summaryLines: [
        'Passed in 10.0s: 5.0s setup, 4.0s test run, 1.0s teardown.',
      ],
      originTime: '2025-07-09T10:11:15Z',
      finishTime: '2025-07-09T10:11:25Z',
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
            label: '+5s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_CENTER',
          },
          {
            leftPercent: 100,
            label: '+10s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_END',
          },
        ],
      },
      splitSegments: [
        {
          focusKey: 'setup',
          label: 'Setup',
          fill: 'TIMELINE_FILL_SETUP',
          flexWeight: 5000,
          durationText: '5.0s',
          shareText: '50%',
        },
        {
          focusKey: 'test',
          label: 'Test run',
          fill: 'TIMELINE_FILL_TEST',
          flexWeight: 4000,
          durationText: '4.0s',
          shareText: '40%',
        },
        {
          focusKey: 'teardown',
          label: 'Teardown',
          fill: 'TIMELINE_FILL_TEARDOWN',
          flexWeight: 1000,
          durationText: '1.0s',
          shareText: '10%',
        },
      ],
      testTimeline: {
        phases: [
          {
            phaseId: 'setup',
            focusKey: 'setup',
            label: 'Setup',
            fill: 'TIMELINE_FILL_SETUP',
            stepCountText: '2 steps',
            bracketLeftPercent: 0,
            bracketWidthPercent: 50,
            durationText: '5.0s',
            shareText: '50%',
            rows: [
              {
                label: 'Sending files to the lab',
                focusKey: 'setup',
                fill: 'TIMELINE_FILL_SETUP',
                durationText: '3.0s',
                shareText: '30%',
                bars: [
                  {
                    startTime: '2025-07-09T10:11:15Z',
                    endTime: '2025-07-09T10:11:18Z',
                    leftPercent: 0,
                    widthPercent: 30,
                    startOffsetText: '+0ms',
                    endOffsetText: '+3.0s',
                    durationText: '3.0s',
                  },
                ],
              },
              {
                label: 'Preparing the device on the lab',
                focusKey: 'setup',
                fill: 'TIMELINE_FILL_SETUP',
                durationText: '2.0s',
                shareText: '20%',
                bars: [
                  {
                    startTime: '2025-07-09T10:11:18Z',
                    endTime: '2025-07-09T10:11:20Z',
                    leftPercent: 30,
                    widthPercent: 20,
                    startOffsetText: '+3.0s',
                    endOffsetText: '+5.0s',
                    durationText: '2.0s',
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
            bracketLeftPercent: 50,
            bracketWidthPercent: 40,
            durationText: '4.0s',
            shareText: '40%',
            rows: [
              {
                label: 'AndroidInstrumentation',
                focusKey: 'test',
                fill: 'TIMELINE_FILL_TEST',
                durationText: '4.0s',
                shareText: '40%',
                bars: [
                  {
                    startTime: '2025-07-09T10:11:20Z',
                    endTime: '2025-07-09T10:11:24Z',
                    leftPercent: 50,
                    widthPercent: 40,
                    startOffsetText: '+5.0s',
                    endOffsetText: '+9.0s',
                    durationText: '4.0s',
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
            bracketLeftPercent: 90,
            bracketWidthPercent: 10,
            durationText: '1.0s',
            shareText: '10%',
            rows: [
              {
                label: 'Cleaning up on the lab',
                focusKey: 'teardown',
                fill: 'TIMELINE_FILL_TEARDOWN',
                durationText: '1.0s',
                shareText: '10%',
                bars: [
                  {
                    startTime: '2025-07-09T10:11:24Z',
                    endTime: '2025-07-09T10:11:25Z',
                    leftPercent: 90,
                    widthPercent: 10,
                    startOffsetText: '+9.0s',
                    endOffsetText: '+10.0s',
                    durationText: '1.0s',
                  },
                ],
              },
            ],
          },
        ],
      },
    },
  },
  log: 'Test passed with warnings.',
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=resource.type%3D%22mobileharness_test%22%20AND%20labels.test_id%3D%22test-warning-1%22',
};
