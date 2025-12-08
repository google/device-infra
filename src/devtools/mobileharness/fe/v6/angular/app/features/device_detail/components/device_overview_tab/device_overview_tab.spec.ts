import {ChangeDetectionStrategy, Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {firstValueFrom, timer} from 'rxjs';

import {DeviceOverview} from '../../../../core/models/device_overview';

import {DeviceOverviewTab} from './device_overview_tab';

@Component({
  standalone: true,
  imports: [DeviceOverviewTab],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-device-overview-tab [device]="device"></app-device-overview-tab>`,
})
class TestHostComponent {
  device!: DeviceOverview;
}

describe('DeviceOverviewTab Component', () => {
  const mockDevice: DeviceOverview = {
    id: '1234567890',
    basicInfo: {
      batteryLevel: 95,
      build: 'AP1A.240405.002',
      form: 'physical',
      hardware: 'g/2345a',
      model: 'Pixel 9',
      network: {wifiRssi: -58, hasInternet: true},
      os: 'Android',
      version: '15',
    },
    capabilities: {
      supportedDrivers: [
        'AndroidInstrumentation',
        'AndroidGUnit',
        'AndroidMonkey',
        'AndroidRoboTest',
        'AndroidTradefedTest',
        'FlutterDriver',
        'MoblyAospTest',
        'MoblyTest',
      ],
      supportedDecorators: [
        'AndroidBugreportDecorator',
        'AndroidCrashMonitorDecorator',
        'AndroidFilePullerDecorator',
        'AndroidLogCatDecorator',
        'AndroidScreenshotDecorator',
      ],
    },
    healthAndActivity: {
      state: 'IN_SERVICE_IDLE',
      title: 'In Service (Idle)',
      subtitle: 'The device is healthy and ready for new tasks.',
      deviceStatus: {
        status: 'IDLE',
        isCritical: false,
      },
      deviceTypes: [
        {
          type: 'AndroidRealDevice',
          isAbnormal: false,
        },
      ],
      lastInServiceTime: '2025-09-09T03:33:47.715Z',
    },
    dimensions: {
      supported: {
        'From Device Config': {
          dimensions: [
            {name: 'pool', value: 'pixel-prod'},
            {name: 'os', value: '14'},
          ],
        },
      },
      required: {},
    },
    permissions: {
      executors: ['test-runner-service-account', 'auto-recovery-service'],
      owners: ['user-a', 'group-infra-team', 'derekchen'],
    },
    host: {
      name: 'host-a-1.prod.example.com',
      ip: '192.168.1.101',
    },
    properties: {
      'test-type': 'instrumentation',
      'max-run-time': '3600',
      'network-requirement': 'full',
      'encryption-state': 'encrypted',
    },
  };

  let fixture: ComponentFixture<TestHostComponent>;
  let component: DeviceOverviewTab;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        DeviceOverviewTab,
        TestHostComponent,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(TestHostComponent);
    fixture.componentInstance.device = mockDevice;
    fixture.detectChanges();
    component = fixture.debugElement.query(
      By.directive(DeviceOverviewTab),
    ).componentInstance;
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('should search dimensions', async () => {
    component.dimensionsSearchTerm = 'pixel';
    component.onDimensionsSearchChange();
    // Wait for the debounceTime (100ms) plus a small buffer.
    await firstValueFrom(timer(150));
    fixture.detectChanges();
    expect(component.filteredDimensions()).toEqual([
      {
        section: 'supported',
        source: 'From Device Config',
        key: 'pool',
        value: 'pixel-prod',
        keyLower: 'pool',
        valueLower: 'pixel-prod',
      },
    ]);
  });

  it('should search capabilities', async () => {
    // Search drivers
    component.driversSearchTerm = 'mobly';
    component.onDriversSearchChange();
    // Wait for the debounceTime (100ms) plus a small buffer.
    await firstValueFrom(timer(150));
    fixture.detectChanges();

    // Check drivers filtered
    expect(component.filteredDrivers()).toEqual(['MoblyAospTest', 'MoblyTest']);
    // Check decorators not affected (shows all)
    expect(component.filteredDecorators().length).toBe(5);

    // Search decorators
    component.decoratorsSearchTerm = 'bugreport';
    component.onDecoratorsSearchChange();
    await firstValueFrom(timer(150));
    fixture.detectChanges();

    // Check decorators filtered
    expect(component.filteredDecorators()).toEqual([
      'AndroidBugreportDecorator',
    ]);
    // Drivers should remain filtered by previous search
    expect(component.filteredDrivers()).toEqual(['MoblyAospTest', 'MoblyTest']);
  });

  it('should return IN_SERVICE_BUSY when device is busy', async () => {
    component.device.healthAndActivity.state = 'IN_SERVICE_BUSY';
    component.device.healthAndActivity.title = 'In Service (Busy)';
    component.device.healthAndActivity.subtitle =
      'The device is busy running tasks.';
    component.device.healthAndActivity.deviceStatus = {
      status: 'BUSY',
      isCritical: false,
    };
    component.device.healthAndActivity.deviceTypes = [
      {
        type: 'AndroidRealDevice',
        isAbnormal: false,
      },
    ];
    component.device.healthAndActivity.lastInServiceTime =
      '2025-09-09T03:33:47.715Z';
    expect(
      component.getHealthStateUI(component.device.healthAndActivity.state),
    ).toEqual({
      icon: 'sync',
      iconColorClass: 'text-blue-600',
      iconBgColorClass: 'bg-blue-100',
      borderColorClass: 'border-l-blue-500',
      isSpinning: true,
    });
  });

  it('should return OUT_OF_SERVICE_RECOVERING when device is recovering', async () => {
    component.device.healthAndActivity.state = 'OUT_OF_SERVICE_RECOVERING';
    component.device.healthAndActivity.title = 'Out of Service (Recovering)';
    component.device.healthAndActivity.subtitle =
      'The device is recovering from an error.';
    component.device.healthAndActivity.deviceStatus = {
      status: 'RECOVERING',
      isCritical: false,
    };
    component.device.healthAndActivity.deviceTypes = [
      {
        type: 'AndroidRealDevice',
        isAbnormal: false,
      },
    ];
    component.device.healthAndActivity.lastInServiceTime =
      '2025-09-09T03:33:47.715Z';
    expect(
      component.getHealthStateUI(component.device.healthAndActivity.state),
    ).toEqual({
      icon: 'autorenew',
      iconColorClass: 'text-amber-600',
      iconBgColorClass: 'bg-amber-100',
      borderColorClass: 'border-l-amber-500',
      isSpinning: true,
    });
  });

  it('should return OUT_OF_SERVICE_TEMP_MAINT when device is in temporary maintenance', async () => {
    component.device.healthAndActivity.state = 'OUT_OF_SERVICE_TEMP_MAINT';
    component.device.healthAndActivity.title =
      'Out of Service (Temporary Maintenance)';
    component.device.healthAndActivity.subtitle =
      'The device is in temporary maintenance.';
    component.device.healthAndActivity.deviceStatus = {
      status: 'MAINTENANCE',
      isCritical: false,
    };
    component.device.healthAndActivity.deviceTypes = [
      {
        type: 'AndroidRealDevice',
        isAbnormal: false,
      },
    ];
    component.device.healthAndActivity.lastInServiceTime =
      '2025-09-09T03:33:47.715Z';
    expect(
      component.getHealthStateUI(component.device.healthAndActivity.state),
    ).toEqual({
      icon: 'warning',
      iconColorClass: 'text-amber-600',
      iconBgColorClass: 'bg-amber-100',
      borderColorClass: 'border-l-amber-500',
      isSpinning: false,
    });
  });

  it('should return OUT_OF_SERVICE_NEEDS_FIXING when device needs fixing', async () => {
    component.device.healthAndActivity.state = 'OUT_OF_SERVICE_NEEDS_FIXING';
    component.device.healthAndActivity.title = 'Out of Service (Needs Fixing)';
    component.device.healthAndActivity.subtitle =
      'The device needs fixing or troubleshooting.';
    component.device.healthAndActivity.deviceStatus = {
      status: 'NEEDS_REPAIR',
      isCritical: true,
    };
    component.device.healthAndActivity.deviceTypes = [
      {
        type: 'AndroidRealDevice',
        isAbnormal: true,
      },
    ];
    component.device.healthAndActivity.lastInServiceTime =
      '2025-09-09T03:33:47.715Z';
    expect(
      component.getHealthStateUI(component.device.healthAndActivity.state),
    ).toEqual({
      icon: 'error',
      iconColorClass: 'text-red-600',
      iconBgColorClass: 'bg-red-100',
      borderColorClass: 'border-l-red-500',
      isSpinning: false,
    });
  });

  it('should return network_wifi_3_bar signal icon and Good quality text when wifi rssi is -75', async () => {
    expect(component.getWifiSignalIcon(-75)).toEqual('network_wifi_3_bar');
    expect(component.getWifiQualityText(-75)).toEqual('Good');
  });

  it('should return network_wifi_2_bar signal icon and Okay quality text when wifi rssi is -85', async () => {
    expect(component.getWifiSignalIcon(-85)).toEqual('network_wifi_2_bar');
    expect(component.getWifiQualityText(-85)).toEqual('Okay');
  });

  it('should return network_wifi_1_bar signal icon and Weak quality text when wifi rssi is -105', async () => {
    expect(component.getWifiSignalIcon(-105)).toEqual('network_wifi_1_bar');
    expect(component.getWifiQualityText(-105)).toEqual('Weak');
  });
});
