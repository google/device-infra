import {
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
} from '../../../models/common_models';
import {MockSessionScenario} from '../models';

/** A mock session scenario representing a failed session. */
export const SCENARIO_SESSION_FAILED: MockSessionScenario = {
  id: 'session-failed',
  scenarioName: 'Failed Session',
  overview: {
    id: '4f1e3f2e-64ac-48be-adfb-c01a5ece8a58',
    name: 'Gateway Run: MobileHarness',
    status: SessionStatus.SESSION_STATUS_DONE,
    result: SessionResult.SESSION_RESULT_FAIL,
    client: 'dafeni-client',
    version: '4.256.1',
    executor: '/bns/...',
    labels: ['label-1', 'label-2'],
    spongeLink: 'http://sponge',
    troubleshooting: {
      resultCause: {
        error: [
          {
            message: 'Session failed because one or more jobs failed.',
            trace: 'See individual job failures for more details.',
          },
        ],
      },
    },
    executionDetails: {
      user: 'dafeni',
      actualUser: 'dafeni',
      createTime: '2025-07-09T10:10:18Z',
      startTime: '2025-07-09T10:10:24Z',
      endTime: '2025-07-09T10:12:45Z',
    },
    properties: {
      'session_prop_1': 'session_value_1',
      'session_prop_2': 'session_value_2',
    },
    jobs: [
      {
        id: 'job-passed-1',
        name: 'hello_mobile_harness_test_on_mh',
        status: JobStatus.JOB_STATUS_DONE,
        result: JobResult.JOB_RESULT_PASS,
        startTime: '2025-07-09T10:11:05Z',
        endTime: '2025-07-09T10:11:40Z',
      },
      {
        id: 'job-failed-1',
        name: 'com.google.android.gm.GmailInstrumentationTest',
        status: JobStatus.JOB_STATUS_DONE,
        result: JobResult.JOB_RESULT_FAIL,
        startTime: '2025-07-09T10:12:00Z',
        endTime: '2025-07-09T10:12:40Z',
      },
    ],
  },
  log: '[10:10:18] Session created\n[10:10:24] Session started\n[10:11:00] Job job-passed-1 started\n[10:12:00] Job job-failed-1 started\n[10:12:40] Job job-failed-1 failed, marking session as failed.\n[10:12:45] Session finished.',
};
