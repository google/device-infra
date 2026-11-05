import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';
import {MockTestScenario} from '../models';

/** A mock test scenario representing a error test. */
export const SCENARIO_TEST_ERROR: MockTestScenario = {
  id: 'test-error-1',
  scenarioName: 'Test with Infrastructure Error',
  overview: {
    id: 'test-error-1',
    name: 'com.google.devtools.mobileharness.infra.HarnessErrorTest#testSetup',
    status: TestStatus.TEST_STATUS_DONE,
    result: TestResult.TEST_RESULT_ERROR,
    job: {
      id: 'c3578d2a-776d-49e3-b065-c66624b9d665',
      name: 'com.google.android.gm.GmailInstrumentationTest',
      status: JobStatus.JOB_STATUS_DONE,
      result: JobResult.JOB_RESULT_ERROR,
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
      createTime: '2025-07-09T10:15:00Z',
      startTime: '2025-07-09T10:15:05Z',
      endTime: '2025-07-09T10:15:10Z',
      updateTime: '2025-07-09T10:15:10Z',
      user: 'dafeni',
      actualUser: 'dafeni@example.com',
    },
    properties: {
      'infra_version': 'v6.2.1',
    },
    troubleshooting: {
      resultCause: {
        error: [
          {
            message: 'DEVICE_DETECTION_ERROR: Failed to detect USB connection',
            trace:
              'HarnessException: Failed to detect USB connection\\n  at com.google.devtools.mobileharness.infra.detector.UsbDetector.detect(UsbDetector.java:45)',
          },
        ],
      },
    },
    timingBreakdown: {
      summaryLines: [
        'Stopped with an error after 10.0s, during Preparing the device on the lab (+10.0s from start). The test driver never started.',
        '5.0s waiting to start, 5.0s setup.',
      ],
      originTime: '2025-07-09T10:15:00Z',
      finishTime: '2025-07-09T10:15:10Z',
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
          focusKey: 'wait',
          label: 'Waiting to start',
          fill: 'TIMELINE_FILL_WAIT',
          flexWeight: 5000,
          durationText: '5.0s',
          shareText: '50%',
        },
        {
          focusKey: 'setup',
          label: 'Setup',
          fill: 'TIMELINE_FILL_SETUP',
          flexWeight: 5000,
          durationText: '5.0s',
          shareText: '50%',
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
            bracketWidthPercent: 50,
            durationText: '5.0s',
            shareText: '50%',
            rows: [
              {
                label: 'Waiting for a device',
                focusKey: 'wait',
                fill: 'TIMELINE_FILL_WAIT',
                durationText: '5.0s',
                shareText: '50%',
                bars: [
                  {
                    startTime: '2025-07-09T10:15:00Z',
                    endTime: '2025-07-09T10:15:05Z',
                    leftPercent: 0,
                    widthPercent: 50,
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
            bracketLeftPercent: 50,
            bracketWidthPercent: 50,
            durationText: '5.0s',
            shareText: '50%',
            rows: [
              {
                label: 'Preparing the device on the lab',
                focusKey: 'setup',
                fill: 'TIMELINE_FILL_SETUP',
                durationText: '5.0s',
                shareText: '50%',
                isEndedStep: true,
                resultTone: 'TIMELINE_RESULT_TONE_FAIL',
                resultLabel: 'Error',
                endMarkLeftPercent: 100,
                errorMessage:
                  'DEVICE_DETECTION_ERROR: Failed to detect USB connection',
                bars: [
                  {
                    startTime: '2025-07-09T10:15:05Z',
                    endTime: '2025-07-09T10:15:10Z',
                    leftPercent: 50,
                    widthPercent: 50,
                    startOffsetText: '+5.0s',
                    endOffsetText: '+10.0s',
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
  log: 'Infrastructure error occurred.',
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=resource.type%3D%22mobileharness_test%22%20AND%20labels.test_id%3D%22test-error-1%22',
};
