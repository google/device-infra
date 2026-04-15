import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {MatTableModule} from '@angular/material/table';
import {load} from '@google-web-components/google-chart/loader';
import {Subject, of} from 'rxjs';
import {catchError, finalize, switchMap, tap} from 'rxjs/operators';

import {DEVICE_SERVICE} from '../../../../core/services/device/device_service';
import {
  MasterDetailLayout,
  NavItem,
} from '../../../../shared/components/master_detail_layout/master_detail_layout';
import {dateUtils} from '../../../../shared/utils/date_utils';
import {GoogleChartComponent} from './google_chart/google_chart';
import {
  getHealthDetailChartData,
  getHealthSummaryChartData,
  getRecoveryTaskChartData,
  getTestResultChartData,
  transformHealthStatsToCharts,
  transformHealthStatsToTable,
  transformRecoveryStatsToCharts,
  transformRecoveryStatsToTable,
  transformTestStatsToCharts,
  transformTestStatsToTable,
} from './health_statistic_transformer';
import {StatisticBreakdown} from './statistic_breakdown/statistic_breakdown';
import {StatisticCard} from './statistic_card/statistic_card';

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
    GoogleChartComponent,
    MasterDetailLayout,
    MatTableModule,
    StatisticBreakdown,
    StatisticCard,
  ],
})
export class HealthStatisticTab implements OnInit, OnDestroy {
  @Input({required: true}) deviceId!: string;

  private readonly deviceService = inject(DEVICE_SERVICE);

  // Used for breakdown charts that depend on global google lib being loaded for DataTable creation
  private readonly chartsLibraryLoaded = signal(false);

