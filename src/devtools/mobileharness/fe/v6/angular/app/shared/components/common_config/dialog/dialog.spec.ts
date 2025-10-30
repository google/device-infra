import {importProvidersFrom} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {MatTestDialogOpener} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';
import {FakeConfigService} from '../../../../core/services/config/fake_config_service';

import {Dialog} from './dialog';

describe('Dialog Component', () => {
  beforeEach(async () => {
    const fakeConfigService = new FakeConfigService();
    spyOn(fakeConfigService, 'checkDeviceWritePermission').and.returnValue(
      of({hasPermission: true, userName: 'test-user'}),
    );
    await TestBed
        .configureTestingModule({
          imports: [
            Dialog,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [
            provideRouter([]),
            {provide: CONFIG_SERVICE, useValue: fakeConfigService},
            importProvidersFrom(MatDialogModule),
          ],
        })
        .compileComponents();
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(
        MatTestDialogOpener.withComponent(Dialog, {
          data: {
            title: 'Test Title',
            content: 'Test Content',
            deviceId: 'test-device',  // Added deviceId for Footer
          },
        }),
    );
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });
});
