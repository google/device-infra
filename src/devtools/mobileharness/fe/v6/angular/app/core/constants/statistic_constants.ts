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
  'pass': '#34A853',
  'fail': '#EA4335',
  'error': '#FDBB05',
  'timeout': '#A142F4',
  'unknown': '#546E7A',
  'skip': '#CFD8DC',
  'abort': '#AD1457',
};
/** Chart colors for test results. */
export const TEST_CHART_COLORS = [
  TEST_COLORS['pass'],
  TEST_COLORS['fail'],
  TEST_COLORS['error'],
  TEST_COLORS['timeout'],
  TEST_COLORS['unknown'],
  TEST_COLORS['skip'],
  TEST_COLORS['abort'],
];

/** Colors associated with different recovery outcomes. */
export const RECOVERY_COLORS: Record<string, string> = {
  'success': '#34A853',
  'fail': '#EA4335',
};
/** Chart colors for recovery outcomes. */
export const RECOVERY_CHART_COLORS = [
  RECOVERY_COLORS['success'],
  RECOVERY_COLORS['fail'],
];