  constructor() {
    load().then(() => {
      google.charts.setOnLoadCallback(() => {
        this.chartsLibraryLoaded.set(true);
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

  loadingHealth = signal(false);
  loadingTest = signal(false);
  loadingRecovery = signal(false);

  healthDateRangeString = signal('');
  testDateRangeString = signal('');
  recoveryDateRangeString = signal('');

  private readonly healthDateRange$ = new Subject<{start: Date; end: Date}>();
  private readonly testDateRange$ = new Subject<{start: Date; end: Date}>();
  private readonly recoveryDateRange$ = new Subject<{start: Date; end: Date}>();

  // Healthiness statistics.
  readonly healthinessStats = toSignal(
    this.healthDateRange$.pipe(
      tap((range) => {
        this.loadingHealth.set(true);
        this.healthDateRangeString.set(
          dateUtils.formatDateRange(range.start, range.end),
        );
      }),
      switchMap((range) =>
        this.deviceService
          .getDeviceHealthinessStats(
            this.deviceId,
            dateUtils.toGoogleDate(range.start),
            dateUtils.toGoogleDate(range.end),
          )
          .pipe(
            catchError(() => of(null)),
            finalize(() => {
              this.loadingHealth.set(false);
            }),
          ),
      ),
    ),
    {initialValue: null},
  );

  healthDataSource = computed(() => {
    try {
      return transformHealthStatsToTable(
        this.healthinessStats()?.aggregatedStats,
      );
    } catch (e) {
      console.error('Error transforming health stats to table:', e);
      return [];
    }
  });

  private readonly healthBreakdownChartsData = computed(() => {
    if (!this.chartsLibraryLoaded()) {
      return [];
    }
    try {
      return transformHealthStatsToCharts(
        this.healthinessStats()?.aggregatedStats,
      );
    } catch (e) {
      console.error('Error transforming health stats to charts:', e);
      return [];
    }
  });

  healthBreakdownCharts = computed(() => {
    return this.healthBreakdownChartsData();
  });

  // New Computed Signals for Health Charts
  readonly healthSummaryChartData = computed(() => {
    try {
      return getHealthSummaryChartData(this.healthinessStats());
    } catch (e) {
      console.error('Error getting health summary chart data:', e);
      return {columns: [], rows: [], options: {}, hasData: false};
    }
  });

  readonly healthSummaryHasData = computed(
    () => this.healthSummaryChartData().hasData,
  );

  readonly healthDetailChartData = computed(() => {
    try {
      return getHealthDetailChartData(this.healthinessStats());
    } catch (e) {
      console.error('Error getting health detail chart data:', e);
      return {columns: [], rows: [], options: {}, hasData: false};
    }
  });

  readonly healthDetailHasData = computed(
    () => this.healthDetailChartData().hasData,
  );

  // Test result statistics.
  readonly testResultStats = toSignal(
    this.testDateRange$.pipe(
      tap((range) => {
        this.loadingTest.set(true);
        this.testDateRangeString.set(
          dateUtils.formatDateRange(range.start, range.end),
        );
      }),
      switchMap((range) =>
        this.deviceService
          .getDeviceTestResultStats(
            this.deviceId,
            dateUtils.toGoogleDate(range.start),
            dateUtils.toGoogleDate(range.end),
          )
          .pipe(
            catchError(() => of(null)),
            finalize(() => {
              this.loadingTest.set(false);
            }),
          ),
      ),
    ),
    {initialValue: null},
  );

  testDataSource = computed(() => {
    try {
      return transformTestStatsToTable(this.testResultStats()?.summary);
    } catch (e) {
      console.error('Error transforming test stats to table:', e);
      return [];
    }
  });

  private readonly testBreakdownChartsData = computed(() => {
    if (!this.chartsLibraryLoaded()) {
      return [];
    }
    try {
      return transformTestStatsToCharts(this.testResultStats()?.summary);
    } catch (e) {
      console.error('Error transforming test stats to charts:', e);
      return [];
    }
  });

  testBreakdownCharts = computed(() => {
    return this.testBreakdownChartsData();
  });

  readonly testChartData = computed(() => {
    try {
      return getTestResultChartData(this.testResultStats());
    } catch (e) {
      console.error('Error getting test result chart data:', e);
      return {columns: [], rows: [], options: {}, hasData: false};
    }
  });

  readonly testHasData = computed(() => this.testChartData().hasData);

  // Recovery task statistics.
  readonly recoveryTaskStats = toSignal(
    this.recoveryDateRange$.pipe(
      tap((range) => {
        this.loadingRecovery.set(true);
        this.recoveryDateRangeString.set(
          dateUtils.formatDateRange(range.start, range.end),
        );
      }),
      switchMap((range) =>
        this.deviceService
          .getDeviceRecoveryTaskStats(
            this.deviceId,
            dateUtils.toGoogleDate(range.start),
            dateUtils.toGoogleDate(range.end),
          )
          .pipe(
            catchError(() => of(null)),
            finalize(() => {
              this.loadingRecovery.set(false);
            }),
          ),
      ),
    ),
    {initialValue: null},
  );

  recoveryDataSource = computed(() => {
    try {
      return transformRecoveryStatsToTable(this.recoveryTaskStats()?.summary);
    } catch (e) {
      console.error('Error transforming recovery stats to table:', e);
      return [];
    }
  });

  private readonly recoveryBreakdownChartsData = computed(() => {
    if (!this.chartsLibraryLoaded()) {
      return [];
    }
    try {
      return transformRecoveryStatsToCharts(this.recoveryTaskStats()?.summary);
    } catch (e) {
      console.error('Error transforming recovery stats to charts:', e);
      return [];
    }
  });

  recoveryBreakdownCharts = computed(() => {
    return this.recoveryBreakdownChartsData();
  });

  readonly recoveryChartData = computed(() => {
    try {
      return getRecoveryTaskChartData(this.recoveryTaskStats());
    } catch (e) {
      console.error('Error getting recovery task chart data:', e);
      return {columns: [], rows: [], options: {}, hasData: false};
    }
  });

  readonly recoveryHasData = computed(() => this.recoveryChartData().hasData);

  ngOnInit() {}

  ngOnDestroy() {}

  onDateRangeChange(
    type: 'health' | 'test' | 'recovery',
    range: {start: Date; end: Date},
  ) {
    switch (type) {
      case 'health':
        this.healthDateRange$.next(range);
        break;
      case 'test':
        this.testDateRange$.next(range);
        break;
      case 'recovery':
        this.recoveryDateRange$.next(range);
        break;
      default:
        break;
    }
  }
}
