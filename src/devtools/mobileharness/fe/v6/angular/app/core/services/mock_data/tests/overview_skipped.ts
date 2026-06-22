import {MockTestScenario} from '../models';

/** A mock test scenario representing a skipped test. */
export const SCENARIO_TEST_SKIPPED: MockTestScenario = {
  id: 'test-skipped-1',
  scenarioName: 'Skipped Test',
  overview: {
    id: '6a89a0bf-ec5f-4fcc-b0cd-571097e234c0',
    name: 'com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#ignoredTest',
    status: 'DONE',
    result: 'SKIP',
    user: 'dafeni',
    jobId: 'b65cadd7-6ad6-440e-a3b7-bfe1948557e6',
    jobName: 'hello_mobile_harness_test_on_mh',
    jobStatus: 'Done',
    jobResult: 'PASS',
    deviceId: '99061FFAZ004AA',
    deviceType: 'AndroidRealDevice',
    hostName: '3a-cm-10-18-01.acs.google.com',
    hostIp: '100.107.200.155',
    createTime: '2025-07-09T10:11:15Z',
    endTime: '2025-07-09T10:11:16Z',
    properties: {},
    timingBreakdown: {
      createTime: '2025-07-09T10:11:15Z',
      endTime: '2025-07-09T10:11:16Z',
      stages: [],
    },
  },
  log: 'Test skipped due to @Ignore annotation.',
  cloudLogLink: '#',
};
