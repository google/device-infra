import {
  HEALTH_COLORS,
  HEALTH_SUMMARY_COLORS,
  RECOVERY_COLORS,
  TEST_COLORS,
} from '../../../../core/constants/statistic_constants';
import {
  GoogleTypeDate,
  HealthinessStats,
  RecoveryOutcomeCategory,
  RecoveryTaskStats,
  TestResultCategory,
  TestResultGroup,
  TestResultStats,
} from '../../../../core/models/device_stats';
import {
  BreakdownChart,
  TableItem,
} from './statistic_breakdown/statistic_breakdown';

type PieChartOptions = google.visualization.PieChartOptions;
type ColumnChartOptions = google.visualization.ColumnChartOptions;

type ChartRowValue = string | number | boolean | Date | {v: number; f: string};

/**
 * Data structure for rendering a Google Column Chart.
 */
export interface ColumnChartData {
  columns: Array<{type: string; label: string}>;
  rows: Array<Array<ChartRowValue | null | undefined>>;
  options: ColumnChartOptions;
  hasData: boolean;
}

// --- Helpers ---

/**
 * Returns the color associated with a health state.
 */
export function getHealthColor(state: string): string {
  return HEALTH_COLORS[state.toLowerCase()] || '#BDBDBD';
}

/**
 * Returns the color associated with a test result category.
 */
export function getTestColor(state: string): string {
  return TEST_COLORS[state] || '#9E9E9E';
}

/**
 * Returns the color associated with a recovery outcome category.
 */
export function getRecoveryColor(state: string): string {
  return RECOVERY_COLORS[state] || '#9E9E9E';
}

/**
 * Returns a readable label for a test result category.
 */
export function getTestLabel(category: string): string {
  return category.replace('TEST_RESULT_CATEGORY_', '');
}

/**
 * Returns a readable label for a recovery outcome category.
 */
export function getRecoveryLabel(category: string): string {
  return category.replace('RECOVERY_OUTCOME_CATEGORY_', '');
}

/**
 * Collects unique categories from a list of items into a Set.
 */
export function collectCategories(
  items: Array<{category: string; percent?: number; count?: number}>,
  categorySet: Set<string>,
) {
  for (const item of items) {
    if (item.category) {
      categorySet.add(item.category);
    }
  }
}

function createPieChartOptions(colors: string[]): PieChartOptions {
  const options: PieChartOptions = {
    pieHole: 0.4,
    colors,
    backgroundColor: 'transparent',
    legend: {position: 'right', alignment: 'center'},
    chartArea: {left: 10, top: 20, width: '90%', height: '85%'},
  };

  if (colors.length === 1) {
    options.pieSliceTextStyle = {color: 'black'};
  }

  return options;
}

function createEmptyChartData(): ColumnChartData {
  return {columns: [], rows: [], options: {}, hasData: false};
}

function prepareColumnChartData<T extends {date: string | GoogleTypeDate}>(
  dataList: T[],
  columns: Array<{type: string; label: string}>,
  rowMapper: (item: T, date: Date) => Array<ChartRowValue | null | undefined>,
  baseOptions: ColumnChartOptions,
): ColumnChartData {
  const rows: Array<Array<ChartRowValue | null | undefined>> = [];
  const dates: Date[] = [];
  let hasData = false;

  dataList.forEach((d) => {
    let date: Date;
    if (typeof d.date === 'string') {
      date = new Date(`${d.date}T00:00:00`);
    } else {
      date = new Date(d.date.year, d.date.month - 1, d.date.day);
    }
    dates.push(date);
    const row = rowMapper(d, date);

    // Check for data presence
    if (
      row.some((val) => {
        if (typeof val === 'number') {
          return !isNaN(val) && Math.abs(val) > 0;
        } else if (
          val !== null &&
          typeof val === 'object' &&
          'v' in val &&
          typeof val.v === 'number'
        ) {
          return !isNaN(val.v) && Math.abs(val.v) > 0;
        }
        return false;
      })
    ) {
      hasData = true;
    }
    rows.push(row);
  });

  const options: ColumnChartOptions = {...baseOptions};
  if (hasData && dates.length > 0 && dates.length <= 15) {
    options.hAxis = options.hAxis || {};
    options.hAxis.ticks = dates;
  }

  return {columns, rows, options, hasData};
}

