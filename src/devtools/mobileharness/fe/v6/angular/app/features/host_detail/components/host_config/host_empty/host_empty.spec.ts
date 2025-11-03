import {TestBed} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {MatTestDialogOpener, MatTestDialogOpenerModule} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';

import {HostEmpty} from './host_empty';

describe('HostEmpty Component', () => {
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            HostEmpty,
            NoopAnimationsModule,  // This makes test faster and more stable.
            MatDialogModule,
            MatTestDialogOpenerModule,
          ],
          providers: [
            provideRouter([]),
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

  it('should be created and show empty config message', () => {
    const dialogOpener = TestBed.createComponent(
        MatTestDialogOpener.withComponent(HostEmpty, {
          data: {
            hostName: 'test-host',
            title: 'This host has an empty configuration'
          },
        }),
    );
    dialogOpener.detectChanges();
    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    expect(comp).toBeTruthy();
  });
});
