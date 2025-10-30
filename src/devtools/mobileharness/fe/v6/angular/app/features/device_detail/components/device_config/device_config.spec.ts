import {importProvidersFrom} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA, MatDialogModule} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';
import {FakeConfigService} from '../../../../core/services/config/fake_config_service';
import {DEVICE_SERVICE} from '../../../../core/services/device/device_service';
import {FakeDeviceService} from '../../../../core/services/device/fake_device_service';
import {SCENARIO_IN_SERVICE_IDLE} from '../../../../core/services/mock_data/devices/01_in_service_idle';

import {DeviceConfig} from './device_config';

describe('DeviceConfig Component', () => {
  let fixture: ComponentFixture<DeviceConfig>;
  let component: DeviceConfig;
  let fakeConfigService: FakeConfigService;

  beforeEach(async () => {
    fakeConfigService = new FakeConfigService();
    spyOn(fakeConfigService, 'getDeviceConfig').and.returnValue(
      of({
        deviceConfig: SCENARIO_IN_SERVICE_IDLE.config,
        isHostManaged: false,
        hostName: SCENARIO_IN_SERVICE_IDLE.overview.host.name,
      }),
    );

    await TestBed
        .configureTestingModule({
          imports: [
            DeviceConfig,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [
            provideRouter([]),
            {provide: DEVICE_SERVICE, useClass: FakeDeviceService},
            {provide: CONFIG_SERVICE, useValue: fakeConfigService},
            {
              provide: MAT_DIALOG_DATA,
              useValue: {deviceId: SCENARIO_IN_SERVICE_IDLE.id},
            },
            importProvidersFrom(MatDialogModule),
          ],
        })
        .compileComponents();

    fixture = TestBed.createComponent(DeviceConfig);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });
});
