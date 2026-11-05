import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';
import {MockTestScenario} from '../models';

/** A mock test scenario representing a test with various files populated. */
export const SCENARIO_TEST_FILES: MockTestScenario = {
  id: 'test-with-files-id-1234',
  scenarioName: 'Test With Files',
  overview: {
    id: 'test-with-files-id-1234',
    name: 'com.google.devsite.test.TestWithFiles#run',
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
      user: 'dafeng',
      actualUser: 'dafeng',
    },
    properties: {
      'dimension_rooted': 'false',
    },
    timingBreakdown: {
      summaryLines: ['Passed in 10.0s: 4.0s setup, 6.0s test run.'],
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
          flexWeight: 4000,
          durationText: '4.0s',
          shareText: '40%',
        },
        {
          focusKey: 'test',
          label: 'Test run',
          fill: 'TIMELINE_FILL_TEST',
          flexWeight: 6000,
          durationText: '6.0s',
          shareText: '60%',
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
            bracketWidthPercent: 40,
            durationText: '4.0s',
            shareText: '40%',
            rows: [
              {
                label: 'Sending files to the lab',
                focusKey: 'setup',
                fill: 'TIMELINE_FILL_SETUP',
                durationText: '4.0s',
                shareText: '40%',
                bars: [
                  {
                    startTime: '2025-07-09T10:11:15Z',
                    endTime: '2025-07-09T10:11:19Z',
                    leftPercent: 0,
                    widthPercent: 40,
                    startOffsetText: '+0ms',
                    endOffsetText: '+4.0s',
                    durationText: '4.0s',
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
            bracketLeftPercent: 40,
            bracketWidthPercent: 60,
            durationText: '6.0s',
            shareText: '60%',
            rows: [
              {
                label: 'TestWithFiles',
                focusKey: 'test',
                fill: 'TIMELINE_FILL_TEST',
                durationText: '6.0s',
                shareText: '60%',
                bars: [
                  {
                    startTime: '2025-07-09T10:11:19Z',
                    endTime: '2025-07-09T10:11:25Z',
                    leftPercent: 40,
                    widthPercent: 60,
                    startOffsetText: '+4.0s',
                    endOffsetText: '+10.0s',
                    durationText: '6.0s',
                  },
                ],
              },
            ],
          },
        ],
      },
    },
    fileExplorer: {
      cnsPath: '/cns-fake/lz-d/home/dafeng/mobileharness/experiments/run_1',
      files: [
        {
          path: 'test_run.log',
          size: 1048,
          viewable: true,
        },
        {
          path: 'screenshot.png',
          size: 143520,
          viewable: false,
        },
        {
          path: 'nested/folder/config.json',
          size: 429,
          viewable: true,
        },
        {
          path: 'nested/folder/output.txt',
          size: 12054,
          viewable: true,
        },
        {
          path: 'large_file.db',
          size: 25 * 1024 * 1024, // 25MB
          viewable: false,
        },
      ],
    },
  },
  log: '[10:11:15] Loading files...\n[10:11:25] Test execution complete.',
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=labels.test_id%3D%22test-with-files-id-1234%22',
};
