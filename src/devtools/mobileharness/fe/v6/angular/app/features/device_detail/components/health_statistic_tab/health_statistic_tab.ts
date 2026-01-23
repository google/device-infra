import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  Input,
  OnDestroy,
  OnInit,
  QueryList,
  signal,
  ViewChild,
  ViewChildren,
  WritableSignal,
} from '@angular/core';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTableModule} from '@angular/material/table';
import {load} from '@google-web-components/google-chart/loader';
import {Observable} from 'rxjs';
import {finalize} from 'rxjs/operators';
import {
  HEALTH_COLORS,
  HEALTH_SUMMARY_COLORS,
  RECOVERY_CHART_COLORS,
  RECOVERY_COLORS,
  TEST_CHART_COLORS,
  TEST_COLORS,
} from '../../../../core/constants/statistic_constants';
import {
  HealthinessStats,
  RecoveryTaskStats,
  TestResultStats,
} from '../../../../core/models/device_stats';
import {DEVICE_SERVICE} from '../../../../core/services/device/device_service';
import {DateRangePicker} from '../../../../shared/components/date_range_picker/date_range_picker';
import {InfoCard} from '../../../../shared/components/info_card/info_card';
import {
  MasterDetailLayout,
  NavItem,
} from '../../../../shared/components/master_detail_layout/master_detail_layout';
import {dateUtils} from '../../../../shared/utils/date_utils';
import {
  BreakdownChart,
  StatisticBreakdown,
  TableItem,
} from './statistic_breakdown/statistic_breakdown';

type ColumnChartOptions = google.visualization.ColumnChartOptions;
type PieChartOptions = google.visualization.PieChartOptions;

/**
 * A tab component to show device healthiness, test result and recovery task
 * statistics.
 */
@Component({
  selector: 'app-health-statistic-tab',
  standalone: true,
  templateUrl: './health_statistic_tab.ng.html',
  styleUrl: './health_statistic_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MasterDetailLayout,
    InfoCard,
    DateRangePicker,
    MatProgressBarModule,
    MatTableModule,
    StatisticBreakdown,
  ],
  host: {
    '(window:resize)': 'onWindowResize()',
  },
})
export class HealthStatisticTab implements OnInit, OnDestroy {
  @Input({required: true}) deviceId!: string;

  private readonly deviceService = inject(DEVICE_SERVICE);

  private readonly googleChartsLoaded: Promise<void>;

  constructor() {
    this.googleChartsLoaded = new Promise<void>((resolve) => {
      load().then(() => {
        google.charts.setOnLoadCallback(() => {
          resolve();
        });
      });
    });
  }

  navList: NavItem[] = [
    {
      id: 'health-statistic',
      label: 'Healthiness Statistics',
    },
    {
      id: 'test-result-statistic',
      label: 'Test Result Statistics',
    },
    {
      id: 'recovery-task-statistic',
      label: 'Recovery Task Statistics',
    },
  ];

  // Healthiness statistics.
  healthinessStats: HealthinessStats | null = null;
  healthDetailStates: string[] = [];
  healthDataSource: TableItem[] = [];
  healthBreakdownCharts: BreakdownChart[] = [];
  healthDateRangeString = '';

  // Test result statistics.
  testResultStats: TestResultStats | null = null;
  testDataSource: TableItem[] = [];
  testBreakdownCharts: BreakdownChart[] = [];
  testDateRangeString = '';

  // Recovery task statistics.
  recoveryTaskStats: RecoveryTaskStats | null = null;
  recoveryDataSource: TableItem[] = [];
  recoveryBreakdownCharts: BreakdownChart[] = [];
  recoveryDateRangeString = '';

  loadingHealth = signal(false);
  loadingTest = signal(false);
  loadingRecovery = signal(false);

  // ViewChilds for charts.
  private healthChartSummaryElementInternal: ElementRef | undefined;
  @ViewChild('healthChartSummary')
  set healthChartSummaryElement(value: ElementRef | undefined) {
    this.healthChartSummaryElementInternal = value;
    this.googleChartsLoaded.then(() => {
      this.drawHealthinessSummaryCharts();
    });
  }
  get healthChartSummaryElement(): ElementRef | undefined {
    return this.healthChartSummaryElementInternal;
  }

