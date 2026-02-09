import {GoogleDate} from '../../../shared/utils/date_utils';
import {
  DailyCategoryStats,
  DailyHealthiness,
  DailyOutcomeStats,
  DailyRecoveryTasks,
  DailyTestResults,
  HealthinessStats,
  HealthinessSummary,
  RecoveryOutcomeCategory,
  RecoveryTaskBreakdownItem,
  RecoveryTaskStats,
  TestResultBreakdownItem,
  TestResultCategory,
  TestResultStats,
} from '../../models/device_stats';

function formatDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function getDates(
  startDate: GoogleDate,
  endDate: GoogleDate,
): {start: Date; days: number} {
  const start = new Date(startDate.year, startDate.month - 1, startDate.day);
  const end = new Date(endDate.year, endDate.month - 1, endDate.day);

  if (isNaN(start.getTime()) || isNaN(end.getTime())) {
    throw new Error('Invalid start or end date');
  }

  if (start > end) {
    throw new Error('Start date must be before end date');
  }

  const days = Math.round((end.getTime() - start.getTime()) / 86400000) + 1;
  return {start, days};
}

function createHealthinessSummary(
  idle: number,
  busy: number,
  lameduck: number,
  init: number,
  dying: number,
  dirty: number,
  prepping: number,
  missing: number,
  failed: number,
  others: number,
): HealthinessSummary {
  const inServiceTotal = idle + busy;
  const outOfServiceTotal =
    lameduck + init + dying + dirty + prepping + missing + failed + others;
  const total = inServiceTotal + outOfServiceTotal;

  return {
    inServicePercent: total ? (inServiceTotal / total) * 100 : 0,
    outOfServicePercent: total ? (outOfServiceTotal / total) * 100 : 0,
    inServiceBreakdown: [
      {category: 'IDLE', percent: total ? (idle / total) * 100 : 0},
      {category: 'BUSY', percent: total ? (busy / total) * 100 : 0},
    ].filter((i) => i.percent > 0),
    outOfServiceBreakdown: [
      {category: 'LAMEDUCK', percent: total ? (lameduck / total) * 100 : 0},
      {category: 'INIT', percent: total ? (init / total) * 100 : 0},
      {category: 'DYING', percent: total ? (dying / total) * 100 : 0},
      {category: 'DIRTY', percent: total ? (dirty / total) * 100 : 0},
      {category: 'PREPPING', percent: total ? (prepping / total) * 100 : 0},
      {category: 'MISSING', percent: total ? (missing / total) * 100 : 0},
      {category: 'FAILED', percent: total ? (failed / total) * 100 : 0},
      {category: 'OTHERS', percent: total ? (others / total) * 100 : 0},
    ].filter((i) => i.percent > 0),
  };
}

/** Generates mock healthiness stats. date YYYY-MM-DD. */
export function generateHealthinessStats(
  startDate: GoogleDate,
  endDate: GoogleDate,
): HealthinessStats {
  const {start, days} = getDates(startDate, endDate);

  const accumulated = {
    idle: 0,
    busy: 0,
    lameduck: 0,
    init: 0,
    dying: 0,
    dirty: 0,
    prepping: 0,
    missing: 0,
    failed: 0,
    others: 0,
  };

  const dailyStats: DailyHealthiness[] = Array.from({length: days}, (_, i) => {
    const date = new Date(start);
    date.setDate(start.getDate() + i);

    let rem = 100;
    const idle = Math.random() * (rem * 0.7);
    rem -= idle;
    const busy = Math.random() * (rem * 0.6);
    rem -= busy;
    const lameduck = Math.random() < 0.2 ? Math.random() * (rem * 0.4) : 0;
    rem -= lameduck;
    const prepping = Math.random() < 0.3 ? Math.random() * (rem * 0.5) : 0;
    rem -= prepping;
    const dying = Math.random() < 0.1 ? Math.random() * (rem * 0.3) : 0;
    rem -= dying;
    const dirty = Math.random() < 0.15 ? Math.random() * (rem * 0.4) : 0;
    rem -= dirty;
    const init = Math.random() < 0.2 ? Math.random() * (rem * 0.5) : 0;
    rem -= init;
    const missing = Math.random() < 0.1 ? Math.random() * (rem * 0.2) : 0;
    rem -= missing;
    const failed = Math.random() < 0.15 ? Math.random() * (rem * 0.25) : 0;
    rem -= failed;
    const others = rem;

    accumulated.idle += idle;
    accumulated.busy += busy;
    accumulated.lameduck += lameduck;
    accumulated.init += init;
    accumulated.dying += dying;
    accumulated.dirty += dirty;
    accumulated.prepping += prepping;
    accumulated.missing += missing;
    accumulated.failed += failed;
    accumulated.others += others;

    return {
      date: formatDate(date),
      healthinessSummary: createHealthinessSummary(
        idle,
        busy,
        lameduck,
        init,
        dying,
        dirty,
        prepping,
        missing,
        failed,
        others,
      ),
    };
  });

  const aggregatedStats = createHealthinessSummary(
    accumulated.idle,
    accumulated.busy,
    accumulated.lameduck,
    accumulated.init,
    accumulated.dying,
    accumulated.dirty,
    accumulated.prepping,
    accumulated.missing,
    accumulated.failed,
    accumulated.others,
  );

  return {dailyStats, aggregatedStats};
}

