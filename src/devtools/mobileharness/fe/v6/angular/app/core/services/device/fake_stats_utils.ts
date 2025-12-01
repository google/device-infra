import {
  AggregatedHealthiness,
  AggregatedRecoveryTasks,
  AggregatedTestResults,
  DailyHealthiness,
  DailyRecoveryTasks,
  DailyTestResults,
  HealthinessStats,
  RecoveryTaskStats,
  TestResultStats,
} from '../../models/device_stats';

function getDates(
  startDate: string,
  endDate: string,
): {start: Date; days: number} {
  const start = new Date(startDate);
  const end = new Date(endDate);

  if (isNaN(start.getTime()) || isNaN(end.getTime())) {
    throw new Error('Invalid start or end date');
  }

  if (start > end) {
    throw new Error('Start date must be before end date');
  }

  start.setHours(0, 0, 0, 0);
  end.setHours(0, 0, 0, 0);

  const days = Math.floor((end.getTime() - start.getTime()) / 86400000) + 1;
  return {start, days};
}

/** Generates mock healthiness stats. */
export function generateHealthinessStats(
  startDate: string,
  endDate: string,
): HealthinessStats {
  const DAY_IN_MS = 86400000; // 24 * 60 * 60 * 1000
  const {start, days} = getDates(startDate, endDate);

  const dailyStats: DailyHealthiness[] = Array.from({length: days}, (_, i) => {
    const date = new Date(start.getTime() + i * DAY_IN_MS);

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
    return {
      date: date.toISOString().substring(0, 10),
      idle,
      busy,
      lameduck,
      init,
      dying,
      dirty,
      prepping,
      missing,
      failed,
      others: rem,
    };
  });

  const totalIdle = dailyStats.reduce((sum, day) => sum + day.idle, 0);
  const totalBusy = dailyStats.reduce((sum, day) => sum + day.busy, 0);
  const totalLameduck = dailyStats.reduce((sum, day) => sum + day.lameduck, 0);
  const totalInit = dailyStats.reduce((sum, day) => sum + day.init, 0);
  const totalDying = dailyStats.reduce((sum, day) => sum + day.dying, 0);
  const totalDirty = dailyStats.reduce((sum, day) => sum + day.dirty, 0);
  const totalPrepping = dailyStats.reduce((sum, day) => sum + day.prepping, 0);
  const totalMissing = dailyStats.reduce((sum, day) => sum + day.missing, 0);
  const totalFailed = dailyStats.reduce((sum, day) => sum + day.failed, 0);
  const totalOthers = dailyStats.reduce((sum, day) => sum + day.others, 0);

  const inServiceTotal = totalIdle + totalBusy;
  const outOfServiceTotal =
    totalLameduck +
    totalInit +
    totalDying +
    totalDirty +
    totalPrepping +
    totalMissing +
    totalFailed +
    totalOthers;

  const total = inServiceTotal + outOfServiceTotal;

  const aggregatedStats: AggregatedHealthiness = {
    inServicePercent: total ? (inServiceTotal / total) * 100 : 0,
    outOfServicePercent: total ? (outOfServiceTotal / total) * 100 : 0,
    statusBreakdown: [
      {status: 'IDLE', percent: total ? (totalIdle / total) * 100 : 0},
      {status: 'BUSY', percent: total ? (totalBusy / total) * 100 : 0},
      {status: 'LAMEDUCK', percent: total ? (totalLameduck / total) * 100 : 0},
      {status: 'INIT', percent: total ? (totalInit / total) * 100 : 0},
      {status: 'DYING', percent: total ? (totalDying / total) * 100 : 0},
      {status: 'DIRTY', percent: total ? (totalDirty / total) * 100 : 0},
      {status: 'PREPPING', percent: total ? (totalPrepping / total) * 100 : 0},
      {status: 'MISSING', percent: total ? (totalMissing / total) * 100 : 0},
      {status: 'FAILED', percent: total ? (totalFailed / total) * 100 : 0},
      {status: 'OTHERS', percent: total ? (totalOthers / total) * 100 : 0},
    ].filter((item) => item.percent > 0),
  };

  return {dailyStats, aggregatedStats};
}

