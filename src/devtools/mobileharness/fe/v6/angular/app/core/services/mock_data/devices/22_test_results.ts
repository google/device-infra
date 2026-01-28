/**
 * @fileoverview Mock data for device with test results.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {
  HealthinessStats,
  RecoveryTaskStats,
  TestResultStats,
} from '../../../models/device_stats';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'DEVICE_WITH_TEST_RESULTS';

const HEALTHINESS_STATS: HealthinessStats = {
  'dailyStats': [
    {
      'date': '2026-01-17',
      'healthinessSummary': {
        'inServiceBreakdown': [
          {
            'category': 'BUSY',
          },
          {
            'category': 'IDLE',
          },
        ],
        'outOfServiceBreakdown': [
          {
            'category': 'DIRTY',
          },
          {
            'category': 'DYING',
          },
          {
            'category': 'FAILED',
          },
          {
            'category': 'INIT',
          },
          {
            'category': 'LAMEDUCK',
          },
          {
            'category': 'MISSING',
          },
          {
            'category': 'OTHERS',
          },
          {
            'category': 'PREPPING',
          },
        ],
      },
    },
    {
      'date': '2026-01-18',
      'healthinessSummary': {
        'inServiceBreakdown': [
          {
            'category': 'BUSY',
          },
          {
            'category': 'IDLE',
          },
        ],
        'outOfServiceBreakdown': [
          {
            'category': 'DIRTY',
          },
          {
            'category': 'DYING',
          },
          {
            'category': 'FAILED',
          },
          {
            'category': 'INIT',
          },
          {
            'category': 'LAMEDUCK',
          },
          {
            'category': 'MISSING',
          },
          {
            'category': 'OTHERS',
          },
          {
            'category': 'PREPPING',
          },
        ],
      },
    },
    {
      'date': '2026-01-19',
      'healthinessSummary': {
        'inServiceBreakdown': [
          {
            'category': 'BUSY',
          },
          {
            'category': 'IDLE',
          },
        ],
        'outOfServiceBreakdown': [
          {
            'category': 'DIRTY',
          },
          {
            'category': 'DYING',
          },
          {
            'category': 'FAILED',
          },
          {
            'category': 'INIT',
          },
          {
            'category': 'LAMEDUCK',
          },
          {
            'category': 'MISSING',
          },
          {
            'category': 'OTHERS',
          },
          {
            'category': 'PREPPING',
          },
        ],
      },
    },
    {
      'date': '2026-01-20',
      'healthinessSummary': {
        'inServiceBreakdown': [
          {
            'category': 'BUSY',
          },
          {
            'category': 'IDLE',
          },
        ],
        'outOfServiceBreakdown': [
          {
            'category': 'DIRTY',
          },
          {
            'category': 'DYING',
          },
          {
            'category': 'FAILED',
          },
          {
            'category': 'INIT',
          },
          {
            'category': 'LAMEDUCK',
          },
          {
            'category': 'MISSING',
          },
          {
            'category': 'OTHERS',
          },
          {
            'category': 'PREPPING',
          },
        ],
      },
    },
    {
      'date': '2026-01-21',
      'healthinessSummary': {
        'inServiceBreakdown': [
          {
            'category': 'BUSY',
          },
          {
            'category': 'IDLE',
          },
        ],
        'outOfServiceBreakdown': [
          {
            'category': 'DIRTY',
          },
          {
            'category': 'DYING',
          },
          {
            'category': 'FAILED',
          },
          {
            'category': 'INIT',
          },
          {
            'category': 'LAMEDUCK',
          },
          {
            'category': 'MISSING',
          },
          {
            'category': 'OTHERS',
          },
          {
            'category': 'PREPPING',
          },
        ],
      },
    },
    {
      'date': '2026-01-22',
      'healthinessSummary': {
        'inServiceBreakdown': [
          {
            'category': 'BUSY',
          },
          {
            'category': 'IDLE',
          },
        ],
        'outOfServiceBreakdown': [
          {
            'category': 'DIRTY',
          },
          {
            'category': 'DYING',
          },
          {
            'category': 'FAILED',
          },
          {
            'category': 'INIT',
          },
          {
            'category': 'LAMEDUCK',
          },
          {
            'category': 'MISSING',
          },
          {
            'category': 'OTHERS',
          },
          {
            'category': 'PREPPING',
          },
        ],
      },
    },
    {
      'date': '2026-01-23',
      'healthinessSummary': {
        'inServiceBreakdown': [
          {
            'category': 'BUSY',
          },
          {
            'category': 'IDLE',
          },
        ],
        'outOfServiceBreakdown': [
          {
            'category': 'DIRTY',
          },
          {
            'category': 'DYING',
          },
          {
            'category': 'FAILED',
          },
          {
            'category': 'INIT',
          },
          {
            'category': 'LAMEDUCK',
          },
          {
            'category': 'MISSING',
          },
          {
            'category': 'OTHERS',
          },
          {
            'category': 'PREPPING',
          },
        ],
      },
    },
  ],
  'aggregatedStats': {
    'inServiceBreakdown': [
      {
        'category': 'BUSY',
      },
      {
        'category': 'IDLE',
      },
    ],
    'outOfServiceBreakdown': [
      {
        'category': 'DIRTY',
      },
      {
        'category': 'DYING',
      },
      {
        'category': 'FAILED',
      },
      {
        'category': 'INIT',
      },
      {
        'category': 'LAMEDUCK',
      },
      {
        'category': 'MISSING',
      },
      {
        'category': 'OTHERS',
      },
      {
        'category': 'PREPPING',
      },
    ],
  },
};
const TEST_RESULT_STATS: TestResultStats = {
  'dailyStats': [
    {
      'date': '2026-01-01',
      'pass': 10,
      'timeout': 5,
    },
    {
      'date': '2026-01-02',
    },
    {
      'date': '2026-01-03',
      'pass': 7,
      'timeout': 5,
    },
    {
      'date': '2026-01-04',
      'pass': 8,
      'timeout': 9,
    },
    {
      'date': '2026-01-05',
      'pass': 5,
      'timeout': 5,
    },
    {
      'date': '2026-01-06',
      'pass': 9,
      'error': 1,
      'timeout': 8,
    },
    {
      'date': '2026-01-07',
      'pass': 1,
      'timeout': 1,
    },
  ],
  'aggregatedStats': {
    'totalTests': 74,
    'completionStats': {
      'count': 40,
      'percent': 54.054054,
    },
    'nonCompletionStats': {
      'count': 34,
      'percent': 45.945946,
    },
    'completionBreakdown': [
      {
        'category': 'PASS',
        'stats': {
          'count': 40,
          'percent': 54.054054,
        },
      },
      {
        'category': 'FAIL',
        'stats': {},
      },
    ],
    'nonCompletionBreakdown': [
      {
        'category': 'ERROR',
        'stats': {
          'count': 1,
          'percent': 1.3513514,
        },
      },
      {
        'category': 'TIMEOUT',
        'stats': {
          'count': 33,
          'percent': 44.594593,
        },
      },
      {
        'category': 'ABORT',
        'stats': {},
      },
      {
        'category': 'SKIP',
        'stats': {},
      },
      {
        'category': 'UNKNOWN',
        'stats': {},
      },
    ],
  },
};
const RECOVERY_TASK_STATS: RecoveryTaskStats = {
  'dailyStats': [
    {
      'date': '2026-01-01',
      'success': 14,
    },
    {
      'date': '2026-01-02',
      'success': 14,
    },
    {
      'date': '2026-01-03',
      'success': 15,
    },
    {
      'date': '2026-01-04',
      'success': 14,
    },
    {
      'date': '2026-01-05',
      'success': 12,
      'fail': 5,
    },
    {
      'date': '2026-01-06',
      'success': 9,
    },
    {
      'date': '2026-01-07',
      'success': 1,
    },
  ],
  'aggregatedStats': {
    'totalTasks': 84,
    'outcomeBreakdown': [
      {
        'category': 'SUCCESS',
        'stats': {
          'count': 79,
          'percent': 94.04762,
        },
      },
      {
        'category': 'FAIL',
        'stats': {
          'count': 5,
          'percent': 5.952381,
        },
      },
    ],
  },
};

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-a-1.prod.example.com', ip: '192.168.1.101'},
  healthAndActivity: {
    title: 'In Service (Idle)',
    subtitle: 'The device is healthy and ready for new tasks.',
    state: 'IN_SERVICE_IDLE',
    deviceStatus: {status: 'IDLE', isCritical: false},
    deviceTypes: [
      'AndroidDevice',
      'AndroidFlashableDevice',
      'AndroidOnlineDevice',
      'AndroidRealDevice',
    ].map((type) => ({type, isAbnormal: false})),
    lastInServiceTime: new Date().toISOString(),
  },
  basicInfo: {
    model: 'Pixel 8 Pro',
    version: '14',
    form: 'physical',
    os: 'Android',
    batteryLevel: 95,
    network: {wifiRssi: -58, hasInternet: true},
    hardware: 'g/2345a',
    build: 'AP1A.240405.002',
  },
  permissions: {
    owners: ['user-a', 'group-infra-team', 'derekchen'],
    executors: ['test-runner-service-account', 'auto-recovery-service'],
  },
  capabilities: {
    supportedDrivers: [
      'AndroidGUnit',
      'AndroidInstrumentation',
      'AndroidMonkey',
      'AndroidRoboTest',
      'AndroidTradefedTest',
      'FlutterDriver',
      'MoblyAospTest',
      'MoblyTest',
    ],
    supportedDecorators: [
      'AndroidBugreportDecorator',
      'AndroidCrashMonitorDecorator',
      'AndroidFilePullerDecorator',
      'AndroidLogCatDecorator',
      'AndroidScreenshotDecorator',
    ],
  },
  dimensions: {
    supported: {
      'From Device Config': {
        dimensions: [
          {name: 'pool', value: 'pixel-prod'},
          {name: 'os', value: '14'},
        ],
      },
      'Detected by OmniLab': {
        dimensions: [
          {name: 'abi', value: 'arm64-v8a'},
          {name: 'abilist', value: 'arm64-v8a,armeabi-v7a,armeabi'},
          {
            name: 'baseband_version',
            value: 'qb5491625_s5318ap_erd_sgc,qb5491625_s5318ap_erd_sgc',
          },
          {name: 'battery_level', value: '97'},
          {name: 'battery_status', value: 'ok'},
        ],
      },
    },
    required: {
      'From Device Config': {
        dimensions: [{name: 'min-sdk', value: '33'}],
      },
    },
  },
  properties: {
    'test-type': 'instrumentation',
    'max-run-time': '3600',
    'network-requirement': 'full',
    'encryption-state': 'encrypted',
  },
};

const CONFIG: DeviceConfig = {
  permissions: {
    owners: ['user-a', 'group-infra-team', 'derekchen'],
    executors: ['test-runner-service-account', 'auto-recovery-service'],
  },
  wifi: {
    type: 'pre-configured',
    ssid: 'GoogleGuest',
    psk: 'some-secure-password',
    scanSsid: true,
  },
  dimensions: {
    supported: [
      {name: 'pool', value: 'pixel-prod'},
      {name: 'os', value: '14'},
    ],
    required: [{name: 'min-sdk', value: '33'}],
  },
  settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
};

/**
 * Represents a mock device scenario with test result stats.
 */
export const SCENARIO_TEST_RESULTS: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: '22. Device with Test Results',
  overview: OVERVIEW,
  config: CONFIG,
  isQuarantined: false,
  healthinessStats: HEALTHINESS_STATS,
  testResultStats: TEST_RESULT_STATS,
  recoveryTaskStats: RECOVERY_TASK_STATS,
  actionVisibility: {
    screenshot: true,
    logcat: true,
    flash: true,
    remoteControl: true,
    quarantine: true,
  },
};
