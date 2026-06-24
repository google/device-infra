import {MockJobScenario} from '../models';

/** A mock job scenario representing a multi-device job. */
export const SCENARIO_JOB_MULTI_DEVICE: MockJobScenario = {
  id: 'job-multi-device',
  scenarioName: 'Multi-Device Job (Mobly)',
  overview: {
    id: 'c0ffee-beef-d00d-face-1234567890ab',
    title: 'Mobly Multi-Device Interop Test (Pixel 8 & Pixel 7)',
    status: 'DONE',
    result: 'PASS',
    user: 'dafeni',
    actualUser: 'dafeni',
    spongeLink: '#',
    sessionId: 'a1b2c3d4-e5f6-7890-abcd-ef0123456789',
    sessionTitle: 'Nightly Pixel 8 Pro Tests',
    sessionStatus: 'DONE',
    sessionResult: 'PASS',
    tests: [
      {
        id: 'test-003',
        title: 'Test #3',
        status: 'Passed',
        duration: '15s',
        device: 'Pixel8-A',
      },
      {
        id: 'test-004',
        title: 'Test #4',
        status: 'Passed',
        duration: '25s',
        device: 'Pixel8-A',
      },
    ],
    createTime: '2025-07-10T10:00:00Z',
    startTime: '2025-07-10T10:01:00Z',
    endTime: '2025-07-10T10:05:00Z',
    config: {
      core: {
        'Driver': 'moblyTest',
        'Decorator(s)': 'AndroidLogcatDecorator, WifiDecorator',
        'Total Test Count': 2,
        'Job Priority': 'HIGH',
      },
      retry: {
        'Job Timeout(s)': 1800,
        'Test Timeout(s)': 900,
        'Start Timeout(s)': 300,
        'Test Attempts': 1,
        'Force Retry': false,
        'Retry Level': 'ERROR',
      },
      devices: [
        {
          'Device Type': 'AndroidRealDevice',
          dimensions: {'model': 'pixel_8', 'os_version': '14', 'carrier': 'GoogleFi'},
          decorators: 'AndroidLogcatDecorator, WifiDecorator',
        },
        {
          'Device Type': 'AndroidRealDevice',
          dimensions: {'model': 'pixel_7', 'os_version': '13'},
          decorators: 'AndroidLogcatDecorator',
        },
      ],
      params: {
        'mobly_config_file': 'wifi_interop_spec.yaml',
        'wifi_band': '5GHz',
      },
    },
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
  cloudLogLink: '#',
};
