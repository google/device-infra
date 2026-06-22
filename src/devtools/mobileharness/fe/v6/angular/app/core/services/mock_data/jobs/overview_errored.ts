import {MockJobScenario} from '../models';

/** A mock job scenario representing an errored job. */
export const SCENARIO_JOB_ERRORED: MockJobScenario = {
  id: 'job-errored-1',
  scenarioName: 'Errored Job',
  overview: {
    id: 'e9a8e532-0ac4-4d89-8260-8012626e2e4f',
    title: 'mh_infrastructure_diagnostics_job',
    status: 'DONE',
    result: 'ERROR',
    user: 'dafeni',
    actualUser: 'dafeni',
    spongeLink: '#',
    sessionId: '4f1e3f2e-64ac-48be-adfb-c01a5ece8a58',
    sessionTitle: 'Gateway Run: MobileHarness',
    sessionStatus: 'DONE',
    sessionResult: 'FAIL',
    tests: [],
    createTime: '2025-07-09T10:15:00Z',
    startTime: '2025-07-09T10:15:05Z',
    endTime: '2025-07-09T10:15:10Z',
    error: {
      message:
        'LAB_SERVER_OFFLINE: Failed to establish communication with Lab Server.',
      trace:
        'MobileHarnessException: Lab Server mt31-dm01-a-x04 is offline.\n  at com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.RemoteAllocator.allocate(RemoteAllocator.java:102)',
    },
    config: {
      core: {
        'Device Type': 'AndroidRealDevice',
        'Driver': 'AndroidInstrumentation',
        'Total Test Count': 0,
        'Job Priority': 'DEFAULT',
      },
      retry: {},
      dimensions: {},
      params: {},
    },
    properties: {
      'infra_error_code': 'LAB_SERVER_OFFLINE',
    },
    timingBreakdown: {
      createTime: '2025-07-09T10:15:00Z',
      startTime: '2025-07-09T10:15:05Z',
      endTime: '2025-07-09T10:15:10Z',
      stages: [],
    },
  },
  log: '[10:15:05] Job started\n[10:15:05] Initializing lab server connection...\n[10:15:10] ERROR: Lab server is offline, unable to allocate devices.\n[10:15:10] Job errored.',
  cloudLogLink: '#',
};