/** Generates mock test result stats. */
export function generateTestResultStats(
  startDate: GoogleDate,
  endDate: GoogleDate,
): TestResultStats {
  const {start, days} = getDates(startDate, endDate);

  // Accumulators
  const totalCounts: Record<TestResultCategory, number> = {
    'TEST_RESULT_CATEGORY_PASS': 0,
    'TEST_RESULT_CATEGORY_FAIL': 0,
    'TEST_RESULT_CATEGORY_ERROR': 0,
    'TEST_RESULT_CATEGORY_TIMEOUT': 0,
    'TEST_RESULT_CATEGORY_UNKNOWN': 0,
    'TEST_RESULT_CATEGORY_SKIP': 0,
    'TEST_RESULT_CATEGORY_ABORT': 0,
    'TEST_RESULT_CATEGORY_UNSPECIFIED': 0,
  };

  const dailyStats: DailyTestResults[] = Array.from({length: days}, (_, i) => {
    const date = new Date(start);
    date.setDate(start.getDate() + i);

    const total = 50 + Math.floor(Math.random() * 150);
    let rem = total;
    const pass = Math.floor(Math.random() * (rem * 0.85));
    rem -= pass;
    const fail = Math.floor(Math.random() * (rem * 0.5));
    rem -= fail;
    const error = Math.floor(Math.random() * (rem * 0.6));
    rem -= error;
    const timeout = Math.floor(Math.random() * (rem * 0.7));
    rem -= timeout;
    const unknown = Math.floor(Math.random() * (rem * 0.4));
    rem -= unknown;
    const skip = Math.floor(Math.random() * (rem * 0.5));
    rem -= skip;
    const abort = Math.floor(Math.random() * (rem * 0.5));
    rem -= abort;
    const unspecified = rem;

    totalCounts['TEST_RESULT_CATEGORY_PASS'] += pass;
    totalCounts['TEST_RESULT_CATEGORY_FAIL'] += fail;
    totalCounts['TEST_RESULT_CATEGORY_ERROR'] += error;
    totalCounts['TEST_RESULT_CATEGORY_TIMEOUT'] += timeout;
    totalCounts['TEST_RESULT_CATEGORY_UNKNOWN'] += unknown;
    totalCounts['TEST_RESULT_CATEGORY_UNSPECIFIED'] += unspecified;
    totalCounts['TEST_RESULT_CATEGORY_SKIP'] += skip;
    totalCounts['TEST_RESULT_CATEGORY_ABORT'] += abort;

    return {
      date: formatDate(date),
      totalCount: total,
      categoryStats: [
        {
          category: 'TEST_RESULT_CATEGORY_PASS',
          stats: {count: pass, percent: total ? (pass / total) * 100 : 0},
        },
        {
          category: 'TEST_RESULT_CATEGORY_FAIL',
          stats: {count: fail, percent: total ? (fail / total) * 100 : 0},
        },
        {
          category: 'TEST_RESULT_CATEGORY_ERROR',
          stats: {count: error, percent: total ? (error / total) * 100 : 0},
        },
        {
          category: 'TEST_RESULT_CATEGORY_TIMEOUT',
          stats: {count: timeout, percent: total ? (timeout / total) * 100 : 0},
        },
        {
          category: 'TEST_RESULT_CATEGORY_UNKNOWN',
          stats: {count: unknown, percent: total ? (unknown / total) * 100 : 0},
        },
        {
          category: 'TEST_RESULT_CATEGORY_UNSPECIFIED',
          stats: {
            count: unspecified,
            percent: total ? (unspecified / total) * 100 : 0,
          },
        },
        {
          category: 'TEST_RESULT_CATEGORY_SKIP',
          stats: {count: skip, percent: total ? (skip / total) * 100 : 0},
        },
        {
          category: 'TEST_RESULT_CATEGORY_ABORT',
          stats: {count: abort, percent: total ? (abort / total) * 100 : 0},
        },
      ].filter(
        (i) => i.stats.count && i.stats.count > 0,
      ) as DailyCategoryStats[],
    };
  });

  const totalTests = Object.values(totalCounts).reduce((a, b) => a + b, 0);

  const completionCount =
    totalCounts['TEST_RESULT_CATEGORY_PASS'] +
    totalCounts['TEST_RESULT_CATEGORY_FAIL'];
  const nonCompletionCount =
    totalCounts['TEST_RESULT_CATEGORY_ERROR'] +
    totalCounts['TEST_RESULT_CATEGORY_TIMEOUT'] +
    totalCounts['TEST_RESULT_CATEGORY_ABORT'] +
    totalCounts['TEST_RESULT_CATEGORY_SKIP'] +
    totalCounts['TEST_RESULT_CATEGORY_UNKNOWN'];
  const unknownCount = totalCounts['TEST_RESULT_CATEGORY_UNSPECIFIED'];

  const getPercent = (count: number) =>
    totalTests ? (count / totalTests) * 100 : 0;

  return {
    dailyStats,
    summary: {
      totalCount: totalTests,
      completionGroup: {
        displayName: 'Completion',
        totalStats: {
          count: completionCount,
          percent: getPercent(completionCount),
        },
        breakdownItems: [
          {
            category: 'TEST_RESULT_CATEGORY_PASS',
            stats: {
              count: totalCounts['TEST_RESULT_CATEGORY_PASS'],
              percent: getPercent(totalCounts['TEST_RESULT_CATEGORY_PASS']),
            },
          },
          {
            category: 'TEST_RESULT_CATEGORY_FAIL',
            stats: {
              count: totalCounts['TEST_RESULT_CATEGORY_FAIL'],
              percent: getPercent(totalCounts['TEST_RESULT_CATEGORY_FAIL']),
            },
          },
        ].filter(
          (i) => i.stats.count && i.stats.count > 0,
        ) as TestResultBreakdownItem[],
      },
      nonCompletionGroup: {
        displayName: 'Non-Completion',
        totalStats: {
          count: nonCompletionCount,
          percent: getPercent(nonCompletionCount),
        },
        breakdownItems: [
          {
            category: 'TEST_RESULT_CATEGORY_ERROR',
            stats: {
              count: totalCounts['TEST_RESULT_CATEGORY_ERROR'],
              percent: getPercent(totalCounts['TEST_RESULT_CATEGORY_ERROR']),
            },
          },
          {
            category: 'TEST_RESULT_CATEGORY_TIMEOUT',
            stats: {
              count: totalCounts['TEST_RESULT_CATEGORY_TIMEOUT'],
              percent: getPercent(totalCounts['TEST_RESULT_CATEGORY_TIMEOUT']),
            },
          },
          {
            category: 'TEST_RESULT_CATEGORY_ABORT',
            stats: {
              count: totalCounts['TEST_RESULT_CATEGORY_ABORT'],
              percent: getPercent(totalCounts['TEST_RESULT_CATEGORY_ABORT']),
            },
          },
          {
            category: 'TEST_RESULT_CATEGORY_SKIP',
            stats: {
              count: totalCounts['TEST_RESULT_CATEGORY_SKIP'],
              percent: getPercent(totalCounts['TEST_RESULT_CATEGORY_SKIP']),
            },
          },
          {
            category: 'TEST_RESULT_CATEGORY_UNKNOWN',
            stats: {
              count: totalCounts['TEST_RESULT_CATEGORY_UNKNOWN'],
              percent: getPercent(totalCounts['TEST_RESULT_CATEGORY_UNKNOWN']),
            },
          },
        ].filter(
          (i) => i.stats.count && i.stats.count > 0,
        ) as TestResultBreakdownItem[],
      },
      unknownGroup: {
        displayName: 'Unknown',
        totalStats: {
          count: unknownCount,
          percent: getPercent(unknownCount),
        },
        breakdownItems: [
          {
            category: 'TEST_RESULT_CATEGORY_UNSPECIFIED',
            stats: {
              count: totalCounts['TEST_RESULT_CATEGORY_UNSPECIFIED'],
              percent: getPercent(
                totalCounts['TEST_RESULT_CATEGORY_UNSPECIFIED'],
              ),
            },
          },
        ].filter(
          (i) => i.stats.count && i.stats.count > 0,
        ) as TestResultBreakdownItem[],
      },
    },
  };
}

