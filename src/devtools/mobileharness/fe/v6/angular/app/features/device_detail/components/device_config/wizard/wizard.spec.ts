import {TestBed} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {MatTestDialogOpener} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {FakeConfigService} from '../../../../../core/services/config/fake_config_service';
import {DEVICE_SERVICE} from '../../../../../core/services/device/device_service';
import {FakeDeviceService} from '../../../../../core/services/device/fake_device_service';

import {Wizard} from './wizard';

describe('Wizard Component', () => {
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            Wizard,
            NoopAnimationsModule,  // This makes test faster and more stable.
            MatDialogModule,
          ],
          providers: [
            provideRouter([]),
            {provide: DEVICE_SERVICE, useClass: FakeDeviceService},
            {provide: CONFIG_SERVICE, useClass: FakeConfigService},
          ],
        })
        .compileComponents();
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(
        MatTestDialogOpener.withComponent(Wizard, {
          data: {deviceId: 'test-id', source: 'new'},
        }),
    );
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });
});
