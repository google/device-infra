import {ActionButtonState} from './action_common';
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
import {TestSummaries} from './test_overview';
import {TimeBreakdown} from './timeline';
export type {TimeBreakdown};

/** Represents all operations/actions achievable on a Job. */
export declare interface JobActions {
  readonly kill: ActionButtonState;
}

export {
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
  TestResult,
  TestStatus,
};

/** Alias for an error info item belonging to a job. */
export type JobError = ErrorInfo;
/** Alias for a warning info item belonging to a job. */
export type JobWarning = WarningInfo;

/** Information identifying the parent session that scheduled the job. */
export declare interface ParentSessionInfo {
  id: string;
  name: string;
  status: SessionStatus;
  result?: SessionResult;
}

/** Requirements for a device needed by the job. */
export declare interface DeviceRequirement {
  deviceType: string;
  driver?: string;
  decorators?: string[];
  dimensions?: Record<string, string>;
}

/** Container for a collection of needed device requirements. */
export declare interface DeviceRequirements {
  device?: DeviceRequirement[];
}

/** General execution, retry, and timeout settings for the job. */
export declare interface Settings {
  totalTestCount?: number;
  priority?: string;
  jobTimeoutSec?: number;
  testTimeoutSec?: number;
  startTimeoutSec?: number;
  testAttempts?: number;
  forceRetry?: boolean;
  retryLevel?: string;
}

/** Represents configuration options for a job. */
export declare interface JobConfig {
  devices?: DeviceRequirements;
  settings?: Settings;
  params?: Record<string, string>;
}

/** Represents the full detailed data of a job overview. */
export declare interface JobOverviewData {
  id: string;
  name: string;
  status: JobStatus;
  result?: JobResult;
  spongeLink?: string;
  session?: ParentSessionInfo;
  tests?: TestSummaries;

  executionDetails?: ExecutionDetails;
  config: JobConfig;
  properties: Record<string, string>;
  troubleshooting?: Troubleshooting;
  timingBreakdown?: TimeBreakdown;
  // File explorer resources.
  fileExplorer?: FileExplorer;
}

/** Information about a file associated with the job. */
export type JobFile = FileInfo;

/** Request structure for the getJob API. */
export declare interface GetJobRequest {
  jobId: string;
}

/** Response structure for the getJob API. */
export declare interface GetJobResponse {
  job: JobOverviewData;
  actions?: JobActions;
}

/** Request structure for the getJobLog API. */
export declare interface GetJobLogRequest {
  jobId: string;
  offset: number;
  contentHash?: string;
}

/** Response structure for the getJobLog API. */
export declare interface GetJobLogResponse {
  logContent: string;
  nextOffset: number;
  jobStatus: JobStatus;
  logReset?: boolean;
  contentHash?: string;
}

/** Request structure for the getJobFile API. */
export declare interface GetJobFileRequest {
  jobId: string;
  filePath: string;
}

/** Response structure for the getJobFile API. */
export declare interface GetJobFileResponse {
  content: string;
}
