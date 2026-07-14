import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatTestDialogOpener} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of} from 'rxjs';

import {DeployableVersion} from '@deviceinfra/app/core/models/host_action';
import {FakeHostService} from '@deviceinfra/app/core/services/host/fake_host_service';
import {HOST_SERVICE} from '@deviceinfra/app/core/services/host/host_service';
import {FlagsDialog} from '@deviceinfra/app/features/host_detail/components/host_overview/flags_dialog/flags_dialog';
import {TrackingDialog} from '@deviceinfra/app/features/host_detail/components/host_overview/tracking_dialog/tracking_dialog';
import {SnackBarService} from '@deviceinfra/app/shared/services/snackbar_service';
import {ReleaseDialog, ReleaseDialogData} from './release_dialog';

// release dialog is unavailable in OSS
