import {MockJobScenario} from '../models';

/** A mock job scenario representing an aborted job. */
export const SCENARIO_JOB_ABORTED: MockJobScenario = {
  id: 'job-aborted-1',
  scenarioName: 'Aborted Job',
  overview: {
    id: 'f1a8e532-0ac4-4d89-8260-8012626e2e4f',
    title: 'adhoc_g3_ci_cancellation_run',
    status: 'DONE',
    result: 'ABORT',
    user: 'blaze-user',
    actualUser: 'g3-ci-system',
    spongeLink: '#',
    tests: [],
    createTime: '2025-07-09T12:00:00Z',
    startTime: '2025-07-09T12:00:05Z',
    endTime: '2025-07-09T12:02:00Z',
    config: {
      core: {
        'Device Type': 'AndroidRealDevice',
        'Driver': 'AndroidInstrumentation',
      },
      retry: {},
      dimensions: {},
      params: {},
    },
    properties: {
      'aborted_by': 'g3-ci-scheduler',
    },
    timingBreakdown: {
      createTime: '2025-07-09T12:00:00Z',
      startTime: '2025-07-09T12:00:05Z',
      endTime: '2025-07-09T12:02:00Z',
      stages: [],
    },
  },
  log: '[12:00:05] Job started\n[12:01:00] Allocating devices...\n[12:02:00] ABORT: Cancelled by user or upstream scheduler.\n[12:02:00] Job aborted.',
  cloudLogLink: '#',
};