/** Generates mock test result stats. */
export function generateTestResultStats(
  startDate: string,
  endDate: string,
): TestResultStats {
  const DAY_IN_MS = 86400000; // 24 * 60 * 60 * 1000

  const {start, days} = getDates(startDate, endDate);
  const dailyStats: DailyTestResults[] = Array.from({length: days}, (_, i) => {
    const date = new Date(start.getTime() + i * DAY_IN_MS);

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

    return {
      date: date.toISOString().substring(0, 10),
      pass,
      fail,
      error,
      timeout,
      other: rem,
    };
  });

  const totalPass = dailyStats.reduce((sum, day) => sum + day.pass, 0);
  const totalFail = dailyStats.reduce((sum, day) => sum + day.fail, 0);
  const totalError = dailyStats.reduce((sum, day) => sum + day.error, 0);
  const totalTimeout = dailyStats.reduce((sum, day) => sum + day.timeout, 0);
  const totalOther = dailyStats.reduce((sum, day) => sum + day.other, 0);

  const totalTests =
    totalPass + totalFail + totalError + totalTimeout + totalOther;
  const completionCount = totalPass + totalFail;
  const nonCompletionCount = totalError + totalTimeout + totalOther;

  const aggregatedStats: AggregatedTestResults = {
    totalTests,
    completionCount,
    nonCompletionCount,
    resultBreakdown: [
      {
        result: 'PASS',
        count: totalPass,
        percent: totalTests ? (totalPass / totalTests) * 100 : 0,
      },
      {
        result: 'FAIL',
        count: totalFail,
        percent: totalTests ? (totalFail / totalTests) * 100 : 0,
      },
      {
        result: 'ERROR',
        count: totalError,
        percent: totalTests ? (totalError / totalTests) * 100 : 0,
      },
      {
        result: 'TIMEOUT',
        count: totalTimeout,
        percent: totalTests ? (totalTimeout / totalTests) * 100 : 0,
      },
      {
        result: 'OTHER',
        count: totalOther,
        percent: totalTests ? (totalOther / totalTests) * 100 : 0,
      },
    ].filter((item) => item.count > 0),
  };

  return {dailyStats, aggregatedStats};
}

/** Generates mock recovery task stats. */
export function generateRecoveryTaskStats(
  startDate: string,
  endDate: string,
): RecoveryTaskStats {
  const DAY_IN_MS = 86400000; // 24 * 60 * 60 * 1000

  const {start, days} = getDates(startDate, endDate);
  const dailyStats: DailyRecoveryTasks[] = Array.from(
    {length: days},
    (_, i) => {
      const date = new Date(start.getTime() + i * DAY_IN_MS);

      const total = 5 + Math.floor(Math.random() * 20);
      const success = Math.floor(Math.random() * total * 0.8);
      const fail = total - success;
      return {
        date: date.toISOString().substring(0, 10),
        success,
        fail,
      };
    },
  );

  const totalSuccess = dailyStats.reduce((sum, day) => sum + day.success, 0);
  const totalFail = dailyStats.reduce((sum, day) => sum + day.fail, 0);
  const totalTasks = totalSuccess + totalFail;

  const aggregatedStats: AggregatedRecoveryTasks = {
    totalTasks,
    outcomeBreakdown: [
      {
        outcome: 'SUCCESS',
        count: totalSuccess,
        percent: totalTasks ? (totalSuccess / totalTasks) * 100 : 0,
      },
      {
        outcome: 'FAIL',
        count: totalFail,
        percent: totalTasks ? (totalFail / totalTasks) * 100 : 0,
      },
    ].filter((item) => item.count > 0),
  };

  return {dailyStats, aggregatedStats};
}
