import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {RemoteControlDialogData} from '../../../../core/models/device_action';
import {DEVICE_SERVICE} from '../../../../core/services/device/device_service';
import {FakeDeviceService} from '../../../../core/services/device/fake_device_service';
import {SnackBarService} from '../../../../shared/services/snackbar_service';
import {RemoteControlDialog} from './remote_control_dialog';

describe('RemoteControlDialog', () => {
  let component: RemoteControlDialog;
  let fixture: ComponentFixture<MatTestDialogOpener<RemoteControlDialog>>;
  let snackBarService: jasmine.SpyObj<SnackBarService>;
  let dialogRef: MatDialogRef<RemoteControlDialog>;

  const dialogData: RemoteControlDialogData = {
    deviceId: 'test-device',
    runAsOptions: [{value: 'test_user', label: 'test_user', isDefault: true}],
    defaultRunAs: 'test_user',
  };

  beforeEach(async () => {
    snackBarService = jasmine.createSpyObj('SnackBarService', ['showError']);

    await TestBed.configureTestingModule({
      imports: [
        RemoteControlDialog,
        NoopAnimationsModule,
        MatTestDialogOpenerModule,
      ],
      providers: [
        {provide: MAT_DIALOG_DATA, useValue: dialogData},
        {provide: DEVICE_SERVICE, useClass: FakeDeviceService},
        {provide: SnackBarService, useValue: snackBarService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(RemoteControlDialog, {
        data: dialogData,
      }),
    );
    dialogRef = fixture.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    fixture.detectChanges();
    component = fixture.componentInstance.dialogRef.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should validate timeout correctly', () => {
    component.timeoutHours = 0;
    expect(component.validateTimeout()).toBeFalse();
    expect(component.timeoutError).toBe(
      'Timeout must be between 1 and 12 hours.',
    );

    component.timeoutHours = 13;
    expect(component.validateTimeout()).toBeFalse();

    component.timeoutHours = 8;
    expect(component.validateTimeout()).toBeTrue();
    expect(component.timeoutError).toBe('');
  });

  it('should validate flash options only when flash is enabled', () => {
    component.enableFlash = true;
    component.flashBranch = '';
    component.flashBuildId = 'build123';
    component.flashBuildTarget = 'target';
    expect(component.isFormValid()).toBeFalse();
    expect(component.flashBranchError).toBe('Branch is required.');

    component.flashBranch = 'main';
    expect(component.isFormValid()).toBeTrue();
    expect(component.flashBranchError).toBe('');
  });

  it('should not validate flash options when flash is disabled', () => {
    component.enableFlash = false;
    component.flashBranch = '';
    component.flashBuildId = '';
    component.flashBuildTarget = '';

    expect(component.isFormValid()).toBeTrue();
    expect(component.flashBranchError).toBe('');
    expect(component.flashBuildIdError).toBe('');
    expect(component.flashBuildTargetError).toBe('');
  });

  describe('Start Session button', () => {
    let startSessionButton: HTMLButtonElement;

    beforeEach(() => {
      startSessionButton = document.querySelector(
        '.dialog-button-primary',
      ) as HTMLButtonElement;
    });

    it('should be disabled when timeout is invalid', async () => {
      component.timeoutHours = 0;
      component.validate();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(startSessionButton.disabled).toBeTrue();

      component.timeoutHours = 13;
      component.validate();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(startSessionButton.disabled).toBeTrue();
    });

    it('should be disabled when flash is enabled but branch is missing', async () => {
      component.enableFlash = true;
      component.flashBranch = '';
      component.validate();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(startSessionButton.disabled).toBeTrue();
    });

    it('should be disabled when flash is enabled but build ID is missing', async () => {
      component.enableFlash = true;
      component.flashBranch = 'main';
      component.flashBuildId = '';
      component.validate();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(startSessionButton.disabled).toBeTrue();
    });

    it('should be disabled when flash is enabled but build target is missing', async () => {
      component.enableFlash = true;
      component.flashBranch = 'main';
      component.flashBuildId = '12345678';
      component.flashBuildTarget = '';
      component.validate();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(startSessionButton.disabled).toBeTrue();
    });

    it('should be enabled when flash is enabled and all flash inputs are valid', () => {
      component.timeoutHours = 5;
      component.enableFlash = true;
      component.flashBranch = 'main';
      component.flashBuildId = '12345678';
      component.flashBuildTarget = 'target';
      fixture.detectChanges();
      expect(startSessionButton.disabled).toBeFalse();
    });

    it('should be enabled when flash is disabled and timeout is valid', () => {
      component.timeoutHours = 5;
      component.enableFlash = false;
      fixture.detectChanges();
      expect(startSessionButton.disabled).toBeFalse();
    });
  });

  it('should close the dialog when Cancel button is clicked', () => {
    const cancelButton = document.querySelector(
      '.dialog-button-secondary',
    ) as HTMLButtonElement;
    cancelButton.click();
    expect(dialogRef.close).toHaveBeenCalled();
  });

  it('should show an error snackbar when startSession is called with an invalid form', () => {
    spyOn(component, 'isFormValid').and.returnValue(false);
    component.startSession();
    expect(snackBarService.showError).toHaveBeenCalledWith(
      'Please correct the errors in the form.',
    );
  });
});
