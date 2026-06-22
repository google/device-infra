import {MockJobScenario} from '../models';

/** A mock job scenario representing an in-progress job. */
export const SCENARIO_JOB_IN_PROGRESS: MockJobScenario = {
  id: 'job-inprogress-1',
  scenarioName: 'In-Progress Standalone Job',
  overview: {
    id: 'a1e8e532-0ac4-4d89-8260-8012626e2e4f',
    title: 'standalone_blaze_test_run',
    status: 'RUNNING',
    result: 'UNKNOWN',
    user: 'blaze-user',
    actualUser: 'mobileharness-ci-runner',
    spongeLink: '#',
    tests: [
      {
        id: 'fdda90bf-ec5f-4fcc-b0cd-571097e234c0',
        title: 'com.google.photos.PhotosTest#uploadTest',
        status: 'In Progress',
        duration: '45s',
        device: '4D5A1FDAB001BB',
      },
    ],
    createTime: '2025-07-09T11:30:00Z',
    startTime: '2025-07-09T11:30:05Z',
    config: {
      core: {
        'Device Type': 'AndroidRealDevice',
        'Driver': 'AndroidInstrumentation',
        'Decorator(s)': 'AndroidLogcatDecorator',
        'Total Test Count': 1,
        'Job Priority': 'DEFAULT',
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
        'product': 'flame',
      },
      params: {},
    },
    properties: {},
    timingBreakdown: {
      createTime: '2025-07-09T11:30:00Z',
      startTime: '2025-07-09T11:30:05Z',
      stages: [
        {
          name: 'Allocation',
          startTime: '2025-07-09T11:30:00Z',
          endTime: '2025-07-09T11:30:05Z',
        },
        {
          name: 'Pre-run Job',
          startTime: '2025-07-09T11:30:05Z',
          endTime: '2025-07-09T11:30:10Z',
        },
        {
          name: 'Running Tests',
          startTime: '2025-07-09T11:30:10Z',
        },
      ],
    },
  },
  log: '[11:30:05] Job started\n[11:30:10] Allocated device 4D5A1FDAB001BB\n[11:30:15] Running test fdda90bf... ',
  cloudLogLink: '#',
};
