import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {MatTestDialogOpener, MatTestDialogOpenerModule} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';

import {HostWizard} from './host_wizard';

describe('HostWizard Component', () => {
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            HostWizard,
            NoopAnimationsModule,  // This makes test faster and more stable.
            MatDialogModule,
            MatTestDialogOpenerModule,
          ],
          providers: [
            provideRouter([]),
            {
              provide: CONFIG_SERVICE,
              useValue: {
                updateHostConfig: () => of({success: true}),
                checkHostWritePermission: () => of({hasPermission: true}),
                checkDeviceWritePermission: () => of({hasPermission: true}),
              },
            },
          ],
        })
        .compileComponents();
  });

  it('should be created', () => {
    const dialogOpener =
        TestBed.createComponent(
            MatTestDialogOpener.withComponent(HostWizard, {
              data: {hostName: 'test-host', source: 'new'},
            }),
            ) as ComponentFixture<MatTestDialogOpener<HostWizard>>;
    dialogOpener.detectChanges();
    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    expect(comp).toBeTruthy();
  });
});
