import {TestBed} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {MatTestDialogOpener, MatTestDialogOpenerModule} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import type {DeviceConfig} from '../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {FakeConfigService} from '../../../../../core/services/config/fake_config_service';
import {DEVICE_SERVICE} from '../../../../../core/services/device/device_service';
import {FakeDeviceService} from '../../../../../core/services/device/fake_device_service';
import {SCENARIO_IN_SERVICE_IDLE} from '../../../../../core/services/mock_data/devices/01_in_service_idle';

import {DeviceSettings} from './device_settings';

describe('Device Settings Component', () => {
  beforeEach(async () => {
    const fakeConfigService = new FakeConfigService();
    spyOn(fakeConfigService, 'checkDeviceWritePermission')
        .and.returnValue(
            of({hasPermission: true, userName: 'test-user'}),
        );

    await TestBed
        .configureTestingModule({
          imports: [
            DeviceSettings,
            NoopAnimationsModule,  // This makes test faster and more stable.
            MatDialogModule,
            MatTestDialogOpenerModule,
          ],
          providers: [
            provideRouter([]),
            {provide: DEVICE_SERVICE, useClass: FakeDeviceService},
            {provide: CONFIG_SERVICE, useValue: fakeConfigService},
          ],
        })
        .compileComponents();
  });

  it('should be created', () => {
    const dialogOpener = TestBed.createComponent(
        MatTestDialogOpener.withComponent(DeviceSettings, {
          data: {deviceId: 'test-device'},
        }),
    );
    dialogOpener.componentInstance.dialogRef.componentInstance.config =
        SCENARIO_IN_SERVICE_IDLE.config as DeviceConfig;
    dialogOpener.detectChanges();
    expect(dialogOpener.componentInstance.dialogRef).toBeTruthy();
    expect(
        dialogOpener.componentInstance.dialogRef.componentInstance,
        )
        .toBeTruthy();
  });
});