  private healthChartDetailElementInternal: ElementRef | undefined;
  @ViewChild('healthChartDetail')
  set healthChartDetailElement(value: ElementRef | undefined) {
    this.healthChartDetailElementInternal = value;
    this.googleChartsLoaded.then(() => {
      this.drawHealthinessDetailCharts();
    });
  }
  get healthChartDetailElement(): ElementRef | undefined {
    return this.healthChartDetailElementInternal;
  }

  private testChartElementInternal: ElementRef | undefined;
  @ViewChild('testChart')
  set testChartElement(value: ElementRef | undefined) {
    this.testChartElementInternal = value;
    this.googleChartsLoaded.then(() => {
      this.drawTestStackedBarChart();
    });
  }
  get testChartElement(): ElementRef | undefined {
    return this.testChartElementInternal;
  }

  private recoveryChartElementInternal: ElementRef | undefined;
  @ViewChild('recoveryChart')
  set recoveryChartElement(value: ElementRef | undefined) {
    this.recoveryChartElementInternal = value;
    this.googleChartsLoaded.then(() => {
      this.drawRecoveryStackedBarChart();
    });
  }
  get recoveryChartElement(): ElementRef | undefined {
    return this.recoveryChartElementInternal;
  }

  @ViewChildren(StatisticBreakdown)
  breakdownComponents!: QueryList<StatisticBreakdown>;

  private resizeTimeout: ReturnType<typeof setTimeout> | undefined;

  onWindowResize() {
    if (this.resizeTimeout) {
      clearTimeout(this.resizeTimeout);
    }

    this.resizeTimeout = setTimeout(() => {
      this.renderHealthinessCard();
      this.renderTestCard();
      this.renderRecoveryCard();
    }, 200);
  }

  ngOnInit() {}

  ngOnDestroy() {
    if (this.resizeTimeout) {
      clearTimeout(this.resizeTimeout);
    }
  }

  onDateRangeChange(
    type: 'health' | 'test' | 'recovery',
    range: {start: Date; end: Date},
  ) {
    const rangeStr = dateUtils.formatDateRange(range.start, range.end);

    switch (type) {
      case 'health':
        this.healthDateRangeString = rangeStr;
        this.loadStats(
          range,
          this.loadingHealth,
          (start, end) =>
            this.deviceService.getDeviceHealthinessStats(
              this.deviceId,
              dateUtils.toGoogleDate(start),
              dateUtils.toGoogleDate(end),
            ),
          (stats) => {
            this.healthinessStats = stats;
            this.updateHealthDetailStates();
            this.renderHealthinessCard();
          },
        );
        break;
      case 'test':
        this.testDateRangeString = rangeStr;
        this.loadStats(
          range,
          this.loadingTest,
          (start, end) =>
            this.deviceService.getDeviceTestResultStats(
              this.deviceId,
              dateUtils.toGoogleDate(start),
              dateUtils.toGoogleDate(end),
            ),
          (stats) => {
            this.testResultStats = stats;
            this.renderTestCard();
          },
        );
        break;
      case 'recovery':
        this.recoveryDateRangeString = rangeStr;
        this.loadStats(
          range,
          this.loadingRecovery,
          (start, end) =>
            this.deviceService.getDeviceRecoveryTaskStats(
              this.deviceId,
              dateUtils.toGoogleDate(start),
              dateUtils.toGoogleDate(end),
            ),
          (stats) => {
            this.recoveryTaskStats = stats;
            this.renderRecoveryCard();
          },
        );
        break;
      default:
        break;
    }
  }

  private loadStats<T>(
    range: {start: Date; end: Date},
    loadingSignal: WritableSignal<boolean>,
    fetchData: (start: Date, end: Date) => Observable<T>,
    onSuccess: (stats: T) => void,
  ) {
    loadingSignal.set(true);

    fetchData(range.start, range.end)
      .pipe(
        finalize(() => {
          loadingSignal.set(false);
        }),
      )
      .subscribe((stats) => {
        onSuccess(stats);
      });
  }

  async renderHealthinessCard() {
    await this.googleChartsLoaded;
    this.drawHealthinessSummaryCharts();
    this.drawHealthinessDetailCharts();
    this.calculateHealthinessTableData();
    this.calculateHealthPieChartsData();
  }

  async renderTestCard() {
    await this.googleChartsLoaded;
    this.drawTestStackedBarChart();
    this.calculateTestTableData();
    this.calculateTestPieChartsData();
  }