// --- Transformers ---

/**
 * Transforms health stats into summary chart data.
 */
export function getHealthSummaryChartData(
  stats: HealthinessStats | null,
): ColumnChartData {
  if (!stats) return createEmptyChartData();

  const options: ColumnChartOptions = {
    backgroundColor: 'transparent',
    isStacked: true,
    legend: {position: 'bottom'},
    vAxis: {
      title: 'Time Percentage',
      format: "#'%'",
      gridlines: {color: '#e0e0e0'},
      viewWindow: {min: 0, max: 100},
    },
    hAxis: {
      title: 'Date (PDT)',
      format: 'MMM d',
      gridlines: {color: 'transparent'},
    },
    colors: HEALTH_SUMMARY_COLORS,
    bar: {groupWidth: '60%'},
    chartArea: {left: 70, top: 20, width: '85%', height: '70%'},
  };

  const columns = [
    {type: 'date', label: 'Date'},
    {type: 'number', label: 'In Service'},
    {type: 'number', label: 'Out of Service'},
  ];

  return prepareColumnChartData(
    stats.dailyStats,
    columns,
    (d, date) => [
      date,
      d.healthinessSummary.inServicePercent ?? 0,
      d.healthinessSummary.outOfServicePercent ?? 0,
    ],
    options,
  );
}

/**
 * Transforms health stats into detailed breakdown chart data.
 */
export function getHealthDetailChartData(
  stats: HealthinessStats | null,
): ColumnChartData {
  if (!stats) return createEmptyChartData();

  const allCategories = new Set<string>();
  for (const day of stats.dailyStats || []) {
    collectCategories(
      day.healthinessSummary?.inServiceBreakdown || [],
      allCategories,
    );
    collectCategories(
      day.healthinessSummary?.outOfServiceBreakdown || [],
      allCategories,
    );
  }
  const states = Array.from(allCategories).sort((a, b) => {
    if (a.toLowerCase() === 'others') return 1;
    if (b.toLowerCase() === 'others') return -1;
    return a.localeCompare(b);
  });

  const options: ColumnChartOptions = {
    backgroundColor: 'transparent',
    isStacked: true,
    legend: {position: 'bottom', maxLines: 2},
    vAxis: {
      title: 'Time Percentage',
      format: "#'%'",
      gridlines: {color: '#e0e0e0'},
      viewWindow: {min: 0, max: 100},
    },
    hAxis: {
      title: 'Date (PDT)',
      format: 'MMM d',
      gridlines: {color: 'transparent'},
    },
    colors: states.map((s) => getHealthColor(s)),
    bar: {groupWidth: '60%'},
    chartArea: {left: 70, top: 20, width: '85%', height: '70%'},
  };

  const columns = [{type: 'date', label: 'Date'}];
  states.forEach((state) => {
    columns.push({
      type: 'number',
      label: state.toLowerCase() === 'others' ? 'Others' : state.toUpperCase(),
    });
  });

  return prepareColumnChartData(
    stats.dailyStats,
    columns,
    (d, date) => {
      const row: Array<Date | number> = [date];
      const breakdownMap: Record<string, number> = {};
      const addToMap = (items: Array<{category: string; percent?: number}>) => {
        for (const item of items) {
          breakdownMap[item.category.toUpperCase()] = item.percent ?? 0;
        }
      };
      addToMap(d.healthinessSummary?.inServiceBreakdown || []);
      addToMap(d.healthinessSummary?.outOfServiceBreakdown || []);

      states.forEach((state) => {
        row.push(breakdownMap[state.toUpperCase()] || 0);
      });
      return row;
    },
    options,
  );
}

/**
 * Transforms test result stats into chart data.
 */
