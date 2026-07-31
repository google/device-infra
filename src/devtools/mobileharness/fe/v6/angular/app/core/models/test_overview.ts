/**
 * @fileoverview Defines interfaces for Test Overview data, structured for
 * presentation in the UI, corresponding to test details in mobileharness.
 */

import {
  ErrorInfo,
  ExecutionDetails,
  FileExplorer,
  FileInfo,
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
  TestResult,
  TestStatus,
  Troubleshooting,
  WarningInfo,
} from './common_models';
import {TimeBreakdown} from './timeline';
export {
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
  TestResult,
  TestStatus,
};
export type {
  ExecutionDetails,
  FileExplorer,
  FileInfo,
  TimeBreakdown,
  Troubleshooting,
};
/** Minimal reference to the parent job. */
export declare interface ParentJobInfo {
  id: string;
  name: string;
  status?: JobStatus;
  result?: JobResult;
  spongeLink?: string;
}
/** One device the test was executed on. */
export declare interface TestDevice {
  id: string;
}
/** Wrapper for a list of devices. */
export declare interface TestDevices {
  device?: TestDevice[];
}
/** The host the test ran on. */
export declare interface HostInfo {
  name: string;
  ip: string;
}
/** Alias for a warning info item belonging to a test. */
export type TestWarning = WarningInfo;
/** Alias for an error info item belonging to a test. */
export type TestError = ErrorInfo;
/** A lightweight summary of a test (for nested sub-tests). */
export declare interface TestSummary {
  id: string;
  name: string;
  status: TestStatus;
  result?: TestResult;
  startTime?: string;
  endTime?: string;
  devices?: TestDevices;
  host?: HostInfo;
}
/** Wrapper for a list of test summaries. */
export declare interface TestSummaries {
  test?: TestSummary[];
}
/** Nested sub-tests list details. */
export declare interface SubTestsInfo {
  rootTestId?: string;
  subTests?: TestSummaries;
}
/** The full overview data for a single test (matches TestDetail proto). */
export declare interface TestOverviewData {
  id: string;
  name: string;
  status: TestStatus;
  result?: TestResult;
  job?: ParentJobInfo;
  devices?: TestDevices;
  host?: HostInfo;
  labUniverse?: string;
  executionDetails?: ExecutionDetails;
  properties?: Record<string, string>;
  troubleshooting?: Troubleshooting;
  subTestsInfo?: SubTestsInfo;
  timingBreakdown?: TimeBreakdown;
  fileExplorer?: FileExplorer;
}
/** Request structure for the getTest API. */
export declare interface GetTestRequest {
  testId: string;
  subTestId?: string;
  jobId: string;
}
/** Request structure for the getTestLog API. */
export declare interface GetTestLogRequest {
  testId: string;
  offset: number;
  jobId: string;
  contentHash?: string;
}
/** Response structure for the getTest API. */
export declare interface GetTestResponse {
  test: TestOverviewData;
}
/** Response structure for the getTestLog API. */
export declare interface GetTestLogResponse {
  logContent: string;
  nextOffset: number;
  testStatus: TestStatus;
  logReset: boolean;
  contentHash: string;
}
