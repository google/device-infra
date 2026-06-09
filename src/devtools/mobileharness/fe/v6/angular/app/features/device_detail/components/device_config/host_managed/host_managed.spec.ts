import {importProvidersFrom} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA, MatDialogModule} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {of} from 'rxjs';
import {DeviceConfig} from '../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {FakeConfigService} from '../../../../../core/services/config/fake_config_service';
import {DEVICE_SERVICE} from '../../../../../core/services/device/device_service';
import {FakeDeviceService} from '../../../../../core/services/device/fake_device_service';

import {HostManaged} from './host_managed';

describe('HostManaged Component', () => {
  let fixture: ComponentFixture<HostManaged>;
  let component: HostManaged;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        HostManaged,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [
        provideRouter([]),
        {provide: DEVICE_SERVICE, useClass: FakeDeviceService},
        {provide: CONFIG_SERVICE, useClass: FakeConfigService},
        {provide: MAT_DIALOG_DATA, useValue: {deviceId: 'test-id'}},
        importProvidersFrom(MatDialogModule),
      ],
    }).compileComponents();
  });

  it('should be created', () => {
    fixture = TestBed.createComponent(HostManaged);
    component = fixture.componentInstance;
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should fetch default device config and set hostDefaultConfig', () => {
    const configService = TestBed.inject(CONFIG_SERVICE);
    const mockConfig: DeviceConfig = {
      wifi: {type: 'custom', ssid: 'test-wifi', psk: '', scanSsid: false},
    };
    spyOn(configService, 'getHostDefaultDeviceConfig').and.returnValue(
      of(mockConfig),
    );

    fixture = TestBed.createComponent(HostManaged);
    component = fixture.componentInstance;
    component.hostName = 'test-host';
    fixture.detectChanges();

    expect(configService.getHostDefaultDeviceConfig).toHaveBeenCalledWith(
      'test-host',
    );
    expect(component.hostDefaultConfig()).toEqual(mockConfig);
  });
});