export function getTestResultChartData(
  stats: TestResultStats | null,
): ColumnChartData {
  if (!stats) return createEmptyChartData();

  const dailyStats = stats.dailyStats || [];
  const existingCategories = new Set(
    dailyStats
      .flatMap((day) => day.categoryStats || [])
      .filter((stat) => stat.stats?.count && stat.stats.count > 0)
      .map((stat) => stat.category),
  );

  const sortedCategories = (
    Object.keys(TEST_COLORS) as TestResultCategory[]
  ).filter((cat) => existingCategories.has(cat));

  const colors = sortedCategories.map((cat) => getTestColor(cat));

  const options: ColumnChartOptions = {
    backgroundColor: 'transparent',
    isStacked: true,
    legend: {position: 'bottom'},
    colors,
    bar: {groupWidth: '75%'},
    chartArea: {left: 70, top: 20, width: '85%', height: '70%'},
    hAxis: {title: 'Date (PDT)', format: 'MMM d'},
    vAxis: {title: 'Number of Tests'},
  };

  const columns = [
    {type: 'date', label: 'Date'},
    ...sortedCategories.map((cat) => ({
      type: 'number',
      label: getTestLabel(cat),
    })),
  ];

  return prepareColumnChartData(
    dailyStats,
    columns,
    (d, date) => {
      const row: Array<Date | number | null | {v: number; f: string}> = [date];
      const statsMap = new Map(
        (d.categoryStats || []).map((s) => [s.category, s.stats]),
      );

      sortedCategories.forEach((cat) => {
        const itemStats = statsMap.get(cat);
        const val = itemStats?.count;
        if (typeof val === 'number' && val > 0) {
          const percent = itemStats?.percent ?? 0;
          const formatted = `${val} (${percent.toFixed(1)}%)`;
          row.push({v: val, f: formatted});
        } else {
          row.push(null);
        }
      });
      return row;
    },
    options,
  );
}

/**
 * Transforms recovery task stats into chart data.
 */
export function getRecoveryTaskChartData(
  stats: RecoveryTaskStats | null,
): ColumnChartData {
  if (!stats) return createEmptyChartData();

  const dailyStats = stats.dailyStats || [];
  const existingCategories = new Set(
    dailyStats
      .flatMap((day) => day.categoryStats || [])
      .filter((stat) => stat.stats?.count && stat.stats.count > 0)
      .map((stat) => stat.category ?? 'RECOVERY_OUTCOME_CATEGORY_UNSPECIFIED'),
  );

  const sortedCategories = (
    Object.keys(RECOVERY_COLORS) as RecoveryOutcomeCategory[]
  ).filter((cat) => existingCategories.has(cat));

  const colors = sortedCategories.map((cat) => getRecoveryColor(cat));

  const options: ColumnChartOptions = {
    backgroundColor: 'transparent',
    isStacked: true,
    legend: {position: 'bottom'},
    colors,
    bar: {groupWidth: '50%'},
    chartArea: {left: 60, top: 20, width: '85%', height: '70%'},
    hAxis: {title: 'Date (PDT)', format: 'MMM d'},
    vAxis: {title: 'Number of Tasks'},
  };

  const columns = [
    {type: 'date', label: 'Date'},
    ...sortedCategories.map((cat) => ({
      type: 'number',
      label: getRecoveryLabel(cat),
    })),
  ];

  return prepareColumnChartData(
    dailyStats,
    columns,
    (d, date) => {
      const row: Array<Date | number | null | {v: number; f: string}> = [date];
      const statsMap = new Map(
        (d.categoryStats || []).map((s) => [s.category, s.stats]),
      );

      sortedCategories.forEach((cat) => {
        const itemStats = statsMap.get(cat);
        const val = itemStats?.count;
        if (typeof val === 'number' && val > 0) {
          const percent = itemStats?.percent ?? 0;
          const formatted = `${val} (${percent.toFixed(1)}%)`;
          row.push({v: val, f: formatted});
        } else {
          row.push(null);
        }
      });
      return row;
    },
    options,
  );
}

/**
 * Transforms aggregated health stats into a table format.
 */
