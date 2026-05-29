import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {MatTestDialogOpener} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of, Subject} from 'rxjs';

import {
  PopularFlag,
  UpdatePassThroughFlagsResponse,
} from '../../../../../core/models/host_action';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {
  HOST_SERVICE,
  HostService,
} from '../../../../../core/services/host/host_service';
import {SnackBarService} from '../../../../../shared/services/snackbar_service';
import {FlagsDialog, FlagsDialogData} from './flags_dialog';

describe('FlagsDialog', () => {
  let component: FlagsDialog;
  let fixture: ComponentFixture<MatTestDialogOpener<FlagsDialog>>;
  let opener: MatTestDialogOpener<FlagsDialog>;
  let hostService: jasmine.SpyObj<HostService>;
  let snackBarService: jasmine.SpyObj<SnackBarService>;
  let dialog: jasmine.SpyObj<MatDialog>;

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
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    const configService = jasmine.createSpyObj('CONFIG_SERVICE', [
      'checkDeviceWritePermission',
      'checkHostWritePermission',
    ]);
    configService.checkDeviceWritePermission.and.returnValue(
      of({hasPermission: true}),
    );
    configService.checkHostWritePermission.and.returnValue(
      of({hasPermission: true}),
    );

    hostService.getPopularFlags.and.returnValue(of({flags: []}));

    await TestBed.configureTestingModule({
      imports: [NoopAnimationsModule],
      providers: [
        {provide: HOST_SERVICE, useValue: hostService},
        {provide: SnackBarService, useValue: snackBarService},
        {provide: CONFIG_SERVICE, useValue: configService},
      ],
    })
      .overrideComponent(FlagsDialog, {
        set: {
          providers: [{provide: MatDialog, useValue: dialog}],
        },
      })
      .compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(FlagsDialog, {data: dialogData}),
    );
    opener = fixture.componentInstance;
    component = opener.dialogRef.componentInstance;
    fixture.detectChanges();
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

    const mockConfirmDialogRef = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    mockConfirmDialogRef.afterClosed.and.returnValue(of('primary'));
    dialog.open.and.returnValue(mockConfirmDialogRef);

    component.save();

    expect(dialog.open).toHaveBeenCalled();
    expect(hostService.updatePassThroughFlags).toHaveBeenCalledWith(
      'test-host',
      '--flag1 --flag2',
    );
    expect(component.isSaving()).toBeFalse();
    expect(snackBarService.showError).toHaveBeenCalledWith('Error message');
  });

  it('should handle save success response', () => {
    const response: UpdatePassThroughFlagsResponse = {
      success: true,
    };
    hostService.updatePassThroughFlags.and.returnValue(of(response));

    const mockConfirmDialogRef = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    mockConfirmDialogRef.afterClosed.and.returnValue(of('primary'));
    dialog.open.and.returnValue(mockConfirmDialogRef);

    spyOn(opener.dialogRef, 'close');

    component.save();

    expect(dialog.open).toHaveBeenCalled();
    expect(hostService.updatePassThroughFlags).toHaveBeenCalledWith(
      'test-host',
      '--flag1 --flag2',
    );
    expect(opener.dialogRef.close).toHaveBeenCalledWith('--flag1 --flag2');
  });

  it('should not save flags if confirmation is cancelled', () => {
    const mockConfirmDialogRef = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    mockConfirmDialogRef.afterClosed.and.returnValue(of('cancel'));
    dialog.open.and.returnValue(mockConfirmDialogRef);

    component.save();

    expect(dialog.open).toHaveBeenCalled();
    expect(hostService.updatePassThroughFlags).not.toHaveBeenCalled();
    expect(component.isSaving()).toBeFalse();
  });

  it('should set isSaving to true while saving', () => {
    const subject = new Subject<UpdatePassThroughFlagsResponse>();
    hostService.updatePassThroughFlags.and.returnValue(subject);

    const mockConfirmDialogRef = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    mockConfirmDialogRef.afterClosed.and.returnValue(of('primary'));
    dialog.open.and.returnValue(mockConfirmDialogRef);

    component.save();

    expect(component.isSaving()).toBeTrue();

    subject.next({success: true});
    subject.complete();

    expect(component.isSaving()).toBeFalse();
  });

  it('should update hasPermission when permission changes', () => {
    component.handlePermissionChange({hasPermission: false});
    expect(component.hasPermission()).toBeFalse();
  });

  it('should not add flag when hasPermission is false', () => {
    component.handlePermissionChange({hasPermission: false});
    component.addInput.set('--new-flag');
    component.addFlag();
    expect(component.currentFlagsArray()).not.toContain('--new-flag');
  });

  it('should not remove flag when hasPermission is false', () => {
    component.handlePermissionChange({hasPermission: false});
    // Initial flags are '--flag1 --flag2'
    component.removeFlag(0);
    expect(component.currentFlagsArray()).toContain('--flag1');
  });

  it('should not clear flags when hasPermission is false', () => {
    component.handlePermissionChange({hasPermission: false});
    component.clearAll();
    expect(component.currentFlagsArray().length).toBeGreaterThan(0);
  });

  it('should not append preset flags when hasPermission is false', () => {
    component.handlePermissionChange({hasPermission: false});
    const preset: PopularFlag = {
      name: 'Preset1',
      cmd: '--flag3',
      description: 'Desc',
    };
    component.appendPreset(preset);
    expect(component.currentFlagsArray()).not.toContain('--flag3');
  });
});
