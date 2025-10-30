import {importProvidersFrom} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA, MatDialogModule} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {FakeConfigService} from '../../../../../core/services/config/fake_config_service';
import {DEVICE_SERVICE} from '../../../../../core/services/device/device_service';
import {FakeDeviceService} from '../../../../../core/services/device/fake_device_service';
import {SCENARIO_IN_SERVICE_IDLE} from '../../../../../core/services/mock_data/devices/01_in_service_idle';

import {Settings} from './settings';

describe('Settings Component', () => {
  let fixture: ComponentFixture<Settings>;
  let component: Settings;
  beforeEach(async () => {
    const fakeConfigService = new FakeConfigService();
    spyOn(fakeConfigService, 'checkDeviceWritePermission').and.returnValue(
      of({hasPermission: true, userName: 'test-user'}),
    );

    await TestBed
        .configureTestingModule({
          imports: [
            Settings,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [
            provideRouter([]),
            {provide: DEVICE_SERVICE, useClass: FakeDeviceService},
            {provide: CONFIG_SERVICE, useValue: fakeConfigService},
            {provide: MAT_DIALOG_DATA, useValue: {deviceId: 'test-device'}},
            importProvidersFrom(MatDialogModule),
          ],
        })
        .compileComponents();

    fixture = TestBed.createComponent(Settings);
    component = fixture.componentInstance;
    component.config = SCENARIO_IN_SERVICE_IDLE.config!;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
