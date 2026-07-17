/**
 * @fileoverview Defines interfaces for Test Overview data, structured for
 * presentation in the UI, corresponding to test details in mobileharness.
 */

import {TimeBreakdown} from './timeline';
export type {TimeBreakdown};

/** gRPC-friendly status of a test, decoupled from its result. */
export enum TestStatus {
  TEST_STATUS_UNSPECIFIED = 'TEST_STATUS_UNSPECIFIED',
  TEST_STATUS_NEW = 'TEST_STATUS_NEW',
  TEST_STATUS_ASSIGNED = 'TEST_STATUS_ASSIGNED',
  TEST_STATUS_RUNNING = 'TEST_STATUS_RUNNING',
  TEST_STATUS_DONE = 'TEST_STATUS_DONE',
  TEST_STATUS_SUSPENDED = 'TEST_STATUS_SUSPENDED',
}

/** gRPC-friendly result of a test. */
export enum TestResult {
  TEST_RESULT_UNSPECIFIED = 'TEST_RESULT_UNSPECIFIED',
  TEST_RESULT_PASS = 'TEST_RESULT_PASS',
  TEST_RESULT_FAIL = 'TEST_RESULT_FAIL',
  TEST_RESULT_ERROR = 'TEST_RESULT_ERROR',
  TEST_RESULT_TIMEOUT = 'TEST_RESULT_TIMEOUT',
  TEST_RESULT_ABORT = 'TEST_RESULT_ABORT',
  TEST_RESULT_SKIP = 'TEST_RESULT_SKIP',
}

/** gRPC-friendly status of a job, decoupled from its result. */
export enum JobStatus {
  JOB_STATUS_UNSPECIFIED = 'JOB_STATUS_UNSPECIFIED',
  JOB_STATUS_NEW = 'JOB_STATUS_NEW',
  JOB_STATUS_RUNNING = 'JOB_STATUS_RUNNING',
  JOB_STATUS_DONE = 'JOB_STATUS_DONE',
  JOB_STATUS_SUSPENDED = 'JOB_STATUS_SUSPENDED',
}

/** gRPC-friendly result of a job. */
export enum JobResult {
  JOB_RESULT_UNSPECIFIED = 'JOB_RESULT_UNSPECIFIED',
  JOB_RESULT_PASS = 'JOB_RESULT_PASS',
  JOB_RESULT_FAIL = 'JOB_RESULT_FAIL',
  JOB_RESULT_ERROR = 'JOB_RESULT_ERROR',
  JOB_RESULT_TIMEOUT = 'JOB_RESULT_TIMEOUT',
  JOB_RESULT_ABORT = 'JOB_RESULT_ABORT',
  JOB_RESULT_SKIP = 'JOB_RESULT_SKIP',
}

/** gRPC-friendly status of a session, decoupled from its result. */
export enum SessionStatus {
  SESSION_STATUS_UNSPECIFIED = 'SESSION_STATUS_UNSPECIFIED',
  SESSION_STATUS_NEW = 'SESSION_STATUS_NEW',
  SESSION_STATUS_RUNNING = 'SESSION_STATUS_RUNNING',
  SESSION_STATUS_DONE = 'SESSION_STATUS_DONE',
}

/** gRPC-friendly result of a session. */
export enum SessionResult {
  SESSION_RESULT_UNSPECIFIED = 'SESSION_RESULT_UNSPECIFIED',
  SESSION_RESULT_PASS = 'SESSION_RESULT_PASS',
  SESSION_RESULT_FAIL = 'SESSION_RESULT_FAIL',
  SESSION_RESULT_ERROR = 'SESSION_RESULT_ERROR',
  SESSION_RESULT_TIMEOUT = 'SESSION_RESULT_TIMEOUT',
  SESSION_RESULT_ABORT = 'SESSION_RESULT_ABORT',
  SESSION_RESULT_SKIP = 'SESSION_RESULT_SKIP',
}

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

/** Execution details. */
export interface ExecutionDetails {
  user?: string;
  actualUser?: string;
  cloudLogLink?: string;
  createTime: string;
  startTime?: string;
  endTime?: string;
  lastUpdateTime?: string;
}

/** One non-fatal warning surfaced during execution. */
export interface TestWarning {
  message: string;
  trace?: string;
}

/** Wrapper for a list of warnings. */
export interface TestWarnings {
  warning?: TestWarning[];
}

/** One error that contributed to a non-passing test result. */
export interface TestError {
  message: string;
  trace?: string;
}

/** Wrapper for a list of errors. */
export interface TestErrors {
  error?: TestError[];
}

/** Troubleshooting details (warnings & errors). */
export interface Troubleshooting {
  warnings?: TestWarnings;
  resultCause?: TestErrors;
}

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
}

/** Request structure for the getTestLog API. */
export interface GetTestLogRequest {
  testId: string;
  offset: number;
  length: number;
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
