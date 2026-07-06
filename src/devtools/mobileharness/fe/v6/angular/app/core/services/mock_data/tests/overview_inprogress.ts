import {
  JobStatus,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';
import {MockTestScenario} from '../models';

/** A mock test scenario representing a test in progress. */
export const SCENARIO_TEST_IN_PROGRESS: MockTestScenario = {
  id: 'test-inprogress-1',
  scenarioName: 'Test in Progress',
  log: `[09:30:15] Starting MobileHarness active test...
[09:30:16] Preparing device 43021FDAQ000UM...
[09:30:17] Installing Gmail instrumentation package...
[09:30:18] Running instrumentation test case: testRun...
[09:30:20] Photo upload task started.
[09:30:22] Uploading image chunk 1/3 (512KB)...
[09:30:24] Uploading image chunk 2/3 (512KB)...
[09:30:26] Uploading image chunk 3/3 (256KB)...
[09:30:28] Photo upload completed successfully.
[09:30:29] Verification passed. Saving results.`,
  cloudLogLink: '#',
  overview: {
    id: 'test-inprogress-1',
    name: 'com.google.devtools.mobileharness.infra.HarnessActiveTest#testRun',
    status: TestStatus.TEST_STATUS_RUNNING,
    job: {
      id: 'c3578d2a-776d-49e3-b065-c66624b9d665',
      name: 'com.google.android.gm.GmailInstrumentationTest',
      status: JobStatus.JOB_STATUS_RUNNING,
      spongeLink: 'http://sponge/mock-job-link',
    },
    devices: {
      device: [
        {
          id: '43021FDAQ000UM',
          type: 'AndroidRealDevice',
        },
      ],
    },
    host: {
      name: 'mt31-dm01-a-x04.moma.google.com',
      ip: '100.107.201.12',
    },
    executionDetails: {
      createTime: '2025-07-09T11:30:10Z',
      startTime: '2025-07-09T11:30:15Z',
      endTime: '2025-07-09T11:30:18Z',
      lastUpdateTime: '2025-07-09T11:30:18Z',
      user: 'dafeni',
      actualUser: 'dafeni@google.com',
    },
    config: {
      dimensions: {
        'Device Type': 'AndroidRealDevice',
      },
    },
    properties: {
      'infra_version': 'v6.2.1',
    },
    timingBreakdown: {
      createTime: '2025-07-09T11:30:10Z',
      startTime: '2025-07-09T11:30:15Z',
      stages: [
        {
          name: 'Pre-run Test',
          startTime: '2025-07-09T11:30:15Z',
          endTime: '2025-07-09T11:30:18Z',
        },
        {
          name: 'Run Test:AndroidInstrumentation',
          startTime: '2025-07-09T11:30:18Z',
        },
      ],
    },
  },
};
