import {TestBed} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {SnackBarService} from '../../../services/snackbar_service';
import {RemoteControlDialog} from './remote_control_dialog';

describe('Dialog Component', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        RemoteControlDialog,
        NoopAnimationsModule, // This makes test faster and more stable.
        MatDialogModule,
        MatTestDialogOpenerModule,
      ],
      providers: [
        provideRouter([]),
        {
          provide: SnackBarService,
          useValue: jasmine.createSpyObj('SnackBarService', ['showError']),
        },
      ],
    }).compileComponents();
  });

  it('should be created', () => {
    const comp = TestBed.createComponent(
      MatTestDialogOpener.withComponent(RemoteControlDialog, {
        data: {
          devices: [],
          eligibilityResults: [],
          sessionOptions: {
            maxDurationHours: 12,
            commonRunAsCandidates: [],
            commonProxyTypes: [],
          },
        },
      }),
    );
    expect(comp).toBeTruthy();
  });
});
