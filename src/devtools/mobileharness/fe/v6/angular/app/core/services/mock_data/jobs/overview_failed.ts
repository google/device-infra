import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/common_models';
import {JobFile} from '@deviceinfra/app/core/models/job_overview';
import {MockJobScenario} from '../models';

/** A mock job scenario representing a failed job. */
export const SCENARIO_JOB_FAILED: MockJobScenario = {
  id: 'job-failed-1',
  scenarioName: 'Failed Job',
  overview: {
    executionDetails: {
      user: 'dafeni',
      actualUser: 'dafeni',
      createTime: '2025-07-09T10:10:35Z',
      startTime: '2025-07-09T10:12:00Z',
      endTime: '2025-07-09T10:12:40Z',
    },
    session: {
      id: '4f1e3f2e-64ac-48be-adfb-c01a5ece8a58',
    },
    name: 'hello_mobile_harness_test_on_mh',
    config: {
      devices: {
        device: [
          {
            deviceType: 'AndroidRealDevice',
            driver: 'AndroidInstrumentation',
            dimensions: {
              'sdk_version': '33',
              'rooted': 'true',
            },
          },
        ],
      },
      settings: {
        totalTestCount: 3,
        priority: 'DEFAULT',
        testAttempts: 2,
        forceRetry: false,
        retryLevel: 'ERROR',
      },
      params: {
        'param_a': 'value_a',
      },
    },
    tests: {
      test: [
        {
          id: '29226bb4-6e61-455a-aad2-b68a06ffb841',
          name: 'HelloMobileHarnessTest#buttonText',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_FAIL,
          startTime: '2025-07-09T10:12:00Z',
          endTime: '2025-07-09T10:12:40Z',
          devices: {
            device: [{id: '43021FDAQ000UM'}],
          },
        },
      ],
    },
    id: 'c3578d2a-776d-49e3-b065-c66624b9d665',
    status: JobStatus.JOB_STATUS_DONE,
    result: JobResult.JOB_RESULT_FAIL,
    spongeLink: 'http://sponge2/c3578d2a-776d-49e3-b065-c66624b9d665',
    properties: {
      'job_failure_reason': 'test_failed',
    },
    troubleshooting: {
      resultCause: {
        error: [
          {
            message:
              'Instrumentation test failures in HelloMobileHarnessTest#buttonText',
            trace:
              'junit.framework.AssertionFailedError: Expected button text to be "Hello World", but was "Hello MobileHarness"\n\tat com.example.HelloMobileHarnessTest.buttonText(HelloMobileHarnessTest.java:45)',
          },
        ],
      },
      warnings: {
        warning: [
          {
            message:
              'Package installation warning on device: target SDK mismatch.',
            trace:
              'Warning: installPackage: target SDK 33 is newer than local device host platform.',
          },
        ],
      },
    },
    timingBreakdown: {
      summaryLines: [
        'Failed after 2m 5s: 1 failed across 1 test run.',
        '10.0s preparing, 1m 25s waiting for devices, 30.0s running.',
      ],
      originTime: '2025-07-09T10:10:35Z',
      finishTime: '2025-07-09T10:12:40Z',
      axis: {
        tickStepPercent: 24,
        ticks: [
          {
            leftPercent: 0,
            label: '0s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_START',
          },
          {
            leftPercent: 24,
            label: '+30s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_CENTER',
          },
          {
            leftPercent: 48,
            label: '+1m',
            alignment: 'TIMELINE_TICK_ALIGNMENT_CENTER',
          },
          {
            leftPercent: 72,
            label: '+1m 30s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_CENTER',
          },
          {
            leftPercent: 100,
            label: '+2m 5s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_END',
          },
        ],
      },
      splitSegments: [
        {
          focusKey: 'preparing',
          label: 'Preparing',
          fill: 'TIMELINE_FILL_SETUP',
          flexWeight: 10000,
          durationText: '10.0s',
          shareText: '8.0%',
        },
        {
          focusKey: 'waiting',
          label: 'Waiting for devices',
          fill: 'TIMELINE_FILL_WAIT',
          flexWeight: 85000,
          durationText: '1m 25s',
          shareText: '68%',
        },
        {
          focusKey: 'running',
          label: 'Running tests',
          fill: 'TIMELINE_FILL_TEST',
          flexWeight: 30000,
          durationText: '30.0s',
          shareText: '24%',
        },
      ],
      jobTimeline: {
        totalDurationText: '2m 5s',
        activityBars: [
          {
            focusKey: 'preparing',
            label: 'Preparing',
            fill: 'TIMELINE_FILL_SETUP',
            shareText: '8.0%',
            interval: {
              startTime: '2025-07-09T10:10:35Z',
              endTime: '2025-07-09T10:10:45Z',
              leftPercent: 0,
              widthPercent: 8,
              startOffsetText: '+0ms',
              endOffsetText: '+10.0s',
              durationText: '10.0s',
            },
          },
          {
            focusKey: 'waiting',
            label: 'Waiting for devices',
            fill: 'TIMELINE_FILL_WAIT',
            shareText: '68%',
            interval: {
              startTime: '2025-07-09T10:10:45Z',
              endTime: '2025-07-09T10:12:10Z',
              leftPercent: 8,
              widthPercent: 68,
              startOffsetText: '+10.0s',
              endOffsetText: '+1m 35s',
              durationText: '1m 25s',
            },
          },
          {
            focusKey: 'running',
            label: 'Running tests',
            fill: 'TIMELINE_FILL_TEST',
            shareText: '24%',
            interval: {
              startTime: '2025-07-09T10:12:10Z',
              endTime: '2025-07-09T10:12:40Z',
              leftPercent: 76,
              widthPercent: 24,
              startOffsetText: '+1m 35s',
              endOffsetText: '+2m 5s',
              durationText: '30.0s',
            },
          },
        ],
        jobSetup: {
          detail: 'Resolving files 7.2s',
          durationText: '10.0s',
          bars: [
            {
              focusKey: 'preparing',
              label: 'Preparing the job',
              fill: 'TIMELINE_FILL_SETUP',
              shareText: '8.0%',
              interval: {
                startTime: '2025-07-09T10:10:35Z',
                endTime: '2025-07-09T10:10:45Z',
                leftPercent: 0,
                widthPercent: 8,
                startOffsetText: '+0ms',
                endOffsetText: '+10.0s',
                durationText: '10.0s',
              },
            },
          ],
        },
        lanes: [
          {
            testId: '29226bb4-6e61-455a-aad2-b68a06ffb841',
            displayName: 'buttonText',
            fullName: 'HelloMobileHarnessTest#buttonText',
            hasRecord: true,
            deviceIds: ['43021FDAQ000UM'],
            resultTone: 'TIMELINE_RESULT_TONE_FAIL',
            resultLabel: 'Fail',
            waitedText: '1m 25s',
            ranCell: {recorded: {durationText: '30.0s'}},
            hasEndMark: true,
            endMarkLeftPercent: 100,
            bars: [
              {
                focusKey: 'waiting',
                fill: 'TIMELINE_FILL_WAIT',
                leftPercent: 8,
                widthPercent: 68,
              },
              {
                focusKey: 'running',
                fill: 'TIMELINE_FILL_SETUP',
                leftPercent: 76,
                widthPercent: 4,
              },
              {
                focusKey: 'running',
                fill: 'TIMELINE_FILL_TEST',
                leftPercent: 80,
                widthPercent: 16.8,
              },
              {
                focusKey: 'running',
                fill: 'TIMELINE_FILL_TEARDOWN',
                leftPercent: 96.8,
                widthPercent: 3.2,
              },
            ],
            tooltipRows: [
              {label: 'Result', value: 'Fail'},
              {label: 'Device', value: '43021FDAQ000UM'},
              {label: 'Waited for device', value: '1m 25s'},
              {label: 'Ran', value: '30.0s'},
            ],
          },
        ],
      },
    },
    fileExplorer: {
      cnsPath:
        '/cnsexample/sa-d/home/mobileharness/gateway/20260712/s_726e8c21-7e1c-4576-aa0f-51e0d4c3d86a/j_1845eb94-459b-4028-a9b4-0f13558fdc61/',
      files: [
        {
          path: 'job_output.txt',
          size: 16384,
          type: 'text/plain',
          content: 'test output log\nJob errored or test failed.',
        },
        {
          path: 'undeclared_outputs.zip',
          size: 140288,
          type: 'application/zip',
          content: '',
        },
      ] as unknown as JobFile[],
    },
  },
  log: '[10:12:00] Job started\n[10:12:05] Allocated device 43021FDAQ000UM\n[10:12:10] Running test-3\n[10:12:40] test-3 failed.\n[10:12:40] Job failed.',
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=resource.type%3D%22mobileharness_job%22%20AND%20labels.job_id%3D%22c3578d2a-776d-49e3-b065-c66624b9d665%22',
};
