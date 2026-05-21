import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of} from 'rxjs';

import {
  PopularFlag,
  UpdatePassThroughFlagsResponse,
} from '../../../../../core/models/host_action';
import {
  HOST_SERVICE,
  HostService,
} from '../../../../../core/services/host/host_service';
import {SnackBarService} from '../../../../../shared/services/snackbar_service';
import {FlagsDialog, FlagsDialogData} from './flags_dialog';

describe('FlagsDialog', () => {
  let component: FlagsDialog;
  let fixture: ComponentFixture<MatTestDialogOpener<FlagsDialog>>;
  let hostService: jasmine.SpyObj<HostService>;
  let snackBarService: jasmine.SpyObj<SnackBarService>;

  const dialogData: FlagsDialogData = {
    hostName: 'test-host',
    currentFlags: '--flag1 --flag2',
  };

  beforeEach(async () => {
    hostService = jasmine.createSpyObj('HOST_SERVICE', [
      'getPopularFlags',
      'updatePassThroughFlags',
    ]);
    snackBarService = jasmine.createSpyObj('SnackBarService', [
      'showSuccess',
      'showError',
    ]);

    hostService.getPopularFlags.and.returnValue(of({flags: []}));

    await TestBed.configureTestingModule({
      imports: [FlagsDialog, NoopAnimationsModule, MatTestDialogOpenerModule],
      providers: [
        {provide: MAT_DIALOG_DATA, useValue: dialogData},
        {provide: HOST_SERVICE, useValue: hostService},
        {provide: SnackBarService, useValue: snackBarService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(FlagsDialog, {data: dialogData}),
    );
    fixture.detectChanges();
    component = fixture.componentInstance.dialogRef.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should append preset flags', () => {
    component.isListMode.set(true);
    const preset: PopularFlag = {
      name: 'Preset1',
      cmd: '--flag3',
      description: 'Desc',
    };
    component.appendPreset(preset);
    expect(component.currentFlagsArray()).toContain('--flag3');
  });

  it('should not append duplicate flags in text mode', () => {
    component.isListMode.set(false);
    component.rawTextFlags.set('--flag1 --flag2');
    const preset: PopularFlag = {
      name: 'Preset1',
      cmd: '--flag1',
      description: 'Desc',
    };
    component.appendPreset(preset);
    expect(component.rawTextFlags()).toEqual('--flag1 --flag2');
  });

  it('should append preset flags in text mode when text is non-empty', () => {
    component.isListMode.set(false);
    component.rawTextFlags.set('--flag1');
    const preset: PopularFlag = {
      name: 'Preset1',
      cmd: '--flag2',
      description: 'Desc',
    };
    component.appendPreset(preset);
    expect(component.rawTextFlags()).toEqual('--flag1 --flag2');
  });

  it('should append preset flags in text mode when text is empty', () => {
    component.isListMode.set(false);
    component.rawTextFlags.set('');
    const preset: PopularFlag = {
      name: 'Preset1',
      cmd: '--flag2',
      description: 'Desc',
    };
    component.appendPreset(preset);
    expect(component.rawTextFlags()).toEqual('--flag2');
  });

  it('should handle save failure response', () => {
    const response: UpdatePassThroughFlagsResponse = {
      success: false,
      error: {message: 'Error message', code: 'WARPGATE_ERROR'},
    };
    hostService.updatePassThroughFlags.and.returnValue(of(response));

    component.save();

    expect(component.isSaving()).toBeFalse();
    expect(snackBarService.showError).toHaveBeenCalledWith('Error message');
  });
});
