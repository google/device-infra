import {TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {MatTestDialogOpener, MatTestDialogOpenerModule} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';

import {DeviceConfig} from './device_config';

describe('DeviceConfig Component', () => {
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            DeviceConfig,
            NoopAnimationsModule,  // This makes test faster and more stable.
            MatTestDialogOpenerModule,
          ],
          providers: [
            provideRouter([]),
            {provide: MAT_DIALOG_DATA, useValue: {deviceId: 'test-device'}},
            {
              provide: CONFIG_SERVICE,
              useValue: {
                getDeviceConfig: () => of({deviceConfig: null}),
                checkDeviceWritePermission: () => of({hasPermission: true}),
              },
            },
          ],
        })
        .compileComponents();
  });

  it('should be created', () => {
    const dialogOpener = TestBed.createComponent(
        MatTestDialogOpener.withComponent(DeviceConfig, {
          data: {deviceId: 'test-device'},
        }),
    );
    const comp =
        (dialogOpener.componentInstance as MatTestDialogOpener<DeviceConfig>)
            .dialogRef.componentInstance;
    dialogOpener.detectChanges();
    expect(comp).toBeTruthy();
  });
});
