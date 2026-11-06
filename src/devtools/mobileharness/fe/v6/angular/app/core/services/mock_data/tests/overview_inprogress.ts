import {
  JobStatus,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';
import {MockTestScenario} from '../models';

/** A mock test scenario representing a test in progress. */
export const SCENARIO_TEST_IN_PROGRESS: MockTestScenario = {
  id: 'test-inprogress-1',
  scenarioName: 'Test in Progress',
  log: `[09:30:15] Starting MobileHarness active test...
[09:30:16] Preparing device 43021FDAQ000UM...
[09:30:17] Installing Gmail instrumentation package...
[09:30:18] Running instrumentation test case: testRun...
[09:30:20] Photo upload task started.
[09:30:22] Uploading image chunk 1/3 (512KB)...
[09:30:24] Uploading image chunk 2/3 (512KB)...
[09:30:26] Uploading image chunk 3/3 (256KB)...
[09:30:28] Photo upload completed successfully.
[09:30:29] Verification passed. Saving results.`,
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=resource.type%3D%22mobileharness_test%22%20AND%20labels.test_id%3D%22test-inprogress-1%22',
  overview: {
    id: 'test-inprogress-1',
    name: 'com.google.devtools.mobileharness.infra.HarnessActiveTest#testRun',
    status: TestStatus.TEST_STATUS_RUNNING,
    job: {
      id: 'c3578d2a-776d-49e3-b065-c66624b9d665',
      name: 'com.google.android.gm.GmailInstrumentationTest',
      status: JobStatus.JOB_STATUS_RUNNING,
      spongeLink: 'http://sponge2/c3578d2a-776d-49e3-b065-c66624b9d665',
    },
    devices: {
      device: [
        {
          id: '43021FDAQ000UM',
        },
      ],
    },
    host: {
      name: 'mt31-dm01-a-x04.moma.example.com',
      ip: '100.107.201.12',
    },
    executionDetails: {
      createTime: '2025-07-09T11:30:10Z',
      startTime: '2025-07-09T11:30:15Z',
      endTime: '2025-07-09T11:30:18Z',
      updateTime: '2025-07-09T11:30:18Z',
      user: 'dafeni',
      actualUser: 'dafeni@example.com',
    },
    properties: {
      'infra_version': 'v6.2.1',
    },
    timingBreakdown: {
      summaryLines: ['Running for 8.0s: 5.0s waiting to start, 3.0s setup.'],
      originTime: '2025-07-09T11:30:10Z',
      finishTime: '2025-07-09T11:30:18Z',
      live: true,
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
            label: '+4s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_CENTER',
          },
          {
            leftPercent: 100,
            label: '+8s',
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
          shareText: '63%',
        },
        {
          focusKey: 'setup',
          label: 'Setup',
          fill: 'TIMELINE_FILL_SETUP',
          flexWeight: 3000,
          durationText: '3.0s',
          shareText: '38%',
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
            bracketWidthPercent: 62.5,
            durationText: '5.0s',
            shareText: '63%',
            rows: [
              {
                label: 'Waiting for a device',
                focusKey: 'wait',
                fill: 'TIMELINE_FILL_WAIT',
                durationText: '5.0s',
                shareText: '63%',
                bars: [
                  {
                    startTime: '2025-07-09T11:30:10Z',
                    endTime: '2025-07-09T11:30:15Z',
                    leftPercent: 0,
                    widthPercent: 62.5,
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
            bracketLeftPercent: 62.5,
            bracketWidthPercent: 37.5,
            durationText: '3.0s',
            shareText: '38%',
            rows: [
              {
                label: 'Preparing on the client',
                focusKey: 'setup',
                fill: 'TIMELINE_FILL_SETUP',
                durationText: '3.0s',
                shareText: '38%',
                bars: [
                  {
                    startTime: '2025-07-09T11:30:15Z',
                    endTime: '2025-07-09T11:30:18Z',
                    leftPercent: 62.5,
                    widthPercent: 37.5,
                    startOffsetText: '+5.0s',
                    endOffsetText: '+8.0s',
                    durationText: '3.0s',
                  },
                ],
              },
            ],
          },
        ],
      },
    },
  },
};
