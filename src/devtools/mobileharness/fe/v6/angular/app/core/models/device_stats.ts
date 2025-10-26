/**
 * @fileoverview Interfaces for device health, test result, and recovery task statistics.
 */

/**
 * Represents the data for the Device Healthiness Statistics card.
 * Backend is responsible for fetching, filtering by date, and aggregating.
 */
export interface HealthinessStats {
  /** Time series data for daily healthiness breakdown. */
  dailyStats: DailyHealthiness[];
  /** Aggregated data over the selected period. */
  aggregatedStats: AggregatedHealthiness;
}

/**
 * Healthiness breakdown for a single day.
 * Values represent the percentage of time spent in each state (summing to 100).
 */
export interface DailyHealthiness {
  /** The date in ISO string format (YYYY-MM-DD). */
  date: string;
  idle: number;
  busy: number;
  lameduck: number;
  init: number;
  dying: number;
  dirty: number;
  prepping: number;
  missing: number;
  failed: number;
  others: number;
}

/**
 * Aggregated healthiness statistics over the selected period.
 */
export interface AggregatedHealthiness {
  /** Total percentage of time the device was considered "In Service". */
  inServicePercent: number;
  /** Total percentage of time the device was considered "Out of Service". */
  outOfServicePercent: number;
  /** Breakdown of time spent in each status. */
  statusBreakdown: Array<{
    status: string; // e.g., 'IDLE', 'BUSY', 'FAILED'
    percent: number; // Percentage of the total period
  }>;
}

// -----------------------------------------------------------------------------

/**
 * Represents the data for the Test Result Statistics card.
 */
export interface TestResultStats {
  /** Time series data for daily test results. */
  dailyStats: DailyTestResults[];
  /** Aggregated data over the selected period. */
  aggregatedStats: AggregatedTestResults;
}

/**
 * Test results for a single day.
 * Values represent counts.
 */
export interface DailyTestResults {
  /** The date in ISO string format (YYYY-MM-DD). */
  date: string;
  pass: number;
  fail: number;
  error: number;
  timeout: number;
  other: number;
}

/**
 * Aggregated test result statistics over the selected period.
 */
export interface AggregatedTestResults {
  totalTests: number;
  completionCount: number; // pass + fail
  nonCompletionCount: number; // error + timeout + other
  resultBreakdown: Array<{
    result: string; // e.g., 'PASS', 'FAIL', 'ERROR'
    count: number;
    percent: number; // Percentage of totalTests
  }>;
}

// -----------------------------------------------------------------------------

/**
 * Represents the data for the Recovery Task Statistics card.
 */
export interface RecoveryTaskStats {
  /** Time series data for daily recovery task outcomes. */
  dailyStats: DailyRecoveryTasks[];
  /** Aggregated data over the selected period. */
  aggregatedStats: AggregatedRecoveryTasks;
}

/**
 * Recovery task outcomes for a single day.
 * Values represent counts.
 */
export interface DailyRecoveryTasks {
  /** The date in ISO string format (YYYY-MM-DD). */
  date: string;
  success: number;
  fail: number;
}

/**
 * Aggregated recovery task statistics over the selected period.
 */
export interface AggregatedRecoveryTasks {
  totalTasks: number;
  outcomeBreakdown: Array<{
    outcome: string; // 'SUCCESS' or 'FAIL'
    count: number;
    percent: number; // Percentage of totalTasks
  }>;
}
