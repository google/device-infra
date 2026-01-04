import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {DEVICE_SERVICE} from '../../../../core/services/device/device_service';
import {HealthStatisticTab} from './health_statistic_tab';

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
});
