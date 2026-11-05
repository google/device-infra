import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {
  HealthinessStats,
  RecoveryTaskStats,
  TestResultStats,
} from '../../../../core/models/device_stats';
import {DEVICE_SERVICE} from '../../../../core/services/device/device_service';
import {HealthStatisticTab} from './health_statistic_tab';
import {
  getHealthDetailChartData,
  getHealthSummaryChartData,
  getRecoveryTaskChartData,
  getTestResultChartData,
  transformHealthStatsToCharts,
  transformHealthStatsToTable,
  transformTestStatsToCharts,
  transformTestStatsToTable,
} from './health_statistic_transformer';

describe('HealthStatisticTab Component', () => {
  let fixture: ComponentFixture<HealthStatisticTab>;
  let component: HealthStatisticTab;
  beforeEach(async () => {
    const mockGoogle = {
      visualization: {
        'DataTable': class {
          addColumn() {}
          addRow() {}
          addRows() {}
        },
        'ColumnChart': class {
          draw() {}
        },
        'PieChart': class {
          draw() {}
        },
      },
      charts: {
        load: jasmine.createSpy('load'),
        setOnLoadCallback: jasmine
          .createSpy('setOnLoadCallback')
          .and.callFake((cb: Function) => {
            cb();
          }),
      },
    };
    (window as unknown as {google: typeof mockGoogle}).google = mockGoogle;

    const deviceServiceSpy = jasmine.createSpyObj('DeviceService', [
      'getDeviceHealthinessStats',
      'getDeviceTestResultStats',
      'getDeviceRecoveryTaskStats',
    ]);
    deviceServiceSpy.getDeviceHealthinessStats.and.returnValue(of(null));
    deviceServiceSpy.getDeviceTestResultStats.and.returnValue(of(null));
    deviceServiceSpy.getDeviceRecoveryTaskStats.and.returnValue(of(null));

    await TestBed.configureTestingModule({
      imports: [
        HealthStatisticTab,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [
        provideRouter([]),
        {provide: DEVICE_SERVICE, useValue: deviceServiceSpy},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HealthStatisticTab);
    component = fixture.componentInstance;
    component.deviceId = 'test_id';
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('handles omitted dailyStats, healthinessSummary, and category in health transformers', () => {
    const emptyStats = {} as unknown as HealthinessStats;
    expect(getHealthSummaryChartData(emptyStats).rows).toEqual([]);
    expect(getHealthDetailChartData(emptyStats).rows).toEqual([]);

    const dayWithoutSummary = {
      dailyStats: [{date: '2026-04-01'}],
    } as unknown as HealthinessStats;
    const summaryData = getHealthSummaryChartData(dayWithoutSummary);
    expect(summaryData.rows.length).toBe(1);
    expect(summaryData.rows[0].slice(1)).toEqual([0, 0]);

    const detailData = getHealthDetailChartData({
      dailyStats: [
        {date: '2026-04-01'},
        {
          date: '2026-04-02',
          healthinessSummary: {
            inServiceBreakdown: [
              {category: '', percent: 10},
              {category: 'IDLE', percent: 90},
            ],
          },
        },
      ],
    } as unknown as HealthinessStats);
    expect(detailData.rows.length).toBe(2);
    expect(detailData.hasData).toBeTrue();

    const aggregatedHealth = {
      inServiceBreakdown: [
        {category: '', percent: 10},
        {category: 'IDLE', percent: 70},
      ],
      outOfServiceBreakdown: [{percent: 5}, {category: 'MISSING', percent: 15}],
    } as unknown as HealthinessStats['aggregatedStats'];
    const healthTable = transformHealthStatsToTable(aggregatedHealth);
    expect(healthTable.map((i) => i.label)).toEqual([
      'In Service',
      'IDLE',
      'Out of Service',
      'MISSING',
    ]);
    expect(healthTable[0].value).toBe(0);
    expect(healthTable[2].value).toBe(0);
    expect(transformHealthStatsToCharts(aggregatedHealth).length).toBe(2);
  });

  it('handles omitted totalCount, stats, percent, and category in test summary transformers', () => {
    const testSummary = {
      completionGroup: {
        displayName: 'Completed',
        totalStats: {count: 5},
        breakdownItems: [{}, {stats: {count: 5}}],
      },
    } as unknown as TestResultStats['summary'];
    const tableItems = transformTestStatsToTable(testSummary);
    expect(tableItems[0].count).toBe(0);
    expect(tableItems[1].percent).toBe(0);
    expect(tableItems[2].percent).toBe(0);
    expect(tableItems.map((i) => i.label)).toContain('UNSPECIFIED');
    expect(transformTestStatsToCharts(testSummary).length).toBe(2);
  });

  it('defaults omitted category to UNSPECIFIED in test and recovery daily chart transformers', () => {
    const testChartData = getTestResultChartData({
      dailyStats: [
        {
          date: '2026-04-01',
          categoryStats: [{stats: {count: 4, percent: 100}}],
        },
      ],
    } as unknown as TestResultStats);
    expect(testChartData.hasData).toBeTrue();
    expect(testChartData.rows[0][1]).toEqual({v: 4, f: '4 (100.0%)'});

    const recoveryChartData = getRecoveryTaskChartData({
      dailyStats: [
        {
          date: '2026-04-01',
          categoryStats: [{stats: {count: 3, percent: 100}}],
        },
      ],
    } as unknown as RecoveryTaskStats);
    expect(recoveryChartData.hasData).toBeTrue();
    expect(recoveryChartData.rows[0][1]).toEqual({v: 3, f: '3 (100.0%)'});
  });
});
