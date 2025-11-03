import {TestBed} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {MatTestDialogOpener, MatTestDialogOpenerModule} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';

import {DeviceEmpty} from './device_empty';

describe('DeviceEmpty Component', () => {
  const mockConfigService = jasmine.createSpyObj('CONFIG_SERVICE', [
    'getHostDefaultDeviceConfig',
    'getDeviceConfig',
    'checkDeviceWritePermission',
  ]);

  beforeEach(async () => {
    mockConfigService.getHostDefaultDeviceConfig.and.returnValue(of({}));
    mockConfigService.checkDeviceWritePermission.and.returnValue(
        of({hasPermission: true, userName: 'test-user'}),
    );

    await TestBed
        .configureTestingModule({
          imports: [
            DeviceEmpty,
            NoopAnimationsModule,  // This makes test faster and more stable.
            MatDialogModule,
            MatTestDialogOpenerModule,
          ],
          providers: [
            provideRouter([]),
            {provide: CONFIG_SERVICE, useValue: mockConfigService},
          ],
        })
        .compileComponents();
  });

  it('should be created', () => {
    const dialogOpener = TestBed.createComponent(
        MatTestDialogOpener.withComponent(DeviceEmpty, {
          data: {
            deviceId: 'test-id',
            hostName: 'test-host',
            title: 'test-title',
          },
        }),
    );
    dialogOpener.detectChanges();
    expect(dialogOpener.componentInstance.dialogRef).toBeTruthy();
    expect(
        dialogOpener.componentInstance.dialogRef.componentInstance,
        )
        .toBeTruthy();
  });
});
