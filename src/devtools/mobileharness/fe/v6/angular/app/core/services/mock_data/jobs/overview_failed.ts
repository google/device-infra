import {MockJobScenario} from '../models';

/** A mock job scenario representing a failed job. */
export const SCENARIO_JOB_FAILED: MockJobScenario = {
  id: 'job-failed-1',
  scenarioName: 'Failed Job',
  overview: {
    id: 'c3578d2a-776d-49e3-b065-c66624b9d665',
    title: 'com.google.android.gm.GmailInstrumentationTest',
    status: 'DONE',
    result: 'FAIL',
    user: 'dafeni',
    actualUser: 'dafeni',
    spongeLink: '#',
    sessionId: '4f1e3f2e-64ac-48be-adfb-c01a5ece8a58',
    sessionTitle: 'Gateway Run: MobileHarness',
    sessionStatus: 'DONE',
    sessionResult: 'FAIL',
    error: {
      message: 'Job failed due to test failure.',
      trace: 'See test 29226bb4... for detailed stack trace.',
    },
    warnings: {
      message: 'Device allocation took longer than expected',
      trace:
        'WARNING [10:10:45] Allocation attempt 1 timed out.\nWARNING [10:11:15] Allocation attempt 2 succeeded but signal strength is low (-85dBm).',
    },
    tests: [
      {
        id: '29226bb4-6e61-455a-aad2-b68a06ffb841',
        title: 'com.google.android.gm.GmailInstrumentationTest#testSync',
        status: 'Failed',
        duration: '30s',
        device: '43021FDAQ000UM',
      },
    ],
    createTime: '2025-07-09T10:10:35Z',
    startTime: '2025-07-09T10:12:00Z',
    endTime: '2025-07-09T10:12:40Z',
    config: {
      core: {
        'Device Type': 'AndroidRealDevice',
        'Driver': 'AndroidInstrumentation',
        'Decorator(s)': 'AndroidLogcatDecorator',
        'Total Test Count': 1,
        'Job Priority': 'HIGH',
      },
      retry: {
        'Job Timeout(s)': 900,
        'Test Timeout(s)': 600,
        'Start Timeout(s)': 300,
        'Test Attempts': 1,
        'Force Retry': false,
        'Retry Level': 'ERROR',
      },
      dimensions: {
        'model': 'pixel_8',
      },
      params: {},
    },
    properties: {
      'job_failure_reason': 'test_failed',
    },
    timingBreakdown: {
      createTime: '2025-07-09T10:10:35Z',
      startTime: '2025-07-09T10:12:00Z',
      endTime: '2025-07-09T10:12:40Z',
      stages: [
        {
          name: 'Allocation',
          startTime: '2025-07-09T10:10:35Z',
          endTime: '2025-07-09T10:12:00Z',
        },
        {
          name: 'Pre-run Job',
          startTime: '2025-07-09T10:12:00Z',
          endTime: '2025-07-09T10:12:10Z',
        },
        {
          name: 'Running Tests',
          startTime: '2025-07-09T10:12:10Z',
          endTime: '2025-07-09T10:12:40Z',
        },
      ],
    },
  },
  log: '[10:12:00] Job started\n[10:12:05] Allocated device 43021FDAQ000UM\n[10:12:10] Running test-3\n[10:12:40] test-3 failed.\n[10:12:40] Job failed.',
  cloudLogLink: '#',
};
