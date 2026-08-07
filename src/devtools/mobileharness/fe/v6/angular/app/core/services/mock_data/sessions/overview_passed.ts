import {
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
} from '../../../models/common_models';
import {MockSessionScenario} from '../models';

/** A mock session scenario representing a passed session with files. */
export const SCENARIO_SESSION_PASSED: MockSessionScenario = {
  id: 'session-passed',
  scenarioName: 'Passed Session',
  overview: {
    id: 'a1b2c3d4-e5f6-7890-ghij-klmnopqrstuv',
    name: 'Nightly Pixel 8 Pro Tests',
    status: SessionStatus.SESSION_STATUS_DONE,
    result: SessionResult.SESSION_RESULT_PASS,
    client: 'cron-scheduler',
    version: '1.0.0',
    executor: '/bns/...',
    labels: ['nightly', 'pixel8'],
    spongeLink: 'http://sponge',
    executionDetails: {
      user: 'test-runner-service',
      actualUser: 'test-runner-service',
      createTime: '2025-07-10T02:00:00Z',
      startTime: '2025-07-10T02:00:05Z',
      endTime: '2025-07-10T02:30:10Z',
    },
    properties: {
      'trigger': 'nightly_cron',
    },
    jobs: [
      {
        id: 'job-passed-2',
        name: 'PixelCameraQualityTest',
        status: JobStatus.JOB_STATUS_DONE,
        result: JobResult.JOB_RESULT_PASS,
        startTime: '2025-07-10T02:01:00Z',
        endTime: '2025-07-10T02:15:00Z',
      },
      {
        id: 'job-passed-3',
        name: 'SystemPerformanceBenchmark',
        status: JobStatus.JOB_STATUS_DONE,
        result: JobResult.JOB_RESULT_PASS,
        startTime: '2025-07-10T02:15:05Z',
        endTime: '2025-07-10T02:30:00Z',
      },
    ],
    fileExplorer: {
      cnsPath:
        '/cns-fake/lz-d/home/dafeng/mobileharness/experiments/run_session_1',
      files: [
        {
          path: 'session_run.log',
          size: 2048,
          viewable: true,
        },
        {
          path: 'summary.yaml',
          size: 512,
          viewable: true,
        },
      ],
    },
  },
  log: 'All jobs completed successfully.',
};
