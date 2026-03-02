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
      'date': {year: 2026, month: 1, day: 17},
      'healthinessSummary': {
        'inServiceBreakdown': [{'category': 'BUSY'}, {'category': 'IDLE'}],
        'outOfServiceBreakdown': [
          {'category': 'DIRTY'},
          {'category': 'DYING'},
          {'category': 'FAILED'},
          {'category': 'INIT'},
          {'category': 'LAMEDUCK'},
          {'category': 'MISSING'},
          {'category': 'OTHERS'},
          {'category': 'PREPPING'},
        ],
      },
    },
    {
      'date': {year: 2026, month: 1, day: 18},
      'healthinessSummary': {
        'inServiceBreakdown': [{'category': 'BUSY'}, {'category': 'IDLE'}],
        'outOfServiceBreakdown': [
          {'category': 'DIRTY'},
          {'category': 'DYING'},
          {'category': 'FAILED'},
          {'category': 'INIT'},
          {'category': 'LAMEDUCK'},
          {'category': 'MISSING'},
          {'category': 'OTHERS'},
          {'category': 'PREPPING'},
        ],
      },
    },
    {
      'date': {year: 2026, month: 1, day: 19},
      'healthinessSummary': {
        'inServiceBreakdown': [{'category': 'BUSY'}, {'category': 'IDLE'}],
        'outOfServiceBreakdown': [
          {'category': 'DIRTY'},
          {'category': 'DYING'},
          {'category': 'FAILED'},
          {'category': 'INIT'},
          {'category': 'LAMEDUCK'},
          {'category': 'MISSING'},
          {'category': 'OTHERS'},
          {'category': 'PREPPING'},
        ],
      },
    },
    {
      'date': {year: 2026, month: 1, day: 20},
      'healthinessSummary': {
        'inServiceBreakdown': [{'category': 'BUSY'}, {'category': 'IDLE'}],
        'outOfServiceBreakdown': [
          {'category': 'DIRTY'},
          {'category': 'DYING'},
          {'category': 'FAILED'},
          {'category': 'INIT'},
          {'category': 'LAMEDUCK'},
          {'category': 'MISSING'},
          {'category': 'OTHERS'},
          {'category': 'PREPPING'},
        ],
      },
    },
    {
      'date': {year: 2026, month: 1, day: 21},
      'healthinessSummary': {
        'inServiceBreakdown': [{'category': 'BUSY'}, {'category': 'IDLE'}],
        'outOfServiceBreakdown': [
          {'category': 'DIRTY'},
          {'category': 'DYING'},
          {'category': 'FAILED'},
          {'category': 'INIT'},
          {'category': 'LAMEDUCK'},
          {'category': 'MISSING'},
          {'category': 'OTHERS'},
          {'category': 'PREPPING'},
        ],
      },
    },
    {
      'date': {year: 2026, month: 1, day: 22},
      'healthinessSummary': {
        'inServiceBreakdown': [{'category': 'BUSY'}, {'category': 'IDLE'}],
        'outOfServiceBreakdown': [
          {'category': 'DIRTY'},
          {'category': 'DYING'},
          {'category': 'FAILED'},
          {'category': 'INIT'},
          {'category': 'LAMEDUCK'},
          {'category': 'MISSING'},
          {'category': 'OTHERS'},
          {'category': 'PREPPING'},
        ],
      },
    },
    {
      'date': {year: 2026, month: 1, day: 23},
      'healthinessSummary': {
        'inServiceBreakdown': [{'category': 'BUSY'}, {'category': 'IDLE'}],
        'outOfServiceBreakdown': [
          {'category': 'DIRTY'},
          {'category': 'DYING'},
          {'category': 'FAILED'},
          {'category': 'INIT'},
          {'category': 'LAMEDUCK'},
          {'category': 'MISSING'},
          {'category': 'OTHERS'},
          {'category': 'PREPPING'},
        ],
      },
    },
  ],
  'aggregatedStats': {
    'inServiceBreakdown': [{'category': 'BUSY'}, {'category': 'IDLE'}],
    'outOfServiceBreakdown': [
      {'category': 'DIRTY'},
      {'category': 'DYING'},
      {'category': 'FAILED'},
      {'category': 'INIT'},
      {'category': 'LAMEDUCK'},
      {'category': 'MISSING'},
      {'category': 'OTHERS'},
      {'category': 'PREPPING'},
    ],
  },
};

