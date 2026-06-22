import {MockTestScenario} from '../models';

/** A mock test scenario representing a failed test. */
export const SCENARIO_TEST_FAILED: MockTestScenario = {
  id: 'test-failed-1',
  scenarioName: 'Failed Test (with Stack Trace)',
  log: '....\n[10:12:39] ANDROID_INSTRUMENTATION_INSTALL_APK_ERROR: Failed to install on internal storage\n....',
  cloudLogLink: '#',
  overview: {
    id: '29226bb4-6e61-455a-aad2-b68a06ffb841',
    name: 'com.google.android.gm.GmailInstrumentationTest#testSync',
    status: 'DONE',
    result: 'FAIL',
    user: 'dafeni',
    jobId: 'c3578d2a-776d-49e3-b065-c66624b9d665',
    jobName: 'com.google.android.gm.GmailInstrumentationTest',
    jobStatus: 'Done',
    jobResult: 'FAIL',
    deviceId: '43021FDAQ000UM',
    deviceType: 'AndroidRealDevice',
    hostName: 'mt31-dm01-a-x04.moma.google.com',
    hostIp: '100.107.201.12',
    createTime: '2025-07-09T10:12:10Z',
    startTime: '2025-07-09T10:12:10Z',
    endTime: '2025-07-09T10:12:40Z',
    properties: {
      'abi': 'arm64-v8a',
    },
    error: {
      message:
        'ANDROID_INSTRUMENTATION_INSTALL_APK_ERROR: Failed to install on internal storage',
      trace:
        'MobileHarnessException: Failed to install on internal storage\n    at com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.v2.ApkInstaller.install(ApkInstaller.java:232)\n    ... 23 more\nCaused by: com.google.devtools.mobileharness.api.model.error.MobileHarnessException: Failure [INSTALL_FAILED_DEPRECATED_SDK_VERSION: App package must target at least SDK version 23, but found 7]\n',
    },
    timingBreakdown: {
      createTime: '2025-07-09T10:12:10Z',
      startTime: '2025-07-09T10:12:10Z',
      endTime: '2025-07-09T10:12:40Z',
      stages: [
        {
          name: 'Pre-run Test',
          startTime: '2025-07-09T10:12:10Z',
          endTime: '2025-07-09T10:12:15Z',
        },
        {
          name: 'Run Test:AndroidInstrumentation',
          startTime: '2025-07-09T10:12:15Z',
          endTime: '2025-07-09T10:12:40Z',
        },
      ],
    },
  },
};
