import {MockTestScenario} from '../models';

/** A mock test scenario representing a error test. */
export const SCENARIO_TEST_ERROR: MockTestScenario = {
  id: 'test-error-1',
  scenarioName: 'Test with Infrastructure Error',
  overview: {
    id: '8c19a0bf-ec5f-4fcc-b0cd-571097e234c0',
    name: 'com.google.devtools.mobileharness.infra.HarnessErrorTest#testSetup',
    status: 'DONE',
    result: 'ERROR',
    user: 'dafeni',
    jobId: 'c3578d2a-776d-49e3-b065-c66624b9d665',
    jobName: 'com.google.android.gm.GmailInstrumentationTest',
    jobStatus: 'Done',
    jobResult: 'ERROR',
    deviceId: '43021FDAQ000UM',
    deviceType: 'AndroidRealDevice',
    hostName: 'mt31-dm01-a-x04.moma.google.com',
    hostIp: '100.107.201.12',
    createTime: '2025-07-09T10:15:00Z',
    startTime: '2025-07-09T10:15:05Z',
    endTime: '2025-07-09T10:15:10Z',
    properties: {
      'infra_version': 'v6.2.1',
    },
    error: {
      message: 'DEVICE_DETECTION_ERROR: Failed to detect USB connection',
      trace:
        'HarnessException: Failed to detect USB connection\n  at com.google.devtools.mobileharness.infra.detector.UsbDetector.detect(UsbDetector.java:45)',
    },
    timingBreakdown: {
      createTime: '2025-07-09T10:15:00Z',
      startTime: '2025-07-09T10:15:05Z',
      endTime: '2025-07-09T10:15:10Z',
      stages: [],
    },
  },
  log: 'Infrastructure error occurred.',
  cloudLogLink: '#',
};