  async renderRecoveryCard() {
    await this.googleChartsLoaded;
    this.drawRecoveryStackedBarChart();
    this.calculateRecoveryTableData();
    this.calculateRecoveryPieChartsData();
  }

  // Healthiness Summary Chart
  healthinessSummaryChartOptions = (): ColumnChartOptions => ({
    backgroundColor: 'transparent',
    isStacked: 'percent',
    legend: {position: 'bottom'},
    vAxis: {
      title: 'Time Percentage',
      format: 'percent',
      gridlines: {color: '#e0e0e0'},
    },
    hAxis: {
      title: 'Date (PDT)',
      format: 'MMM d',
      gridlines: {color: 'transparent'},
    },
    colors: HEALTH_SUMMARY_COLORS,
    bar: {groupWidth: '60%'},
    chartArea: {left: 60, top: 20, width: '85%', height: '70%'},
  });
  private drawHealthinessSummaryCharts() {
    // --- Summary Chart ---
    const summaryColumns = [
      {type: 'date', label: 'Date'},
      {type: 'number', label: 'In Service'},
      {type: 'number', label: 'Out of Service'},
    ];

    this.drawChart(
      this.healthChartSummaryElement,
      this.healthinessStats?.dailyStats || [],
      summaryColumns,
      (d, date) => [
        date,
        d.healthinessSummary.inServicePercent ?? 0,
        d.healthinessSummary.outOfServicePercent ?? 0,
      ],
      this.healthinessSummaryChartOptions(),
    );
  }

  // Healthiness Detail Chart
  healthinessDetailChartOptions = (states: string[]): ColumnChartOptions => ({
    backgroundColor: 'transparent',
    isStacked: 'percent',
    legend: {position: 'bottom', maxLines: 2},
    vAxis: {
      title: 'Time Percentage',
      format: 'percent',
      gridlines: {color: '#e0e0e0'},
    },
    hAxis: {
      title: 'Date (PDT)',
      format: 'MMM d',
      gridlines: {color: 'transparent'},
    },
    colors: states.map((s) => this.getHealthColor(s)),
    bar: {groupWidth: '60%'},
    chartArea: {left: 60, top: 20, width: '85%', height: '70%'},
  });
  private updateHealthDetailStates() {
    if (!this.healthinessStats) {
      this.healthDetailStates = [];
      return;
    }
    const allCategories = new Set<string>();
    for (const day of this.healthinessStats.dailyStats) {
      this.collectCategories(
        day.healthinessSummary.inServiceBreakdown,
        allCategories,
      );
      this.collectCategories(
        day.healthinessSummary.outOfServiceBreakdown,
        allCategories,
      );
    }
    this.healthDetailStates = Array.from(allCategories).sort((a, b) => {
      if (a.toLowerCase() === 'others') return 1;
      if (b.toLowerCase() === 'others') return -1;
      return a.localeCompare(b);
    });
  }

  private collectCategories(
    items: Array<{category: string; percent?: number; count?: number}>,
    categorySet: Set<string>,
  ) {
    for (const item of items) {
      if ((item.percent ?? 0) > 0 || (item.count ?? 0) > 0) {
        categorySet.add(item.category);
      }
    }
  }

  private drawHealthinessDetailCharts() {
    if (!this.healthinessStats) return;

    // --- Detail Chart ---
    const detailColumns = [{type: 'date', label: 'Date'}];
    this.healthDetailStates.forEach((state) => {
      detailColumns.push({
        type: 'number',
        label:
          state.toLowerCase() === 'others' ? 'Others' : state.toUpperCase(),
      });
    });

    this.drawChart(
      this.healthChartDetailElement,
      this.healthinessStats.dailyStats,
      detailColumns,
      (d, date) => {
        const row: Array<Date | number> = [date];
        const breakdownMap: Record<string, number> = {};
        const addToMap = (
          items: Array<{category: string; percent?: number}>,
        ) => {
          for (const item of items) {
            breakdownMap[item.category.toUpperCase()] = item.percent ?? 0;
          }
        };
        addToMap(d.healthinessSummary.inServiceBreakdown);
        addToMap(d.healthinessSummary.outOfServiceBreakdown);

        this.healthDetailStates.forEach((state) => {
          row.push(breakdownMap[state.toUpperCase()] || 0);
        });
        return row;
      },
      this.healthinessDetailChartOptions(this.healthDetailStates),
    );
  }