export function transformHealthStatsToTable(
  stats: HealthinessStats['aggregatedStats'] | undefined,
): TableItem[] {
  if (!stats) {
    return [];
  }

  return [
    {
      label: 'In Service',
      value: stats.inServicePercent,
      color: '#1e8e3e',
      type: 'summary' as const,
    },
    ...(stats.inServiceBreakdown || [])
      .filter((item) => item.percent && item.percent > 0)
      .map((item) => ({
        label: item.category.toUpperCase(),
        value: item.percent,
        color: getHealthColor(item.category.toLowerCase()),
        type: 'detail' as const,
      })),
    {
      label: 'Out of Service',
      value: stats.outOfServicePercent,
      color: '#d93025',
      type: 'summary' as const,
    },
    ...(stats.outOfServiceBreakdown || [])
      .filter((item) => item.percent && item.percent > 0)
      .map((item) => ({
        label:
          item.category.toUpperCase() === 'OTHERS'
            ? 'Others'
            : item.category.toUpperCase(),
        value: item.percent,
        color: getHealthColor(item.category.toLowerCase()),
        type: 'detail' as const,
      })),
  ];
}

/**
 * Transforms aggregated health stats into breakdown charts.
 */
export function transformHealthStatsToCharts(
  stats: HealthinessStats['aggregatedStats'] | undefined,
): BreakdownChart[] {
  if (!stats) {
    return [];
  }
  const charts: BreakdownChart[] = [];

  // Major Chart (In/Out Service)
  const majorData = new google.visualization.DataTable();
  majorData.addColumn('string', 'Status');
  majorData.addColumn('number', 'Percentage');

  const majorColors: string[] = [];
  if (stats.inServicePercent && stats.inServicePercent > 0) {
    majorData.addRow(['In Service', stats.inServicePercent]);
    majorColors.push(HEALTH_SUMMARY_COLORS[0]);
  }
  if (stats.outOfServicePercent && stats.outOfServicePercent > 0) {
    majorData.addRow(['Out of Service', stats.outOfServicePercent]);
    majorColors.push(HEALTH_SUMMARY_COLORS[1]);
  }

  charts.push({
    title: 'Primary Status',
    data: majorData,
    options: createPieChartOptions(majorColors),
  });

  // Minor Chart (Detailed Breakdown)
  const minorData = new google.visualization.DataTable();
  minorData.addColumn('string', 'Status');
  minorData.addColumn('number', 'Value');

  const minorColors: string[] = [];

  const allBreakdown = [
    ...(stats.inServiceBreakdown || []),
    ...(stats.outOfServiceBreakdown || []),
  ];

  allBreakdown.forEach((item) => {
    if (item.percent && item.percent > 0) {
      minorData.addRow([item.category.toUpperCase(), item.percent]);
      minorColors.push(getHealthColor(item.category));
    }
  });

  charts.push({
    title: 'Detailed Status Breakdown',
    data: minorData,
    options: createPieChartOptions(minorColors),
  });

  return charts;
}

/**
 * Transforms aggregated test stats into a table format.
 */
export function transformTestStatsToTable(
  stats: TestResultStats['summary'] | undefined,
): TableItem[] {
  if (!stats) {
    return [];
  }

  const items: TableItem[] = [];

  // Total
  items.push({
    label: 'Total',
    count: stats.totalCount,
    percent: 100,
    type: 'total' as const,
  });

  // Helper to add groups
  const addGroup = (group: TestResultGroup, color: string) => {
    if (!group || !group.totalStats?.count) return;

    items.push({
      label: group.displayName,
      count: group.totalStats.count,
      percent: group.totalStats.percent,
      color,
      type: 'summary' as const,
    });

    (group.breakdownItems ?? [])
      .filter((item) => item.stats.count && item.stats.count > 0)
      .forEach((item) => {
        const label = getTestLabel(item.category);
        items.push({
          label: label === 'OTHER' ? 'Others' : label,
          count: item.stats.count,
          percent: item.stats.percent,
          color: getTestColor(item.category),
          type: 'detail' as const,
        });
      });
  };

  addGroup(stats.completionGroup, '#1e8e3e');
  addGroup(stats.nonCompletionGroup, '#d93025');
  addGroup(stats.unknownGroup, '#c87619');

  return items;
}

/**
 * Transforms aggregated test stats into breakdown charts.
 */
