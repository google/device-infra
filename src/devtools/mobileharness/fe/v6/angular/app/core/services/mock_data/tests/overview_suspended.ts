import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';
import {MockTestScenario} from '../models';

/** A mock test scenario representing a suspended test. */
export const SCENARIO_TEST_SUSPENDED: MockTestScenario = {
  id: 'test-suspended-1',
  scenarioName: 'Suspended Test',
  overview: {
    id: 'test-suspended-1',
    name: 'com.google.photos.PhotosTest#quotaTest',
    status: TestStatus.TEST_STATUS_SUSPENDED,
    result: TestResult.TEST_RESULT_UNSPECIFIED,
    job: {
      id: 'a1e8e532-0ac4-4d89-8260-8012626e2e4f',
      name: 'standalone_blaze_test_run',
      status: JobStatus.JOB_STATUS_DONE,
      result: JobResult.JOB_RESULT_PASS,
      spongeLink: 'http://sponge/mock-job-link',
    },
    devices: {
      device: [
        {
          id: '4D5A1FDAB001BB',
          type: 'AndroidRealDevice',
        },
      ],
    },
    host: {
      name: '4b-cm-01-12-02.acs.google.com',
      ip: '100.107.202.19',
    },
    executionDetails: {
      createTime: '2025-07-09T11:40:00Z',
      updateTime: '2025-07-09T11:45:00Z',
      user: 'blaze-user',
      actualUser: 'mobileharness-ci-runner',
    },
    properties: {},
    timingBreakdown: {
      createTime: '2025-07-09T11:40:00Z',
      stages: [],
    },
  },
  log: 'Test suspended. Waiting for device quota...',
  cloudLogLink: '#',
};