  // Healthiness Breakdown Table and Charts
  private calculateHealthinessTableData() {
    if (!this.healthinessStats || !this.healthinessStats.aggregatedStats) {
      return;
    }

    const stats = this.healthinessStats.aggregatedStats;

    this.healthDataSource = [
      {
        label: 'In Service',
        value: stats.inServicePercent,
        color: '#1e8e3e',
        type: 'summary' as const,
      },
      ...stats.inServiceBreakdown.map((item) => ({
        label: item.category.toUpperCase(),
        value: item.percent,
        color: this.getHealthColor(item.category.toLowerCase()),
        type: 'detail' as const,
      })),
      {
        label: 'Out of Service',
        value: stats.outOfServicePercent,
        color: '#d93025',
        type: 'summary' as const,
      },
      ...stats.outOfServiceBreakdown.map((item) => ({
        label:
          item.category.toUpperCase() === 'OTHERS'
            ? 'Others'
            : item.category.toUpperCase(),
        value: item.percent,
        color: this.getHealthColor(item.category.toLowerCase()),
        type: 'detail' as const,
      })),
    ];
  }
  private calculateHealthPieChartsData() {
    if (!this.healthinessStats || !this.healthinessStats.aggregatedStats) {
      return;
    }

    const data = this.healthinessStats.aggregatedStats;
    const charts: BreakdownChart[] = [];

    // Major Chart (In/Out Service)
    const majorData = new google.visualization.DataTable();
    majorData.addColumn('string', 'Status');
    majorData.addColumn('number', 'Percentage');

    const majorColors: string[] = [];
    if (data.inServicePercent && data.inServicePercent > 0) {
      majorData.addRow(['In Service', data.inServicePercent]);
      majorColors.push(HEALTH_SUMMARY_COLORS[0]);
    }
    if (data.outOfServicePercent && data.outOfServicePercent > 0) {
      majorData.addRow(['Out of Service', data.outOfServicePercent]);
      majorColors.push(HEALTH_SUMMARY_COLORS[1]);
    }

    const majorOptions: PieChartOptions = {
      pieHole: 0.4,
      colors: majorColors,
      backgroundColor: 'transparent',
      legend: {position: 'right', alignment: 'center'},
      chartArea: {left: 10, top: 20, width: '90%', height: '85%'},
      sliceVisibilityThreshold: 0,
    };

    charts.push({
      title: 'Primary Status',
      data: majorData,
      options: majorOptions,
    });

    // Minor Chart (Detailed Breakdown)
    const minorData = new google.visualization.DataTable();
    minorData.addColumn('string', 'Status');
    minorData.addColumn('number', 'Value');

    const minorColors: string[] = [];

    const allBreakdown = [
      ...data.inServiceBreakdown,
      ...data.outOfServiceBreakdown,
    ];

    allBreakdown.forEach((item) => {
      if (item.percent && item.percent > 0) {
        minorData.addRow([item.category.toUpperCase(), item.percent]);
        minorColors.push(this.getHealthColor(item.category));
      }
    });

    const minorOptions: PieChartOptions = {
      pieHole: 0.4,
      colors: minorColors,
      backgroundColor: 'transparent',
      legend: {position: 'right', alignment: 'center'},
      chartArea: {left: 10, top: 20, width: '90%', height: '85%'},
      sliceVisibilityThreshold: 0,
    };

    charts.push({
      title: 'Detailed Status Breakdown',
      data: minorData,
      options: minorOptions,
    });

    this.healthBreakdownCharts = charts;
  }

  // Test Result Chart
  testResultChartOptions = (): ColumnChartOptions => ({
    backgroundColor: 'transparent',
    isStacked: true,
    legend: {position: 'bottom'},
    colors: TEST_CHART_COLORS,
    bar: {groupWidth: '50%'},
    chartArea: {left: 60, top: 20, width: '85%', height: '70%'},
    hAxis: {title: 'Date (PDT)', format: 'MMM d'},
    vAxis: {title: 'Number of Tests'},
  });
  private drawTestStackedBarChart() {
    const columns = [
      {type: 'date', label: 'Date'},
      {type: 'number', label: 'PASS'},
      {type: 'number', label: 'FAIL'},
      {type: 'number', label: 'ERROR'},
      {type: 'number', label: 'TIMEOUT'},
      {type: 'number', label: 'Others'},
    ];

    this.drawChart(
      this.testChartElement,
      this.testResultStats?.dailyStats || [],
      columns,
      (d, date) => [
        date,
        d.pass ?? 0,
        d.fail ?? 0,
        d.error ?? 0,
        d.timeout ?? 0,
        d.other ?? 0,
      ],
      this.testResultChartOptions(),
    );
  }

