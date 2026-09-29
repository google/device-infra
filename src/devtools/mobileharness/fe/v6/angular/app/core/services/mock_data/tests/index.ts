import {MockTestScenario} from '../models';
import {SCENARIO_TEST_ERROR} from './overview_error';
import {SCENARIO_TEST_FAILED} from './overview_failed';
import {SCENARIO_TEST_FILES} from './overview_files';
import {SCENARIO_TEST_IN_PROGRESS} from './overview_inprogress';
import {SCENARIO_TEST_LONG_NAME} from './overview_longname';
import {SCENARIO_TEST_MULTIPLE_ERRORS_WARNINGS} from './overview_multiple_errors_warnings';
import {SCENARIO_TEST_PASSED} from './overview_passed';
import {SCENARIO_TEST_SKIPPED} from './overview_skipped';
import {SCENARIO_TEST_SUSPENDED} from './overview_suspended';
import {SCENARIO_TEST_TIMEOUT} from './overview_timeout';
import {SCENARIO_TEST_WARNING} from './overview_warning';

function createClonedTestScenario(
  base: MockTestScenario,
  opts: {
    id: string;
    scenarioName: string;
    name?: string;
    jobId: string;
    jobName?: string;
    user?: string;
    hostName?: string;
    hostIp?: string;
    deviceIds?: string[];
  },
): MockTestScenario {
  return {
    ...base,
    id: opts.id,
    scenarioName: opts.scenarioName,
    overview: {
      ...base.overview,
      id: opts.id,
      name: opts.name ?? base.overview.name,
      job: {
        ...base.overview.job,
        id: opts.jobId,
        name: opts.jobName ?? base.overview.job?.name ?? 'MobileHarness Job',
      },
      host: opts.hostName
        ? {
            name: opts.hostName,
            ip: opts.hostIp ?? '172.16.42.18',
          }
        : base.overview.host,
      devices: opts.deviceIds
        ? {
            device: opts.deviceIds.map((id) => ({id})),
          }
        : base.overview.devices,
      executionDetails: base.overview.executionDetails
        ? {
            ...base.overview.executionDetails,
            user: opts.user ?? base.overview.executionDetails.user,
            actualUser: opts.user ?? base.overview.executionDetails.actualUser,
          }
        : undefined,
    },
  };
}

/** Central registry of all mock test scenarios. */
export const MOCK_TEST_SCENARIOS: MockTestScenario[] = [
  SCENARIO_TEST_FILES,
  SCENARIO_TEST_FAILED,
  SCENARIO_TEST_PASSED,
  SCENARIO_TEST_IN_PROGRESS,
  SCENARIO_TEST_WARNING,
  SCENARIO_TEST_MULTIPLE_ERRORS_WARNINGS,
  SCENARIO_TEST_LONG_NAME,
  SCENARIO_TEST_ERROR,
  SCENARIO_TEST_TIMEOUT,
  SCENARIO_TEST_SKIPPED,
  SCENARIO_TEST_SUSPENDED,
  createClonedTestScenario(SCENARIO_TEST_PASSED, {
    id: 'test-id-for-1-hit-1',
    scenarioName: 'All-Site 1-Hit Test #1 (Passed)',
    jobId: 'job-id-for-1-hit-1',
    jobName: 'passed_regression_suite',
    user: 'qiupingf',
    hostName: 'host-for-1-hit-1.example.com',
    hostIp: '172.16.42.18',
    deviceIds: ['device-id-for-1-hit-1'],
  }),
  createClonedTestScenario(SCENARIO_TEST_IN_PROGRESS, {
    id: 'test-id-for-1-hit-2',
    scenarioName: 'All-Site 1-Hit Test #2 (In Progress)',
    jobId: 'job-id-for-1-hit-2',
    jobName: 'long_running_reliability_test',
    user: 'qiupingf',
    hostName: 'host-for-1-hit-1.example.com',
    hostIp: '172.16.42.18',
    deviceIds: ['device-id-for-1-hit-2'],
  }),
  createClonedTestScenario(SCENARIO_TEST_PASSED, {
    id: 'tjs-id-for-ambiguous-1',
    scenarioName: 'All-Site Ambiguous TJS: Test Match',
    jobId: 'job-id-for-1-hit-1',
    jobName: 'passed_regression_suite',
    user: 'qiupingf',
    hostName: 'host-for-1-hit-1.example.com',
    hostIp: '172.16.42.18',
    deviceIds: ['device-id-for-1-hit-1'],
  }),
  createClonedTestScenario(SCENARIO_TEST_PASSED, {
    id: 'baf01a94-f625-4d65-9f3d-938d30e5f6f8',
    scenarioName: 'TJS Search Test 1: HelloMobileHarnessTest#addStatsToSponge',
    name: 'com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#addStatsToSponge',
    jobId: 'j_987654',
    jobName: 'MobileHarness Core Tests',
    user: 'qiupingf',
    hostName: 'host-for-1-hit-1.example.com',
    hostIp: '172.16.42.18',
    deviceIds: ['18261FDF6003KC', '98888FDF6005AB'],
  }),
  createClonedTestScenario(SCENARIO_TEST_PASSED, {
    id: 'b6a4e952-5a5c-4728-875f-322bfce27bc8',
    scenarioName: 'TJS Search Test 2: HelloMobileHarnessTest#plusOneButton',
    name: 'com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#plusOneButton',
    jobId: 'j_987654',
    jobName: 'MobileHarness Core Tests',
    user: 'qiupingf',
    hostName: 'host-for-1-hit-1.example.com',
    hostIp: '172.16.42.18',
    deviceIds: ['device-id-for-1-hit-1'],
  }),
  createClonedTestScenario(SCENARIO_TEST_IN_PROGRESS, {
    id: 'a1b2c3d4-e5f6-47a8-9b0c-1d2e3f4a5b6c',
    scenarioName: 'TJS Search Test 3: HelloMobileHarnessTest#loginFlow',
    name: 'com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#loginFlow',
    jobId: 'j_987654',
    jobName: 'MobileHarness Core Tests',
    user: 'qiupingf',
    hostName: 'host-for-1-hit-1.example.com',
    hostIp: '172.16.42.18',
    deviceIds: ['18261FDF6003KC'],
  }),
  createClonedTestScenario(SCENARIO_TEST_FAILED, {
    id: 'c9d8e7f6-a5b4-4321-fedc-ba9876543210',
    scenarioName: 'TJS Search Test 4: ClientTest#testDeviceAllocation',
    name: 'com.google.devtools.mobileharness.infra.client.ClientTest#testDeviceAllocation',
    jobId: 'j_876543',
    jobName: 'Device Infra Integration',
    user: 'dev_user',
    hostName: 'host-for-1-hit-1.example.com',
    hostIp: '172.16.42.18',
    deviceIds: ['device-id-for-1-hit-1'],
  }),
];
