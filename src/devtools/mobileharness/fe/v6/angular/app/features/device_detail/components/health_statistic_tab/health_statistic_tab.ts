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
    return transformHealthStatsToTable(
      this.healthinessStats()?.aggregatedStats,
    );
  });

  private readonly healthBreakdownChartsData = computed(() => {
    if (!this.chartsLibraryLoaded()) {
      return [];
    }
    return transformHealthStatsToCharts(
      this.healthinessStats()?.aggregatedStats,
    );
  });

  healthBreakdownCharts = computed(() => {
    return this.healthBreakdownChartsData();
  });

  // New Computed Signals for Health Charts
  readonly healthSummaryChartData = computed(() => {
    return getHealthSummaryChartData(this.healthinessStats());
  });

  readonly healthSummaryHasData = computed(
    () => this.healthSummaryChartData().hasData,
  );

  readonly healthDetailChartData = computed(() => {
    return getHealthDetailChartData(this.healthinessStats());
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
    return transformTestStatsToTable(this.testResultStats()?.summary);
  });

  private readonly testBreakdownChartsData = computed(() => {
    if (!this.chartsLibraryLoaded()) {
      return [];
    }
    return transformTestStatsToCharts(this.testResultStats()?.summary);
  });

  testBreakdownCharts = computed(() => {
    return this.testBreakdownChartsData();
  });

  readonly testChartData = computed(() => {
    return getTestResultChartData(this.testResultStats());
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
    return transformRecoveryStatsToTable(this.recoveryTaskStats()?.summary);
  });

  private readonly recoveryBreakdownChartsData = computed(() => {
    if (!this.chartsLibraryLoaded()) {
      return [];
    }
    return transformRecoveryStatsToCharts(this.recoveryTaskStats()?.summary);
  });

  recoveryBreakdownCharts = computed(() => {
    return this.recoveryBreakdownChartsData();
  });

  readonly recoveryChartData = computed(() => {
    return getRecoveryTaskChartData(this.recoveryTaskStats());
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