  // Test Result Breakdown Table and Charts
  private calculateTestTableData() {
    if (!this.testResultStats || !this.testResultStats.aggregatedStats) {
      return;
    }

    const stats = this.testResultStats.aggregatedStats;

    this.testDataSource = [
      {
        label: 'Total',
        count: stats.totalTests,
        percent: 100,
        type: 'total' as const,
      },
      {
        label: 'Completion',
        count: stats.completionStats.count,
        percent: stats.completionStats.percent,
        color: '#1e8e3e',
        type: 'summary' as const,
      },
      ...stats.completionBreakdown.map((item) => ({
        label: item.category.toUpperCase(),
        count: item.stats.count,
        percent: item.stats.percent,
        color: this.getTestColor(item.category.toLowerCase()),
        type: 'detail' as const,
      })),
      {
        label: 'Non-Completion',
        count: stats.nonCompletionStats.count,
        percent: stats.nonCompletionStats.percent,
        color: '#d93025',
        type: 'summary' as const,
      },
      ...stats.nonCompletionBreakdown.map((item) => ({
        label:
          item.category.toUpperCase() === 'OTHER'
            ? 'Others'
            : item.category.toUpperCase(),
        count: item.stats.count,
        percent: item.stats.percent,
        color: this.getTestColor(item.category.toLowerCase()),
        type: 'detail' as const,
      })),
    ];
  }
  private calculateTestPieChartsData() {
    if (!this.testResultStats || !this.testResultStats.aggregatedStats) {
      return;
    }

    const data = this.testResultStats.aggregatedStats;
    const charts: BreakdownChart[] = [];

    // Major Chart (Completion/Non-Completion)
    const majorData = new google.visualization.DataTable();
    majorData.addColumn('string', 'Category');
    majorData.addColumn('number', 'Count');

    const majorColors: string[] = [];
    if (data.completionStats.count && data.completionStats.count > 0) {
      majorData.addRow(['Completion', data.completionStats.count]);
      majorColors.push('#2E7D32');
    }
    if (data.nonCompletionStats.count && data.nonCompletionStats.count > 0) {
      majorData.addRow(['Non-Completion', data.nonCompletionStats.count]);
      majorColors.push('#C62828');
    }

    const majorOptions: PieChartOptions = {
      pieHole: 0.4,
      colors: majorColors,
      backgroundColor: 'transparent',
      legend: {position: 'right', alignment: 'center'},
      chartArea: {left: 10, top: 20, width: '90%', height: '85%'},
      sliceVisibilityThreshold: 0,
    };

    charts.push({
      title: 'Primary Result',
      data: majorData,
      options: majorOptions,
    });

    // Minor Chart (Detailed Result)
    const minorData = new google.visualization.DataTable();
    minorData.addColumn('string', 'Result');
    minorData.addColumn('number', 'Count');

    const minorColors: string[] = [];

    const allBreakdown = [
      ...data.completionBreakdown,
      ...data.nonCompletionBreakdown,
    ];

    allBreakdown.forEach((item) => {
      if (item.stats.count && item.stats.count > 0) {
        minorData.addRow([
          item.category.toUpperCase() === 'OTHER'
            ? 'Others'
            : item.category.toUpperCase(),
          item.stats.count,
        ]);
        minorColors.push(this.getTestColor(item.category));
      }
    });

    const minorOptions: PieChartOptions = {
      pieHole: 0.4,
      colors: minorColors,
      backgroundColor: 'transparent',
      legend: {position: 'right', alignment: 'center'},
      chartArea: {left: 10, top: 20, width: '90%', height: '85%'},
      sliceVisibilityThreshold: 0,
    };

    charts.push({
      title: 'Detailed Result Breakdown',
      data: minorData,
      options: minorOptions,
    });

    this.testBreakdownCharts = charts;
  }