export function transformTestStatsToCharts(
  stats: TestResultStats['summary'] | undefined,
): BreakdownChart[] {
  if (!stats) {
    return [];
  }

  const charts: BreakdownChart[] = [];

  // Major Chart (Completion/Non-Completion/Unknown)
  const majorData = new google.visualization.DataTable();
  majorData.addColumn('string', 'Category');
  majorData.addColumn('number', 'Count');

  const majorColors: string[] = [];
  if (
    stats.completionGroup?.totalStats?.count &&
    stats.completionGroup.totalStats.count > 0
  ) {
    majorData.addRow([
      stats.completionGroup.displayName,
      stats.completionGroup.totalStats.count,
    ]);
    majorColors.push('#2E7D32');
  }
  if (
    stats.nonCompletionGroup?.totalStats?.count &&
    stats.nonCompletionGroup.totalStats.count > 0
  ) {
    majorData.addRow([
      stats.nonCompletionGroup.displayName,
      stats.nonCompletionGroup.totalStats.count,
    ]);
    majorColors.push('#C62828');
  }
  if (
    stats.unknownGroup?.totalStats?.count &&
    stats.unknownGroup.totalStats.count > 0
  ) {
    majorData.addRow([
      stats.unknownGroup.displayName,
      stats.unknownGroup.totalStats.count,
    ]);
    majorColors.push('#546E7A');
  }

  charts.push({
    title: 'Primary Result',
    data: majorData,
    options: createPieChartOptions(majorColors),
  });

  // Minor Chart (Detailed Result)
  const minorData = new google.visualization.DataTable();
  minorData.addColumn('string', 'Result');
  minorData.addColumn('number', 'Count');

  const minorColors: string[] = [];

  const allBreakdown = [
    ...(stats.completionGroup?.breakdownItems ?? []),
    ...(stats.nonCompletionGroup?.breakdownItems ?? []),
    ...(stats.unknownGroup?.breakdownItems ?? []),
  ];

  allBreakdown.forEach((item) => {
    if (item.stats.count && item.stats.count > 0) {
      const label = getTestLabel(item.category);
      minorData.addRow([
        label === 'OTHER' ? 'Others' : label,
        item.stats.count,
      ]);
      minorColors.push(getTestColor(item.category));
    }
  });

  charts.push({
    title: 'Detailed Result Breakdown',
    data: minorData,
    options: createPieChartOptions(minorColors),
  });

  return charts;
}

/**
 * Transforms aggregated recovery stats into a table format.
 */
export function transformRecoveryStatsToTable(
  stats: RecoveryTaskStats['summary'] | undefined,
): TableItem[] {
  if (!stats) {
    return [];
  }

  return [
    {
      label: 'Total',
      count: stats.totalCount ?? 0,
      percent: 100,
      type: 'total' as const,
    },
    ...(stats.outcomeBreakdown ?? [])
      .filter((item) => item.stats?.count && item.stats.count > 0)
      .map((item) => ({
        label: getRecoveryLabel(
          item.category ?? 'RECOVERY_OUTCOME_CATEGORY_UNSPECIFIED',
        ),
        count: item.stats?.count ?? 0,
        percent: item.stats?.percent ?? 0,
        color: getRecoveryColor(
          item.category ?? 'RECOVERY_OUTCOME_CATEGORY_UNSPECIFIED',
        ),
        type: 'detail' as const,
      })),
  ];
}

/**
 * Transforms aggregated recovery stats into breakdown charts.
 */
export function transformRecoveryStatsToCharts(
  stats: RecoveryTaskStats['summary'] | undefined,
): BreakdownChart[] {
  if (!stats) {
    return [];
  }

  const charts: BreakdownChart[] = [];
  const chartData = new google.visualization.DataTable();
  chartData.addColumn('string', 'Result');
  chartData.addColumn('number', 'Count');

  const colors: string[] = [];
  (stats.outcomeBreakdown ?? []).forEach((item) => {
    if (item.stats?.count && item.stats.count > 0) {
      chartData.addRow([
        getRecoveryLabel(
          item.category ?? 'RECOVERY_OUTCOME_CATEGORY_UNSPECIFIED',
        ),
        item.stats.count,
      ]);
      colors.push(
        getRecoveryColor(
          item.category ?? 'RECOVERY_OUTCOME_CATEGORY_UNSPECIFIED',
        ),
      );
    }
  });

  charts.push({
    title: 'Recovery Result',
    data: chartData,
    options: createPieChartOptions(colors),
  });

  return charts;
}
