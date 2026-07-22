/**
 * @fileoverview Defines interfaces for Test Overview data, structured for
 * presentation in the UI, corresponding to test details in mobileharness.
 */

import {
  ErrorInfo,
  ExecutionDetails,
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
export type {ExecutionDetails, TimeBreakdown, Troubleshooting};
/** Minimal reference to the parent job. */
export interface ParentJobInfo {
  id: string;
  name: string;
  status?: JobStatus;
  result?: JobResult;
  spongeLink?: string;
}
/** One device the test was executed on. */
export interface TestDevice {
  id: string;
  type: string;
}
/** Wrapper for a list of devices. */
export interface TestDevices {
  device?: TestDevice[];
}
/** The host the test ran on. */
export interface HostInfo {
  name: string;
  ip: string;
}
/** Alias for a warning info item belonging to a test. */
export type TestWarning = WarningInfo;
/** Alias for an error info item belonging to a test. */
export type TestError = ErrorInfo;
/** A lightweight summary of a test (for nested sub-tests). */
export interface TestSummary {
  id: string;
  name: string;
  status: TestStatus;
  result?: TestResult;
  startTime?: string;
  endTime?: string;
  devices?: TestDevices;
}
/** Wrapper for a list of test summaries. */
export interface TestSummaries {
  test?: TestSummary[];
}
/** Nested sub-tests list details. */
export interface SubTestsInfo {
  rootTestId?: string;
  subTests?: TestSummaries;
}
/** The full overview data for a single test (matches TestDetail proto). */
export interface TestOverviewData {
  id: string;
  name: string;
  status: TestStatus;
  result?: TestResult;
  job?: ParentJobInfo;
  devices?: TestDevices;
  host?: HostInfo;
  executionDetails?: ExecutionDetails;
  properties?: Record<string, string>;
  troubleshooting?: Troubleshooting;
  subTestsInfo?: SubTestsInfo;
  timingBreakdown?: TimeBreakdown;
}
/** Request structure for the getTest API. */
export interface GetTestRequest {
  testId: string;
  subTestId?: string;
  jobId: string;
}
/** Request structure for the getTestLog API. */
export interface GetTestLogRequest {
  testId: string;
  offset: number;
  length: number;
  jobId: string;
}
/** Response structure for the getTest API. */
export interface GetTestResponse {
  test: TestOverviewData;
}
/** Response structure for the getTestLog API. */
export interface GetTestLogResponse {
  logContent: string;
  nextOffset: number;
  hasMore: boolean;
}