/** Generates mock recovery task stats. */
export function generateRecoveryTaskStats(
  startDate: GoogleDate,
  endDate: GoogleDate,
): RecoveryTaskStats {
  const {start, days} = getDates(startDate, endDate);

  const totalCounts: Record<RecoveryOutcomeCategory, number> = {
    'RECOVERY_OUTCOME_CATEGORY_SUCCESS': 0,
    'RECOVERY_OUTCOME_CATEGORY_FAIL': 0,
    'RECOVERY_OUTCOME_CATEGORY_UNSPECIFIED': 0,
  };

  const dailyStats: DailyRecoveryTasks[] = Array.from(
    {length: days},
    (_, i) => {
      const date = new Date(start);
      date.setDate(start.getDate() + i);

      const total = 5 + Math.floor(Math.random() * 20);
      let rem = total;
      const success = Math.floor(Math.random() * rem * 0.8);
      rem -= success;
      const fail = Math.floor(Math.random() * rem * 0.9);
      rem -= fail;
      const unspecified = rem;

      totalCounts['RECOVERY_OUTCOME_CATEGORY_SUCCESS'] += success;
      totalCounts['RECOVERY_OUTCOME_CATEGORY_FAIL'] += fail;
      totalCounts['RECOVERY_OUTCOME_CATEGORY_UNSPECIFIED'] += unspecified;

      const categoryStats: DailyOutcomeStats[] = [
        {
          category: 'RECOVERY_OUTCOME_CATEGORY_SUCCESS',
          stats: {count: success, percent: total ? (success / total) * 100 : 0},
        },
        {
          category: 'RECOVERY_OUTCOME_CATEGORY_FAIL',
          stats: {count: fail, percent: total ? (fail / total) * 100 : 0},
        },
        {
          category: 'RECOVERY_OUTCOME_CATEGORY_UNSPECIFIED',
          stats: {
            count: unspecified,
            percent: total ? (unspecified / total) * 100 : 0,
          },
        },
      ].filter(
        (i) => i.stats.count && i.stats.count > 0,
      ) as DailyOutcomeStats[];

      return {
        date: formatDate(date),
        totalCount: total,
        categoryStats,
      };
    },
  );

  const totalTasks = Object.values(totalCounts).reduce((a, b) => a + b, 0);
  const getPercent = (count: number) =>
    totalTasks ? (count / totalTasks) * 100 : 0;

  return {
    dailyStats,
    summary: {
      totalCount: totalTasks,
      outcomeBreakdown: [
        {
          category: 'RECOVERY_OUTCOME_CATEGORY_SUCCESS',
          stats: {
            count: totalCounts['RECOVERY_OUTCOME_CATEGORY_SUCCESS'],
            percent: getPercent(
              totalCounts['RECOVERY_OUTCOME_CATEGORY_SUCCESS'],
            ),
          },
        },
        {
          category: 'RECOVERY_OUTCOME_CATEGORY_FAIL',
          stats: {
            count: totalCounts['RECOVERY_OUTCOME_CATEGORY_FAIL'],
            percent: getPercent(totalCounts['RECOVERY_OUTCOME_CATEGORY_FAIL']),
          },
        },
        {
          category: 'RECOVERY_OUTCOME_CATEGORY_UNSPECIFIED',
          stats: {
            count: totalCounts['RECOVERY_OUTCOME_CATEGORY_UNSPECIFIED'],
            percent: getPercent(
              totalCounts['RECOVERY_OUTCOME_CATEGORY_UNSPECIFIED'],
            ),
          },
        },
      ].filter(
        (i) => i.stats.count && i.stats.count > 0,
      ) as RecoveryTaskBreakdownItem[],
    },
  };
}
