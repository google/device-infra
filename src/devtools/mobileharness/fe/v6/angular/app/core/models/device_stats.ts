/**
 * @fileoverview Interfaces for device health, test result, and recovery task statistics.
 */

/**
 * Represents the data for the Device Healthiness Statistics card.
 * Backend is responsible for fetching, filtering by date, and aggregating.
 */
export declare interface HealthinessStats {
  /** Time series data for daily healthiness breakdown. */
  dailyStats: DailyHealthiness[];
  /** Aggregated data over the selected period. */
  aggregatedStats: HealthinessSummary;
}

/**
 * Healthiness breakdown for a single day.
 */
export declare interface DailyHealthiness {
  /** The date in YYYY-MM-DD format. */
  date: string;
  healthinessSummary: HealthinessSummary;
}

/**
 * Healthiness statistics summary over the selected period.
 */
export declare interface HealthinessSummary {
  /** Total percentage of time the device was considered "In Service". */
  inServicePercent?: number;
  /** Total percentage of time the device was considered "Out of Service". */
  outOfServicePercent?: number;

  /**
   * Breakdown of "In Service" time.
   * Expected categories: "IDLE", "BUSY".
   */
  inServiceBreakdown: Array<{
    category: string;
    percent?: number;
  }>;

  /**
   * Breakdown of "Out of Service" time.
   * Expected categories: "LAMEDUCK", "INIT", "DYING", "DIRTY", "PREPPING",
   * "MISSING", "FAILED", and "OTHERS".
   */
  outOfServiceBreakdown: Array<{
    category: string;
    percent?: number;
  }>;
}

// -----------------------------------------------------------------------------

/**
 * Common statistics containing count and percentage.
 */
export declare interface Stats {
  count?: number;
  percent?: number;
}

// -----------------------------------------------------------------------------
// Test Result Statistics
// -----------------------------------------------------------------------------

/**
 * Categories for test results.
 */
export declare type TestResultCategory =
  | 'TEST_RESULT_CATEGORY_UNSPECIFIED'
  | 'TEST_RESULT_CATEGORY_PASS'
  | 'TEST_RESULT_CATEGORY_FAIL'
  | 'TEST_RESULT_CATEGORY_ERROR'
  | 'TEST_RESULT_CATEGORY_TIMEOUT'
  | 'TEST_RESULT_CATEGORY_ABORT'
  | 'TEST_RESULT_CATEGORY_SKIP'
  | 'TEST_RESULT_CATEGORY_UNKNOWN';

/**
 * Represents the data for the Test Result Statistics card.
 */
export declare interface TestResultStats {
  /** Time series data for daily test results. */
  dailyStats: DailyTestResults[];
  /** Aggregated data over the selected period. */
  summary: TestResultSummary;
}

/**
 * Test results for a single day.
 */
export declare interface DailyTestResults {
  /** The date in YYYY-MM-DD format. */
  date: string;
  totalCount: number;
  categoryStats: DailyCategoryStats[];
}

/**
 * Statistics for a specific test result category on a specific day.
 */
export declare interface DailyCategoryStats {
  category: TestResultCategory;
  stats: Stats;
}

/**
 * Breakdown of test results by category.
 */
export declare interface TestResultBreakdownItem {
  category: TestResultCategory;
  stats: Stats;
}

/**
 * Group of test results (e.g., Completion, Non-Completion).
 */
export declare interface TestResultGroup {
  displayName: string; // e.g., "Completion", "Non-Completion", "Unknown"
  // 'percent' in totalStats is relative to TestResultSummary.totalCount
  totalStats: Stats; // Total count and percent for this group
  breakdownItems: TestResultBreakdownItem[]; // Breakdown of individual categories within this group
}

/**
 * Summary of test result statistics over a period.
 */
export declare interface TestResultSummary {
  totalCount: number;
  completionGroup: TestResultGroup;
  nonCompletionGroup: TestResultGroup;
  unknownGroup: TestResultGroup;
}

// -----------------------------------------------------------------------------
// Recovery Task Statistics
// -----------------------------------------------------------------------------

/**
 * Categories for recovery task outcomes.
 */
export declare type RecoveryOutcomeCategory =
  | 'RECOVERY_OUTCOME_CATEGORY_UNSPECIFIED'
  | 'RECOVERY_OUTCOME_CATEGORY_SUCCESS'
  | 'RECOVERY_OUTCOME_CATEGORY_FAIL';

/**
 * Represents the data for the Recovery Task Statistics card.
 */
export declare interface RecoveryTaskStats {
  /** Time series data for daily recovery task outcomes. */
  dailyStats: DailyRecoveryTasks[];
  /** Aggregated data over the selected period. */
  summary: RecoveryTaskSummary;
}

/**
 * Recovery task outcomes for a single day.
 */
export declare interface DailyRecoveryTasks {
  /** The date in YYYY-MM-DD format. */
  date: string;
  totalCount: number;
  categoryStats: DailyOutcomeStats[];
}

/**
 * Statistics for a specific recovery outcome category on a specific day.
 */
export declare interface DailyOutcomeStats {
  category: RecoveryOutcomeCategory;
  stats: Stats;
}

/**
 * Breakdown of recovery tasks by outcome category.
 */
export declare interface RecoveryTaskBreakdownItem {
  category: RecoveryOutcomeCategory;
  stats: Stats;
}

/**
 * Summary of recovery task statistics over a period.
 */
export declare interface RecoveryTaskSummary {
  totalCount: number;
  outcomeBreakdown: RecoveryTaskBreakdownItem[];
}
