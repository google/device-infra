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
  aggregatedStats: HealthinessSummary;
}

/**
 * Healthiness breakdown for a single day.
 */
export interface DailyHealthiness {
  /** The date in YYYY-MM-DD format. */
  date: string;
  healthinessSummary: HealthinessSummary;
}

/**
 * Healthiness statistics summary over the selected period.
 */
export interface HealthinessSummary {
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
  /** The date in YYYY-MM-DD format. */
  date: string;
  pass?: number;
  fail?: number;
  error?: number;
  timeout?: number;
  other?: number;
}

/**
 * Common statistics containing count and percentage.
 */
export interface Stats {
  count?: number;
  percent?: number;
}

/**
 * Common breakdown message containing category and stats.
 */
export interface CategoryStats {
  category: string;
  stats: Stats;
}

/**
 * Aggregated test result statistics over the selected period.
 */
export interface AggregatedTestResults {
  totalTests: number;
  completionStats: Stats;
  nonCompletionStats: Stats;
  completionBreakdown: CategoryStats[];
  nonCompletionBreakdown: CategoryStats[];
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
  /** The date in YYYY-MM-DD format. */
  date: string;
  success: number;
  fail: number;
}

/**
 * Aggregated recovery task statistics over the selected period.
 */
export interface AggregatedRecoveryTasks {
  totalTasks: number;
  outcomeBreakdown: CategoryStats[];
}
