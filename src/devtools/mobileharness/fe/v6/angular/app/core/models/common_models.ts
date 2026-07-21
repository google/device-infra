/**
 * @fileoverview Shared core data models and enums mapping to MobileHarness protos.
 */

/** gRPC-friendly status of a test. */
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

/** gRPC-friendly status of a job. */
export enum JobStatus {
  JOB_STATUS_UNSPECIFIED = 'JOB_STATUS_UNSPECIFIED',
  JOB_STATUS_NEW = 'JOB_STATUS_NEW',
  JOB_STATUS_RUNNING = 'JOB_STATUS_RUNNING',
  JOB_STATUS_DONE = 'JOB_STATUS_DONE',
  JOB_STATUS_SUSPENDED = 'JOB_STATUS_SUSPENDED',
  JOB_STATUS_ASSIGNED = 'JOB_STATUS_ASSIGNED',
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

/** gRPC-friendly status of a session. */
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

/** Shared definition for troubleshooting error. */
export declare interface ErrorInfo {
  message: string;
  trace?: string;
}

/** Shared definition for troubleshooting warning. */
export declare interface WarningInfo {
  message: string;
  trace?: string;
}

/** Shared execution details mapped to the "Execution Details" UI cards. */
export declare interface ExecutionDetails {
  user?: string;
  actualUser?: string;
  cloudLogLink?: string;
  createTime: string;
  startTime?: string;
  endTime?: string;
  updateTime?: string;
}

/** Collection container for error entry items. */
export declare interface Errors {
  error?: ErrorInfo[];
}

/** Collection container for warning entry items. */
export declare interface Warnings {
  warning?: WarningInfo[];
}

/** Troubleshooting layout card details shared by both Jobs and Tests. */
export declare interface Troubleshooting {
  warnings?: Warnings;
  resultCause?: Errors;
}
