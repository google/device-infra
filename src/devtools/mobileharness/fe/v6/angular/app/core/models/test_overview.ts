/**
 * @fileoverview Defines interfaces for Test Overview data, structured for
 * presentation in the UI, corresponding to test details in mobileharness.
 */

/** The status of a test execution. */
export type TestStatus = 'NEW' | 'ASSIGNED' | 'RUNNING' | 'DONE' | 'SUSPENDED';

/** The result of a test execution. */
export type TestResult =
  | 'UNKNOWN'
  | 'PASS'
  | 'FAIL'
  | 'ERROR'
  | 'TIMEOUT'
  | 'ABORT'
  | 'SKIP';

/** Represents a parsed timeline event for UI display. */
export interface TestTimelineEvent {
  name: string;
  duration: number;
  offset: number;
  inProgress?: boolean;
}

/** Represents an error payload from the test run. */
export interface TestError {
  message: string;
  trace?: string;
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

/** The timing breakdown of a test. */
export interface TimeBreakdown {
  createTime: string;
  startTime?: string;
  endTime?: string;
  totalDuration?: string;
  stages?: TimelineStage[];
}

/** The full overview data for a single test. */
export interface TestOverviewData {
  id: string;
  name: string;
  status: TestStatus;
  result?: TestResult;

  // Parent Job Info
  /** The ID of the parent job. */
  jobId: string;
  /** The name of the parent job. */
  jobName: string;
  jobStatus?: string;
  jobResult?: string;

  // Executed On
  /** The ID of the device the test ran on. */
  deviceId: string;
  /** The type of the device (e.g. AndroidRealDevice). */
  deviceType: string;
  hostName?: string;
  hostIp?: string;

  // Execution Details
  spongeLink?: string;
  /** The time when the test was created. */
  createTime: string;
  startTime?: string;
  endTime?: string;

  // Configuration
  /** Parameters passed to the test execution. */
  params?: Record<string, string>;
  /** Dimensions of the device or environment. */
  dimensions?: Record<string, string>;

  // Properties
  /** Custom properties of the test run. */
  properties: Record<string, string>;

  // Troubleshooting
  warnings?: string[];
  error?: TestError;

  // Detailed execution phase timelines
  timingBreakdown?: TimeBreakdown;

  // Deprecated / Backwards compatibility fields
  user?: string;
  actualUser?: string;
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
  cloudLogLink?: string;
}
