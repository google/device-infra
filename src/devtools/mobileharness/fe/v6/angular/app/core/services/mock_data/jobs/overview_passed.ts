import {
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/common_models';
import {JobFile} from '@deviceinfra/app/core/models/job_overview';
import {MockJobScenario} from '../models';

/** A mock job scenario representing a passed job. */
export const SCENARIO_JOB_PASSED: MockJobScenario = {
  id: 'job-passed-1',
  scenarioName: 'Passed Job',
  overview: {
    executionDetails: {
      user: 'dafeni',
      actualUser: 'dafeni',
      createTime: '2025-07-09T10:10:30Z',
      startTime: '2025-07-09T10:11:05Z',
      endTime: '2025-07-09T10:11:40Z',
    },
    session: {
      id: '4f1e3f2e-64ac-48be-adfb-c01a5ece8a58',
      name: 'Gateway Run: MobileHarness',
      status: SessionStatus.SESSION_STATUS_DONE,
      result: SessionResult.SESSION_RESULT_FAIL,
    },
    name: 'hello_mobile_harness_test_on_mh',
    config: {
      devices: {
        device: [
          {
            deviceType: 'AndroidRealDevice',
            driver: 'AndroidInstrumentation',
            dimensions: {
              'sdk_version': '33',
              'rooted': 'true',
            },
          },
        ],
      },
      settings: {
        totalTestCount: 2,
        priority: 'DEFAULT',
        testAttempts: 2,
        forceRetry: false,
        retryLevel: 'ERROR',
      },
      params: {
        'param_a': 'value_a',
        'long_param_name_for_testing_wrapping':
          'a_very_long_value_that_should_wrap_properly',
      },
    },
    tests: {
      test: [
        {
          id: 'c0a4b01d-b7f9-4008-adf9-26d1a70e5f61',
          name: 'HelloMobileHarnessTest#buttonText',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2025-07-09T10:11:05Z',
          endTime: '2025-07-09T10:11:15Z',
          devices: {
            device: [{id: '99061FFAZ004AA', type: 'AndroidRealDevice'}],
          },
        },
        {
          id: 'f52aeb45-f80d-4072-870e-4d01b90f09ec',
          name: 'HelloMobileHarnessTest#customTestArgs',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2025-07-09T10:11:05Z',
          endTime: '2025-07-09T10:11:15Z',
          devices: {
            device: [{id: '99061FFAZ004AA', type: 'AndroidRealDevice'}],
          },
        },
      ],
    },
    id: 'b65cadd7-6ad6-440e-a3b7-bfe1948557e6',
    status: JobStatus.JOB_STATUS_DONE,
    result: JobResult.JOB_RESULT_PASS,
    spongeLink: 'http://sponge2/b65cadd7-6ad6-440e-a3b7-bfe1948557e6',
    properties: {
      'job_prop_1': 'job_value_1',
    },
    timingBreakdown: {
      createTime: '2025-07-09T10:10:30Z',
      startTime: '2025-07-09T10:11:05Z',
      endTime: '2025-07-09T10:11:40Z',
      stages: [
        {
          name: 'Allocation',
          startTime: '2025-07-09T10:10:30Z',
          endTime: '2025-07-09T10:11:05Z',
        },
        {
          name: 'Pre-run Job',
          startTime: '2025-07-09T10:11:05Z',
          endTime: '2025-07-09T10:11:10Z',
        },
        {
          name: 'Running Tests',
          startTime: '2025-07-09T10:11:10Z',
          endTime: '2025-07-09T10:11:40Z',
        },
      ],
    },
    fileExplorer: {
      cnsPath:
        '/cnsexample/ok-d/home/mobileharness/session_tmp/s_726e8c21-7e1c-4576-aa0f-51e0d4c3d86a/j_1845eb94-459b-4028-a9b4-0f13558fdc61',
      files: [
        {
          path: 'job_output.txt',
          size: 16384,
          type: 'text/plain',
          content:
            'Talking to master: stubby_server_locator { server_spec: "gslb:mobileharness-masterv5-api-prod:20:" } grpc_server_locator { hostname: "mobileharnessmasterv5-pa.googleapis.com" }\n2026-07-12 23:51:54:121 PDT I Loaded internal plugin: com.google.userplatform.accountprovider.mobileharness.TestAccountProviderClientPlugin\n2026-07-12 23:51:54:121 PDT I Loaded internal plugin: com.google.devtools.mobileharness.infra.client.api.plugin.utp.UtpCommandHistoryRenderer\n2026-07-12 23:51:54:121 PDT I Loaded internal plugin: com.google.devtools.mobileharness.infra.client.api.plugin.utp.UserUtpConfigValidator\n2026-07-12 23:51:54:122 PDT I Loaded internal plugin: com.google.devtools.mobileharness.infra.client.api.plugin.orchestration.AutoLoginAccountProviderClientPlugin\n2026-07-12 23:51:54:122 PDT I Loaded internal plugin: com.google.devtools.mobileharness.infra.client.api.plugin.resultstore.ResultStoreUploaderPlugin\n2026-07-12 23:51:54:122 PDT I Loaded API plugin: com.google.devtools.mobileharness.service.gateway.controller.SessionRunnerImpl\n2026-07-12 23:51:54:122 PDT I Started job 1845eb94-459b-4028-a9b4-0f13558fdc61\n2026-07-12 23:51:54:122 PDT I TIMELINE:PRE_RUN_JOB:START:1845eb94-459b-4028-a9b4-0f13558fdc61 Job started\n2026-07-12 23:51:54:123 PDT I TIMELINE:FILE_RESOLVE:START:1845eb94-459b-4028-a9b4-0f13558fdc61 File resolve started\n2026-07-12 23:51:54:127 PDT I Successfully resolved build_apk:hello_mobile_harness.apk with [/tmp/mobileharness-gateway/tmp/mh_gateway/session_tmp/s_726e8c21-7e1c-4576-aa0f-51e0d4c3d86a/j_1845eb94-459b-4028-a9b4-0f13558fdc61/run/google3/java/com/google/codelab/mobileharness/android/hellomobileharness/hello_mobile_harness.apk]\n2026-07-12 23:51:54:128 PDT I Successfully resolved test_apk:hello_mobile_harness_test.apk with [/tmp/mobileharness-gateway/tmp/mh_gateway/session_tmp/s_726e8c21-7e1c-4576-aa0f-51e0d4c3d86a/j_1845eb94-459b-4028-a9b4-0f13558fdc61/run/google3/javatests/com/google/codelab/mobileharness/android/hellomobileharness/hello_mobile_harness_test.apk]\n2026-07-12 23:51:54:128 PDT I Successfully resolved test_data:example_test_data.txt with [/tmp/mobileharness-gateway/tmp/mh_gateway/session_tmp/s_726e8c21-7e1c-4576-aa0f-51e0d4c3d86a/j_1845eb94-459b-4028-a9b4-0f13558fdc61/run/google3/javatests/com/google/codelab/mobileharness/android/hellomobileharness/example_test_data.txt]\n2026-07-12 23:51:54:129 PDT I TIMELINE:FILE_RESOLVE:END:1845eb94-459b-4028-a9b4-0f13558fdc61 File resolve finished\n2026-07-12 23:51:54:129 PDT I Loading client jar plugins for job 1845eb94-459b-4028-a9b4-0f13558fdc61\n2026-07-12 23:51:54:129 PDT I Loading plugins from jars []\n2026-07-12 23:51:54:129 PDT I No plugin module class name given, scanning the jars to search plugin module classes by plugin module annotation\n2026-07-12 23:51:54:132 PDT I No plugin class name given, searching plugin class by plugin annotation\n2026-07-12 23:51:54:132 PDT I Plugin creator has already scanned all jars: [[]]\n2026-07-12 23:51:54:132 PDT I No plugin is loaded\n2026-07-12 23:52:03:406 PDT I Generated tests:\n- com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#accessTestData\n- com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#addStatsToSponge\n- com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#buttonText\n- com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#customTestArgs\n- com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#outputFile\n- com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#plusOneButton\n- com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#takeScreenshot\n\n2026-07-12 23:52:03:407 PDT I Fetching hybrid UTP mode config: /google_src/files/head/depot/google3/devtools/mobileharness/infra/controller/test/local/utp/config/config.textproto\n2026-07-12 23:52:03:419 PDT I Add job 1845eb94-459b-4028-a9b4-0f13558fdc61 to Moss\n2026-07-12 23:52:32:442 PDT I Device allocation finished\n2026-07-12 23:53:17:484 PDT I Job done\n2026-07-12 23:53:21:538 PDT I The job hello_mobile_harness_test_on_mh PASS!',
        },
        {
          path: 'undeclared_outputs.zip',
          size: 140288,
          type: 'application/zip',
          content: '',
        },
        {
          path: 'undetermined_size_manifest.txt',
          type: 'text/plain',
          content: 'This is a file with undetermined size.',
        },
        {
          path: 'logs/device_1.log',
          size: 46080,
          type: 'Log File',
          content: '[DEVICE 1] Init\n[DEVICE 1] App launched\n',
        },
        {
          path: 'hybrid_utp_mode/config.textproto',
          size: 512,
          type: 'text/plain',
          content: 'hybrid_utm_mode: true\noption: 1',
        },
        {
          path: 'large_system_logs.log',
          size: 15728640, // 15 MB
          type: 'text/plain',
          content: '',
        },
        {
          path: 'undeclared_outputs_annotations.pb',
          size: 184320,
          type: 'application/x-protobuf',
          content: '',
        },
      ] as unknown as JobFile[],
    },
  },
  log: '[10:11:05] Job started\n[10:11:10] Allocated device 99061FFAZ004AA\n[10:11:15] Running test-1\n[10:11:25] test-1 passed\n[10:11:30] Running test-2\n[10:11:40] test-2 passed\n[10:11:40] Job finished successfully.',
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=resource.type%3D%22mobileharness_job%22%20AND%20labels.job_id%3D%22b65cadd7-6ad6-440e-a3b7-bfe1948557e6%22',
};
