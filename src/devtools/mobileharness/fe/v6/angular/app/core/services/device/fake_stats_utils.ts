import {GoogleDate} from '../../../shared/utils/date_utils';
import {
  AggregatedRecoveryTasks,
  AggregatedTestResults,
  DailyHealthiness,
  DailyRecoveryTasks,
  DailyTestResults,
  HealthinessStats,
  HealthinessSummary,
  RecoveryTaskStats,
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
    const abort = rem;

    return {
      date: formatDate(date),
      pass,
      fail,
      error,
      timeout,
      unknown,
      skip,
      abort,
    };
  });

  const totalPass = dailyStats.reduce((sum, day) => sum + (day.pass ?? 0), 0);
  const totalFail = dailyStats.reduce((sum, day) => sum + (day.fail ?? 0), 0);
  const totalError = dailyStats.reduce((sum, day) => sum + (day.error ?? 0), 0);
  const totalTimeout = dailyStats.reduce(
    (sum, day) => sum + (day.timeout ?? 0),
    0,
  );
  const totalUnknown = dailyStats.reduce(
    (sum, day) => sum + (day.unknown ?? 0),
    0,
  );
  const totalSkip = dailyStats.reduce((sum, day) => sum + (day.skip ?? 0), 0);
  const totalAbort = dailyStats.reduce((sum, day) => sum + (day.abort ?? 0), 0);

  const totalTests =
    totalPass +
    totalFail +
    totalError +
    totalTimeout +
    totalUnknown +
    totalSkip +
    totalAbort;
  const completionCount = totalPass + totalFail;
  const nonCompletionCount =
    totalError + totalTimeout + totalUnknown + totalSkip + totalAbort;

  const aggregatedStats: AggregatedTestResults = {
    totalTests,
    completionStats: {
      count: completionCount,
      percent: totalTests ? (completionCount / totalTests) * 100 : 0,
    },
    nonCompletionStats: {
      count: nonCompletionCount,
      percent: totalTests ? (nonCompletionCount / totalTests) * 100 : 0,
    },
    completionBreakdown: [
      {
        category: 'PASS',
        stats: {
          count: totalPass,
          percent: totalTests ? (totalPass / totalTests) * 100 : 0,
        },
      },
      {
        category: 'FAIL',
        stats: {
          count: totalFail,
          percent: totalTests ? (totalFail / totalTests) * 100 : 0,
        },
      },
    ].filter((item) => item.stats.count > 0),
    nonCompletionBreakdown: [
      {
        category: 'ERROR',
        stats: {
          count: totalError,
          percent: totalTests ? (totalError / totalTests) * 100 : 0,
        },
      },
      {
        category: 'TIMEOUT',
        stats: {
          count: totalTimeout,
          percent: totalTests ? (totalTimeout / totalTests) * 100 : 0,
        },
      },
      {
        category: 'UNKNOWN',
        stats: {
          count: totalUnknown,
          percent: totalTests ? (totalUnknown / totalTests) * 100 : 0,
        },
      },
      {
        category: 'SKIP',
        stats: {
          count: totalSkip,
          percent: totalTests ? (totalSkip / totalTests) * 100 : 0,
        },
      },
      {
        category: 'ABORT',
        stats: {
          count: totalAbort,
          percent: totalTests ? (totalAbort / totalTests) * 100 : 0,
        },
      },
    ].filter((item) => item.stats.count > 0),
  };

  return {dailyStats, aggregatedStats};
}

/** Generates mock recovery task stats. */
export function generateRecoveryTaskStats(
  startDate: GoogleDate,
  endDate: GoogleDate,
): RecoveryTaskStats {
  const {start, days} = getDates(startDate, endDate);
  const dailyStats: DailyRecoveryTasks[] = Array.from(
    {length: days},
    (_, i) => {
      const date = new Date(start);
      date.setDate(start.getDate() + i);

      const total = 5 + Math.floor(Math.random() * 20);
      const success = Math.floor(Math.random() * total * 0.8);
      const fail = total - success;
      return {
        date: formatDate(date),
        success,
        fail,
      };
    },
  );

  const totalSuccess = dailyStats.reduce(
    (sum, day) => sum + (day.success ?? 0),
    0,
  );
  const totalFail = dailyStats.reduce((sum, day) => sum + (day.fail ?? 0), 0);
  const totalTasks = totalSuccess + totalFail;

  const aggregatedStats: AggregatedRecoveryTasks = {
    totalTasks,
    outcomeBreakdown: [
      {
        category: 'SUCCESS',
        stats: {
          count: totalSuccess,
          percent: totalTasks ? (totalSuccess / totalTasks) * 100 : 0,
        },
      },
      {
        category: 'FAIL',
        stats: {
          count: totalFail,
          percent: totalTasks ? (totalFail / totalTasks) * 100 : 0,
        },
      },
    ].filter((item) => item.stats.count > 0),
  };

  return {dailyStats, aggregatedStats};
}