  // Recovery Task Chart
  recoveryTaskChartOptions = (): ColumnChartOptions => ({
    backgroundColor: 'transparent',
    isStacked: true,
    legend: {position: 'bottom'},
    colors: RECOVERY_CHART_COLORS,
    bar: {groupWidth: '50%'},
    chartArea: {left: 60, top: 20, width: '85%', height: '70%'},
    hAxis: {title: 'Date (PDT)', format: 'MMM d'},
    vAxis: {title: 'Number of Tasks'},
  });
  private drawRecoveryStackedBarChart() {
    const columns = [
      {type: 'date', label: 'Date'},
      {type: 'number', label: 'SUCCESS'},
      {type: 'number', label: 'FAIL'},
    ];

    this.drawChart(
      this.recoveryChartElement,
      this.recoveryTaskStats?.dailyStats || [],
      columns,
      (d, date) => [date, d.success, d.fail],
      this.recoveryTaskChartOptions(),
    );
  }

  // Recovery Task Breakdown Table and Charts
  private calculateRecoveryTableData() {
    if (!this.recoveryTaskStats || !this.recoveryTaskStats.aggregatedStats) {
      return;
    }

    const stats = this.recoveryTaskStats.aggregatedStats;

    this.recoveryDataSource = [
      {
        label: 'Total',
        count: stats.totalTasks,
        percent: 100,
        type: 'total' as const,
      },
      ...stats.outcomeBreakdown.map((item) => ({
        label: item.category.toUpperCase(),
        count: item.stats.count,
        percent: item.stats.percent,
        color: this.getRecoveryColor(item.category),
        type: 'detail' as const,
      })),
    ];
  }
  private calculateRecoveryPieChartsData() {
    if (!this.recoveryTaskStats || !this.recoveryTaskStats.aggregatedStats) {
      return;
    }

    const data = this.recoveryTaskStats.aggregatedStats;
    const charts: BreakdownChart[] = [];
    const chartData = new google.visualization.DataTable();
    chartData.addColumn('string', 'Result');
    chartData.addColumn('number', 'Count');

    const colors: string[] = [];
    data.outcomeBreakdown.forEach((item) => {
      if (item.stats.count && item.stats.count > 0) {
        chartData.addRow([item.category.toUpperCase(), item.stats.count]);
        colors.push(this.getRecoveryColor(item.category));
      }
    });

    const options: PieChartOptions = {
      pieHole: 0.4,
      colors,
      backgroundColor: 'transparent',
      legend: {position: 'right', alignment: 'center'},
      chartArea: {left: 10, top: 20, width: '90%', height: '85%'},
      sliceVisibilityThreshold: 0,
    };

    charts.push({
      title: 'Recovery Result',
      data: chartData,
      options,
    });

    this.recoveryBreakdownCharts = charts;
  }

  // Helpers for template (health)
  getHealthColor(state: string): string {
    return HEALTH_COLORS[state.toLowerCase()] || '#BDBDBD';
  }

  // Helpers for template (test)
  getTestColor(state: string): string {
    return TEST_COLORS[state.toLowerCase()] || '#9E9E9E';
  }

  // Helpers for template (recovery)
  getRecoveryColor(state: string): string {
    return RECOVERY_COLORS[state.toLowerCase()] || '#9E9E9E';
  }

  private drawChart<T extends {date: string}>(
    element: ElementRef | undefined,
    dataList: T[],
    columns: Array<{type: string; label: string}>,
    rowMapper: (item: T, date: Date) => Array<Date | number | string>,
    options: ColumnChartOptions,
  ) {
    if (!element?.nativeElement) {
      return;
    }

    const data = new google.visualization.DataTable();
    columns.forEach((col) => {
      data.addColumn(col.type, col.label);
    });

    const dates: Date[] = [];
    dataList.forEach((d) => {
      const date = new Date(`${d.date}T00:00:00`);
      dates.push(date);
      data.addRow(rowMapper(d, date));
    });

    // Fix for duplicate dates on hAxis when data points are few.
    // When there are few data points, Google Charts may generate intermediate ticks (e.g. every 12 hours).
    // Since our format is 'MMM d', these intermediate ticks appear as duplicate dates.
    // We strictly define ticks to match our data points when the dataset is small.
    if (dates.length > 0 && dates.length <= 15) {
      options.hAxis = options.hAxis || {};
      options.hAxis.ticks = dates;
    }

    new google.visualization.ColumnChart(element.nativeElement).draw(
      data,
      options,
    );
  }
}
