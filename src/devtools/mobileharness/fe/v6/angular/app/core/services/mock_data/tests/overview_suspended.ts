import {MockTestScenario} from '../models';

/** A mock test scenario representing a suspended test. */
export const SCENARIO_TEST_SUSPENDED: MockTestScenario = {
  id: 'test-suspended-1',
  scenarioName: 'Suspended Test',
  overview: {
    id: '5e79a0bf-ec5f-4fcc-b0cd-571097e234c0',
    name: 'com.google.photos.PhotosTest#quotaTest',
    status: 'SUSPENDED',
    result: 'UNKNOWN',
    user: 'blaze-user',
    actualUser: 'mobileharness-ci-runner',
    jobId: 'a1e8e532-0ac4-4d89-8260-8012626e2e4f',
    jobName: 'standalone_blaze_test_run',
    jobStatus: 'Done',
    jobResult: 'PASS',
    deviceId: '4D5A1FDAB001BB',
    deviceType: 'AndroidRealDevice',
    hostName: '4b-cm-01-12-02.acs.google.com',
    hostIp: '100.107.202.19',
    createTime: '2025-07-09T11:40:00Z',
    properties: {},
    timingBreakdown: {
      createTime: '2025-07-09T11:40:00Z',
      stages: [],
    },
  },
  log: 'Test suspended. Waiting for device quota...',
  cloudLogLink: '#',
};
