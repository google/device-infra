/**
 * @fileoverview Defines interfaces for Job Overview data, structured for
 * presentation in the UI, corresponding to job details in mobileharness.
 */

/** The status of a job execution. */
export type JobStatus = 'NEW' | 'ASSIGNED' | 'RUNNING' | 'DONE' | 'SUSPENDED';

/** The result of a job execution. */
export type JobResult =
  | 'UNKNOWN'
  | 'PASS'
  | 'FAIL'
  | 'ERROR'
  | 'TIMEOUT'
  | 'ABORT'
  | 'SKIP';

/** Represents a single event in the job timeline. */
export interface JobTimelineEvent {
  name: string;
  duration: number;
  offset: number;
  inProgress?: boolean;
}

/** Details about an error that occurred during job execution. */
export interface JobError {
  message: string;
  trace?: string;
}

/** Details about a warning that occurred during job execution. */
export interface JobWarning {
  message: string;
  trace?: string;
}

/** Simplified metadata representing a child test belonging to a job. */
export interface LeanTest {
  id: string;
  title: string;
  status: string;
  duration: string;
  device: string;
}

/** Requirements for a device needed by the job. */
export interface DeviceRequirement {
  'Device Type': string;
  decorators?: string;
  dimensions?: Record<string, string>;
}

/** Represents configuration options for a job. */
export interface JobConfig {
  core: {
    'Device Type'?: string;
    Driver?: string;
    'Decorator(s)'?: string;
    'Total Test Count'?: number;
    'Job Priority'?: string;
    [key: string]: unknown;
  };
  retry?: Record<string, unknown>;
  dimensions?: Record<string, string>;
  params?: Record<string, string>;
  devices?: DeviceRequirement[];
}

/** Represents a single mark point inside a stage (e.g. checkpoint). */
export interface TimelineStageMarkPoint {
  name: string;
  time: string;
}

/** Represents a logical stage of execution (e.g. Allocation, Run). */
export interface TimelineStage {
  name: string;
  tag?: string;
  startTime: string;
  endTime?: string;
  markPoints?: TimelineStageMarkPoint[];
}

/** The timing breakdown of a job. */
export interface TimeBreakdown {
  createTime: string;
  startTime?: string;
  endTime?: string;
  totalDuration?: string;
  stages?: TimelineStage[];
}

/** Represents the full detailed data of a job overview. */
export interface JobOverviewData {
  id: string;
  title: string;
  status: JobStatus;
  result?: JobResult;
  user: string;
  actualUser: string;
  spongeLink?: string;
  sessionId?: string;
  sessionTitle?: string;
  sessionStatus?: JobStatus;
  sessionResult?: JobResult;
  error?: JobError;
  warnings?: JobWarning;
  tests: LeanTest[];

  createTime: string;
  startTime?: string;
  endTime?: string;

  config: JobConfig;
  properties: Record<string, string>;
  timingBreakdown?: TimeBreakdown;
}

/** Response structure for the getJob API. */
export interface GetJobResponse {
  job: JobOverviewData;
}

/** Response structure for the getJobLog API. */
export interface GetJobLogResponse {
  logContent: string;
  nextOffset: number;
  hasMore: boolean;
  cloudLogLink?: string;
}
