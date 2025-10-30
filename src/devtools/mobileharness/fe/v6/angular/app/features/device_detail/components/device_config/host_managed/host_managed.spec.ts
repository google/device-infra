import {importProvidersFrom} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA, MatDialogModule} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {FakeConfigService} from '../../../../../core/services/config/fake_config_service';
import {DEVICE_SERVICE} from '../../../../../core/services/device/device_service';
import {FakeDeviceService} from '../../../../../core/services/device/fake_device_service';

import {HostManaged} from './host_managed';

describe('HostManaged Component', () => {
  let fixture: ComponentFixture<HostManaged>;
  let component: HostManaged;
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            HostManaged,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [
            provideRouter([]),
            {provide: DEVICE_SERVICE, useClass: FakeDeviceService},
            {provide: CONFIG_SERVICE, useClass: FakeConfigService},
            {provide: MAT_DIALOG_DATA, useValue: {deviceId: 'test-id'}},
            importProvidersFrom(MatDialogModule),
          ],
        })
        .compileComponents();

    fixture = TestBed.createComponent(HostManaged);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
