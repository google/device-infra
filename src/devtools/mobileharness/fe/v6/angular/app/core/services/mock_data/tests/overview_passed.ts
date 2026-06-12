import {MockTestScenario} from '../models';

/** A mock test scenario representing a passed test. */
export const SCENARIO_TEST_PASSED: MockTestScenario = {
  id: 'test-passed-1',
  scenarioName: 'Passed Test',
  overview: {
    id: 'c0a4b01d-b7f9-4008-adf9-26d1a70e5f61',
    name: 'com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#buttonText',
    status: 'DONE',
    result: 'PASS',
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
    startTime: '2025-07-09T10:11:15Z',
    endTime: '2025-07-09T10:11:25Z',
    properties: {
      'dimension_rooted': 'false',
      'abibaseband_version': '0c-250327-250401-b-13296697,g5400c-25032',
    },
    timingBreakdown: {
      createTime: '2025-07-09T10:11:15Z',
      startTime: '2025-07-09T10:11:15Z',
      endTime: '2025-07-09T10:11:25Z',
      stages: [
        {
          name: 'Pre-run Test',
          startTime: '2025-07-09T10:11:15Z',
          endTime: '2025-07-09T10:11:17Z',
        },
        {
          name: 'Run Test:AndroidInstrumentation',
          startTime: '2025-07-09T10:11:17Z',
          endTime: '2025-07-09T10:11:25Z',
        },
      ],
    },
  },
  log: 'Test passed successfully.',
  cloudLogLink: '#',
};
