import {TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';
import {Environment} from '../../../../core/services/environment';

import {DeviceConfig} from './device_config';

describe('DeviceConfig Component', () => {
  let isGoogleInternalSpy: jasmine.Spy;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        DeviceConfig,
        NoopAnimationsModule, // This makes test faster and more stable.
        MatTestDialogOpenerModule,
      ],
      providers: [
        provideRouter([]),
        {provide: MAT_DIALOG_DATA, useValue: {deviceId: 'test-device'}},
        {
          provide: CONFIG_SERVICE,
          useValue: {
            getDeviceConfig: () =>
              of({deviceConfig: null, isHostManaged: false}),
            checkDeviceConfigPermission: () => of({hasPermission: true}),
            getConfigurableDimensions: () =>
              of({dimensions: [], allowCustomDimensions: false}),
            getDimensionValueSuggestions: () => of({candidateValues: []}),
          },
        },
      ],
    }).compileComponents();

    isGoogleInternalSpy = spyOn(
      TestBed.inject(Environment),
      'isGoogleInternal',
    ).and.returnValue(true);
  });

  it('should be created', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceConfig, {
        data: {deviceId: 'test-device'},
      }),
    );
    const comp = (
      dialogOpener.componentInstance as MatTestDialogOpener<DeviceConfig>
    ).dialogRef.componentInstance;
    dialogOpener.detectChanges();
    expect(comp).toBeTruthy();
  });

  it('opens DeviceSettings directly in ATS even when deviceConfig is null', async () => {
    isGoogleInternalSpy.and.returnValue(false);

    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceConfig, {
        data: {deviceId: 'ats-device', hostName: 'ats-host', hostIp: '1.2.3.4'},
      }),
    );
    dialogOpener.detectChanges();
    await dialogOpener.whenStable();
    dialogOpener.detectChanges();

    expect(document.querySelector('app-device-settings')).toBeTruthy();
    expect(document.querySelector('app-device-empty')).toBeNull();
  });
});
