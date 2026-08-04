import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';
import {MockTestScenario} from '../models';

/** A mock test scenario representing a test with various files populated. */
export const SCENARIO_TEST_FILES: MockTestScenario = {
  id: 'test-with-files-id-1234',
  scenarioName: 'Test With Files',
  overview: {
    id: 'test-with-files-id-1234',
    name: 'com.google.devsite.test.TestWithFiles#run',
    status: TestStatus.TEST_STATUS_DONE,
    result: TestResult.TEST_RESULT_PASS,
    job: {
      id: 'b65cadd7-6ad6-440e-a3b7-bfe1948557e6',
      name: 'hello_mobile_harness_test_on_mh',
      status: JobStatus.JOB_STATUS_DONE,
      result: JobResult.JOB_RESULT_PASS,
      spongeLink: 'http://sponge2/b65cadd7-6ad6-440e-a3b7-bfe1948557e6',
    },
    devices: {
      device: [
        {
          id: '99061FFAZ004AA',
        },
      ],
    },
    host: {
      name: '3a-cm-10-18-01.acs.example.com',
      ip: '100.107.200.155',
    },
    executionDetails: {
      createTime: '2025-07-09T10:11:15Z',
      startTime: '2025-07-09T10:11:15Z',
      endTime: '2025-07-09T10:11:25Z',
      updateTime: '2025-07-09T10:11:25Z',
      user: 'dafeng',
      actualUser: 'dafeng',
    },
    properties: {
      'dimension_rooted': 'false',
    },
    timingBreakdown: {
      createTime: '2025-07-09T10:11:15Z',
      startTime: '2025-07-09T10:11:15Z',
      endTime: '2025-07-09T10:11:25Z',
      stages: [
        {
          name: 'Run Test',
          tag: 'client',
          startTime: '2025-07-09T10:11:19Z',
          endTime: '2025-07-09T10:11:25Z',
        },
      ],
    },
    fileExplorer: {
      cnsPath: '/cns-fake/lz-d/home/dafeng/mobileharness/experiments/run_1',
      files: [
        {
          path: 'test_run.log',
          size: 1048,
          viewable: true,
        },
        {
          path: 'screenshot.png',
          size: 143520,
          viewable: false,
        },
        {
          path: 'nested/folder/config.json',
          size: 429,
          viewable: true,
        },
        {
          path: 'nested/folder/output.txt',
          size: 12054,
          viewable: true,
        },
        {
          path: 'large_file.db',
          size: 25 * 1024 * 1024, // 25MB
          viewable: false,
        },
      ],
    },
  },
  log: '[10:11:15] Loading files...\n[10:11:25] Test execution complete.',
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=labels.test_id%3D%22test-with-files-id-1234%22',
};
