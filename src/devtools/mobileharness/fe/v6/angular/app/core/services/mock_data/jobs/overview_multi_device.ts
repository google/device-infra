import {
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
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
      name: 'Mobly Cross-Device Test Suite',
      status: SessionStatus.SESSION_STATUS_DONE,
      result: SessionResult.SESSION_RESULT_PASS,
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
            device: [
              {id: 'Pixel-8-A', type: 'AndroidRealDevice'},
              {id: 'Pixel-7-B', type: 'AndroidRealDevice'},
            ],
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
            device: [
              {id: 'Pixel-8-A', type: 'AndroidRealDevice'},
              {id: 'Pixel-7-B', type: 'AndroidRealDevice'},
            ],
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
      createTime: '2025-07-10T10:00:00Z',
      startTime: '2025-07-10T10:01:00Z',
      endTime: '2025-07-10T10:05:00Z',
      stages: [
        {
          name: 'Allocation',
          startTime: '2025-07-10T10:00:00Z',
          endTime: '2025-07-10T10:01:00Z',
        },
        {
          name: 'Mobly Setup',
          startTime: '2025-07-10T10:01:00Z',
          endTime: '2025-07-10T10:01:05Z',
        },
        {
          name: 'Running Tests',
          startTime: '2025-07-10T10:01:05Z',
          endTime: '2025-07-10T10:05:00Z',
        },
      ],
    },
  },
  log: '[10:01:00] Mobly test started\n[10:01:05] Device 1 allocated: Pixel-8-A\n[10:01:10] Device 2 allocated: Pixel-7-B\n[10:01:15] Running Mobly test suite...\n[10:05:00] All tests passed.',
  cloudLogLink:
    'https://console.cloud.google.com/logs/query;query=resource.type%3D%22mobileharness_job%22%20AND%20labels.job_id%3D%22c0ffee-beef-d00d-face-1234567890ab%22',
};
