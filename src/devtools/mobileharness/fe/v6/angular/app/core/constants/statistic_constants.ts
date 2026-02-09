/** Colors associated with different health states. */
export const HEALTH_COLORS: Record<string, string> = {
  'idle': '#66BB6A',
  'busy': '#42A5F5',
  'lameduck': '#FFA726',
  'init': '#FFEE58',
  'dying': '#EF5350',
  'dirty': '#AB47BC',
  'prepping': '#78909C',
  'missing': '#8E44AD',
  'failed': '#F44336',
  'others': '#BDBDBD',
};
/** Colors for health summary (In Service vs Out of Service). */
export const HEALTH_SUMMARY_COLORS = ['#34A853', '#EA4335'];

/** Colors associated with different test results. */
export const TEST_COLORS: Record<string, string> = {
  'TEST_RESULT_CATEGORY_PASS': '#34A853',
  'TEST_RESULT_CATEGORY_FAIL': '#EA4335',
  'TEST_RESULT_CATEGORY_ERROR': '#FDBB05',
  'TEST_RESULT_CATEGORY_TIMEOUT': '#A142F4',
  'TEST_RESULT_CATEGORY_UNKNOWN': '#546E7A',
  'TEST_RESULT_CATEGORY_SKIP': '#CFD8DC',
  'TEST_RESULT_CATEGORY_ABORT': '#AD1457',
  'TEST_RESULT_CATEGORY_UNSPECIFIED': '#BDBDBD',
};
/** Chart colors for test results. */
export const TEST_CHART_COLORS = [
  TEST_COLORS['TEST_RESULT_CATEGORY_PASS'],
  TEST_COLORS['TEST_RESULT_CATEGORY_FAIL'],
  TEST_COLORS['TEST_RESULT_CATEGORY_ERROR'],
  TEST_COLORS['TEST_RESULT_CATEGORY_TIMEOUT'],
  TEST_COLORS['TEST_RESULT_CATEGORY_UNKNOWN'],
  TEST_COLORS['TEST_RESULT_CATEGORY_SKIP'],
  TEST_COLORS['TEST_RESULT_CATEGORY_ABORT'],
  TEST_COLORS['TEST_RESULT_CATEGORY_UNSPECIFIED'],
];

/** Colors associated with different recovery outcomes. */
export const RECOVERY_COLORS: Record<string, string> = {
  'RECOVERY_OUTCOME_CATEGORY_SUCCESS': '#34A853',
  'RECOVERY_OUTCOME_CATEGORY_FAIL': '#EA4335',
  'RECOVERY_OUTCOME_CATEGORY_UNSPECIFIED': '#BDBDBD',
};
/** Chart colors for recovery outcomes. */
export const RECOVERY_CHART_COLORS = [
  RECOVERY_COLORS['RECOVERY_OUTCOME_CATEGORY_SUCCESS'],
  RECOVERY_COLORS['RECOVERY_OUTCOME_CATEGORY_FAIL'],
  RECOVERY_COLORS['RECOVERY_OUTCOME_CATEGORY_UNSPECIFIED'],
];
