import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';
import {MockTestScenario} from '../models';

/** A mock test scenario representing a long name test. */
export const SCENARIO_TEST_LONG_NAME: MockTestScenario = {
  id: 'test-longname-1',
  scenarioName: 'Test with Long Name',
  overview: {
    id: 'test-longname-1',
    name: 'com.google.android.apps.someteam.super.long.package.name.indicating.deep.hierarchy.for.this.particular.test.suite.manifest.class.GmailInstrumentationTest#testSyncWithAVeryLongMethodNameThatExceedsNormalExpectationsForTestingWrappingAndTruncationBehaviorInTheUI',
    status: TestStatus.TEST_STATUS_DONE,
    result: TestResult.TEST_RESULT_PASS,
    job: {
      id: 'd789e532-0bc4-4d89-8260-8012626e2e4f',
      name: 'com.google.android.apps.someteam.super.long.package.name.indicating.deep.hierarchy.for.this.particular.test.suite.manifest.class',
      status: JobStatus.JOB_STATUS_DONE,
      result: JobResult.JOB_RESULT_PASS,
      spongeLink: 'http://sponge2/d789e532-0bc4-4d89-8260-8012626e2e4f',
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
    properties: {},
    timingBreakdown: {
      summaryLines: ['Passed in 10.0s: 2.0s setup, 8.0s test run.'],
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
          flexWeight: 2000,
          durationText: '2.0s',
          shareText: '20%',
        },
        {
          focusKey: 'test',
          label: 'Test run',
          fill: 'TIMELINE_FILL_TEST',
          flexWeight: 8000,
          durationText: '8.0s',
          shareText: '80%',
        },
      ],
      testTimeline: {
        phases: [
          {
            phaseId: 'setup',
            focusKey: 'setup',
            label: 'Setup',
            fill: 'TIMELINE_FILL_SETUP',
            bracketLeftPercent: 0,
            bracketWidthPercent: 20,
            durationText: '2.0s',
            shareText: '20%',
            rows: [
              {
                label: 'Preparing the device on the lab',
                focusKey: 'setup',
                fill: 'TIMELINE_FILL_SETUP',
                durationText: '2.0s',
                shareText: '20%',
                bars: [
                  {
                    startTime: '2025-07-09T10:11:15Z',
                    endTime: '2025-07-09T10:11:17Z',
                    leftPercent: 0,
                    widthPercent: 20,
                    startOffsetText: '+0ms',
                    endOffsetText: '+2.0s',
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
            bracketLeftPercent: 20,
            bracketWidthPercent: 80,
            durationText: '8.0s',
            shareText: '80%',
            rows: [
              {
                label: 'AndroidInstrumentation',
                focusKey: 'test',
                fill: 'TIMELINE_FILL_TEST',
                durationText: '8.0s',
                shareText: '80%',
                bars: [
                  {
                    startTime: '2025-07-09T10:11:17Z',
                    endTime: '2025-07-09T10:11:25Z',
                    leftPercent: 20,
                    widthPercent: 80,
                    startOffsetText: '+2.0s',
                    endOffsetText: '+10.0s',
                    durationText: '8.0s',
                  },
                ],
              },
            ],
          },
        ],
      },
    },
  },
  log: 'Passed.',
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=resource.type%3D%22mobileharness_test%22%20AND%20labels.test_id%3D%22test-long-name%22',
};
