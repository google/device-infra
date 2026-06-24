import {MockTestScenario} from '../models';

/** A mock test scenario representing a timeout test. */
export const SCENARIO_TEST_TIMEOUT: MockTestScenario = {
  id: 'test-timeout-1',
  scenarioName: 'Timeout Test',
  overview: {
    id: '7b99a0bf-ec5f-4fcc-b0cd-571097e234c0',
    name: 'com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#hangTest',
    status: 'DONE',
    result: 'TIMEOUT',
    user: 'dafeni',
    jobId: 'b65cadd7-6ad6-440e-a3b7-bfe1948557e6',
    jobName: 'hello_mobile_harness_test_on_mh',
    jobStatus: 'Done',
    jobResult: 'FAIL',
    deviceId: '99061FFAZ004AA',
    deviceType: 'AndroidRealDevice',
    hostName: '3a-cm-10-18-01.acs.google.com',
    hostIp: '100.107.200.155',
    createTime: '2025-07-09T10:20:00Z',
    startTime: '2025-07-09T10:20:05Z',
    endTime: '2025-07-09T10:25:05Z',
    properties: {},
    timingBreakdown: {
      createTime: '2025-07-09T10:20:00Z',
      startTime: '2025-07-09T10:20:05Z',
      endTime: '2025-07-09T10:25:05Z',
      stages: [],
    },
  },
  log: 'Test execution timed out after 5 minutes.',
  cloudLogLink: '#',
};
