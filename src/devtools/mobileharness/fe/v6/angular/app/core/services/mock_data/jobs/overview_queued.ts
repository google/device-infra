import {MockJobScenario} from '../models';

/** A mock job scenario representing a queued job. */
export const SCENARIO_JOB_QUEUED: MockJobScenario = {
  id: 'job-queued-1',
  scenarioName: 'Queued Job',
  overview: {
    id: '99e8e532-0ac4-4d89-8260-8012626e2e4f',
    title: 'scheduled_daily_smoke_tests',
    status: 'NEW',
    result: 'UNKNOWN',
    user: 'dafeni',
    actualUser: 'dafeni',
    sessionId: 'a1b2c3d4-e5f6-7890-abcd-ef0123456789',
    sessionTitle: 'Nightly Pixel 8 Pro Tests',
    sessionStatus: 'DONE',
    sessionResult: 'PASS',
    tests: [],
    createTime: '2025-07-10T03:00:00Z',
    config: {
      core: {
        'Device Type': 'AndroidRealDevice',
        'Driver': 'AndroidInstrumentation',
        'Total Test Count': 5,
        'Job Priority': 'LOW',
      },
      retry: {},
      dimensions: {
        'sdk_version': '34',
      },
      params: {},
    },
    properties: {
      'trigger': 'cron',
    },
    timingBreakdown: {
      createTime: '2025-07-10T03:00:00Z',
      stages: [],
    },
  },
  log: 'Waiting in queue for device allocation...',
  cloudLogLink: '#',
};