const TEST_RESULT_STATS: TestResultStats = {
  'dailyStats': [
    {
      'date': {year: 2026, month: 1, day: 1},
      'totalCount': 15,
      'categoryStats': [
        {
          'category': 'TEST_RESULT_CATEGORY_PASS',
          'stats': {'count': 10, 'percent': 66.6},
        },
        {
          'category': 'TEST_RESULT_CATEGORY_TIMEOUT',
          'stats': {'count': 5, 'percent': 33.3},
        },
      ],
    },
    {
      'date': {year: 2026, month: 1, day: 2},
      'totalCount': 0,
      'categoryStats': [],
    },
    {
      'date': {year: 2026, month: 1, day: 3},
      'totalCount': 12,
      'categoryStats': [
        {
          'category': 'TEST_RESULT_CATEGORY_PASS',
          'stats': {'count': 7, 'percent': 58.3},
        },
        {
          'category': 'TEST_RESULT_CATEGORY_TIMEOUT',
          'stats': {'count': 5, 'percent': 41.7},
        },
      ],
    },
    {
      'date': {year: 2026, month: 1, day: 4},
      'totalCount': 17,
      'categoryStats': [
        {
          'category': 'TEST_RESULT_CATEGORY_PASS',
          'stats': {'count': 8, 'percent': 47},
        },
        {
          'category': 'TEST_RESULT_CATEGORY_TIMEOUT',
          'stats': {'count': 9, 'percent': 53},
        },
      ],
    },
    {
      'date': {year: 2026, month: 1, day: 5},
      'totalCount': 10,
      'categoryStats': [
        {
          'category': 'TEST_RESULT_CATEGORY_PASS',
          'stats': {'count': 5, 'percent': 50},
        },
        {
          'category': 'TEST_RESULT_CATEGORY_TIMEOUT',
          'stats': {'count': 5, 'percent': 50},
        },
      ],
    },
    {
      'date': {year: 2026, month: 1, day: 6},
      'totalCount': 18,
      'categoryStats': [
        {
          'category': 'TEST_RESULT_CATEGORY_PASS',
          'stats': {'count': 9, 'percent': 50},
        },
        {
          'category': 'TEST_RESULT_CATEGORY_ERROR',
          'stats': {'count': 1, 'percent': 5.5},
        },
        {
          'category': 'TEST_RESULT_CATEGORY_TIMEOUT',
          'stats': {'count': 8, 'percent': 44.4},
        },
      ],
    },
    {
      'date': {year: 2026, month: 1, day: 7},
      'totalCount': 2,
      'categoryStats': [
        {
          'category': 'TEST_RESULT_CATEGORY_PASS',
          'stats': {'count': 1, 'percent': 50},
        },
        {
          'category': 'TEST_RESULT_CATEGORY_TIMEOUT',
          'stats': {'count': 1, 'percent': 50},
        },
      ],
    },
  ],
  'summary': {
    'totalCount': 74,
    'completionGroup': {
      'displayName': 'Completion',
      'totalStats': {'count': 40, 'percent': 54.05},
      'breakdownItems': [
        {
          'category': 'TEST_RESULT_CATEGORY_PASS',
          'stats': {'count': 40, 'percent': 54.05},
        },
      ],
    },
    'nonCompletionGroup': {
      'displayName': 'Non-Completion',
      'totalStats': {'count': 34, 'percent': 45.95},
      'breakdownItems': [
        {
          'category': 'TEST_RESULT_CATEGORY_ERROR',
          'stats': {'count': 1, 'percent': 1.35},
        },
        {
          'category': 'TEST_RESULT_CATEGORY_TIMEOUT',
          'stats': {'count': 33, 'percent': 44.6},
        },
      ],
    },
    'unknownGroup': {
      'displayName': 'Unknown',
      'totalStats': {'count': 0, 'percent': 0},
      'breakdownItems': [],
    },
  },
};

const RECOVERY_TASK_STATS: RecoveryTaskStats = {
  'dailyStats': [
    {
      'date': {year: 2026, month: 1, day: 1},
      'totalCount': 14,
      'categoryStats': [
        {
          'category': 'RECOVERY_OUTCOME_CATEGORY_SUCCESS',
          'stats': {'count': 14, 'percent': 100},
        },
      ],
    },
    {
      'date': {year: 2026, month: 1, day: 2},
      'totalCount': 14,
      'categoryStats': [
        {
          'category': 'RECOVERY_OUTCOME_CATEGORY_SUCCESS',
          'stats': {'count': 14, 'percent': 100},
        },
      ],
    },
    {
      'date': {year: 2026, month: 1, day: 3},
      'totalCount': 15,
      'categoryStats': [
        {
          'category': 'RECOVERY_OUTCOME_CATEGORY_SUCCESS',
          'stats': {'count': 15, 'percent': 100},
        },
      ],
    },
    {
      'date': {year: 2026, month: 1, day: 4},
      'totalCount': 14,
      'categoryStats': [
        {
          'category': 'RECOVERY_OUTCOME_CATEGORY_SUCCESS',
          'stats': {'count': 14, 'percent': 100},
        },
      ],
    },
    {
      'date': {year: 2026, month: 1, day: 5},
      'totalCount': 17,
      'categoryStats': [
        {
          'category': 'RECOVERY_OUTCOME_CATEGORY_SUCCESS',
          'stats': {'count': 12, 'percent': 70.6},
        },
        {
          'category': 'RECOVERY_OUTCOME_CATEGORY_FAIL',
          'stats': {'count': 5, 'percent': 29.4},
        },
      ],
    },
    {
      'date': {year: 2026, month: 1, day: 6},
      'totalCount': 9,
      'categoryStats': [
        {
          'category': 'RECOVERY_OUTCOME_CATEGORY_SUCCESS',
          'stats': {'count': 9, 'percent': 100},
        },
      ],
    },
    {
      'date': {year: 2026, month: 1, day: 7},
      'totalCount': 1,
      'categoryStats': [
        {
          'category': 'RECOVERY_OUTCOME_CATEGORY_SUCCESS',
          'stats': {'count': 1, 'percent': 100},
        },
      ],
    },
  ],
  'summary': {
    'totalCount': 84,
    'outcomeBreakdown': [
      {
        'category': 'RECOVERY_OUTCOME_CATEGORY_SUCCESS',
        'stats': {'count': 79, 'percent': 94.05},
      },
      {
        'category': 'RECOVERY_OUTCOME_CATEGORY_FAIL',
        'stats': {'count': 5, 'percent': 5.95},
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
