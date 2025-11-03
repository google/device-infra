import {TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {MatTestDialogOpener, MatTestDialogOpenerModule} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';

import {HostConfig} from './host_config';

describe('HostConfig Component', () => {
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            HostConfig,
            NoopAnimationsModule,  // This makes test faster and more stable.
            MatTestDialogOpenerModule,
          ],
          providers: [
            provideRouter([]),
            {provide: MAT_DIALOG_DATA, useValue: {hostName: 'test-host'}},
            {
              provide: CONFIG_SERVICE,
              useValue: {
                getHostConfig: () => of({hostConfig: null}),
                checkHostWritePermission: () => of({hasPermission: true}),
                checkDeviceWritePermission: () => of({hasPermission: true}),
              },
            },
          ],
        })
        .compileComponents();
  });

  it('should be created', () => {
    const dialogOpener = TestBed.createComponent(
        MatTestDialogOpener.withComponent(HostConfig, {
          data: {hostName: 'test-host'},
        }),
    );
    const comp =
        (dialogOpener.componentInstance as MatTestDialogOpener<HostConfig>)
            .dialogRef.componentInstance;
    dialogOpener.detectChanges();
    expect(comp).toBeTruthy();
    // Example assertion, adjust as needed based on actual component content
    // expect(fixture.nativeElement.querySelector('app-host-empty')).toBeTruthy();
  });
});
