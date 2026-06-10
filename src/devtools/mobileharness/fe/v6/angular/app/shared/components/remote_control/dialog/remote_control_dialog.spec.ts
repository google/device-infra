import {ApplicationRef} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialog, MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {DeviceProxyType} from '../../../../core/models/host_overview';
import {SnackBarService} from '../../../services/snackbar_service';
import {ConfirmDialog} from '../../confirm_dialog/confirm_dialog';
import {RemoteControlDialogData} from '../remote_control.types';
import {RemoteControlDialog} from './remote_control_dialog';

describe('RemoteControlDialog', () => {
  let fixture: ComponentFixture<MatTestDialogOpener<RemoteControlDialog>>;
  let component: RemoteControlDialog;
  let snackBarSpy: jasmine.SpyObj<SnackBarService>;
  let dialogRef: MatDialogRef<RemoteControlDialog>;
  let appRef: ApplicationRef;

  beforeEach(async () => {
    snackBarSpy = jasmine.createSpyObj('SnackBarService', ['showError']);

    await TestBed.configureTestingModule({
      imports: [
        RemoteControlDialog,
        NoopAnimationsModule,
        MatTestDialogOpenerModule,
      ],
      providers: [
        provideRouter([]),
        {provide: SnackBarService, useValue: snackBarSpy},
      ],
    }).compileComponents();

    appRef = TestBed.inject(ApplicationRef);
  });

  function tickAndDetectChanges() {
    const f = fixture;
    f.detectChanges();
    appRef.tick();
  }

  function initComponent(data: Partial<RemoteControlDialogData>) {
    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(RemoteControlDialog, {
        data: data as RemoteControlDialogData,
      })
    );
    tickAndDetectChanges();
    component = fixture.componentInstance.dialogRef.componentInstance;
    dialogRef = fixture.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
  }

  it('should be created', () => {
    initComponent({
      devices: [],
      eligibilityResults: [],
      sessionOptions: {
        maxDurationHours: 12,
        commonRunAsCandidates: [],
        commonProxyTypes: [],
      },
    });
    expect(fixture).toBeTruthy();
  });

  it('syncToSlider should sync H and M inputs to total minutes', () => {
    initComponent({
      devices: [],
      eligibilityResults: [],
      sessionOptions: {
        maxDurationHours: 3,
        commonRunAsCandidates: [],
        commonProxyTypes: [],
      },
    });
    component.form.get('durationH')?.setValue(2);
    component.form.get('durationM')?.setValue(0);
    component.form.get('durationH')?.updateValueAndValidity();
    component.form.get('durationM')?.updateValueAndValidity();
    tickAndDetectChanges();

    expect(component.form.get('durationMinutes')?.value).toBe(120);
  });

  it('setupDurationValidators should enforce max duration', () => {
    initComponent({
      devices: [],
      eligibilityResults: [],
      sessionOptions: {
        maxDurationHours: 3,
        commonRunAsCandidates: [],
        commonProxyTypes: [],
      },
    });
    const durationMinutes = component.form.get('durationMinutes')!;

    durationMinutes.setValue(190);
    tickAndDetectChanges();

    expect(durationMinutes.invalid).toBeTrue();
    expect(durationMinutes.errors?.['max']).toBeTruthy();

    durationMinutes.setValue(180);
    tickAndDetectChanges();
    expect(durationMinutes.valid).toBeTrue();
  });

  it('isDurationCombinedInvalid should return true only when invalid and dirty/touched', () => {
    initComponent({
      devices: [],
      eligibilityResults: [],
      sessionOptions: {
        maxDurationHours: 3,
        commonRunAsCandidates: [],
        commonProxyTypes: [],
      },
    });
    const durationH = component.form.get('durationH')!;

    durationH.setValue(1.5);
    tickAndDetectChanges();

    expect(component.isDurationCombinedInvalid()).toBeFalse();

    durationH.markAsDirty();
    tickAndDetectChanges();

    expect(component.isDurationCombinedInvalid()).toBeTrue();
  });

  it('initializeDeviceData should map devices and eligibility results correctly', () => {
    initComponent({
      devices: [
        {id: 'device-1', model: 'Pixel 9', isTestbed: false, subDevices: []},
        {id: 'device-2', model: 'Pixel 8', isTestbed: false, subDevices: []},
      ],
      eligibilityResults: [
        {
          deviceId: 'device-1',
          isEligible: true,
          runAsCandidates: ['user1', 'user2'],
          supportedProxyTypes: [],
        },
        {
          deviceId: 'device-2',
          isEligible: false,
          ineligibilityReason: {
            code: 'PERMISSION_DENIED',
            message: 'No access',
          },
          runAsCandidates: [],
          supportedProxyTypes: [],
        },
      ],
      sessionOptions: {
        maxDurationHours: 3,
        commonRunAsCandidates: ['user2'],
        commonProxyTypes: [DeviceProxyType.ADB_ONLY],
      },
    });

    tickAndDetectChanges();

    expect(component.deviceList().length).toBe(2);
    expect(component.deviceList()[0].hasAccess).toBeTrue();
    expect(component.deviceList()[0].validIdentities).toEqual([
      'user1',
      'user2',
    ]);
    expect(component.deviceList()[1].hasAccess).toBeFalse();

    expect(component.commonProxyModes()).toEqual([DeviceProxyType.ADB_ONLY]);
    expect(component.commonIdentities()).toEqual(['user2']);
  });

  it('should sync globalRunAs value to all matching child runAs fields', () => {
    initComponent({
      devices: [
        {id: 'device-1', model: 'Pixel 9', isTestbed: false, subDevices: []},
        {id: 'device-2', model: 'Pixel 8', isTestbed: false, subDevices: []},
      ],
      eligibilityResults: [
        {
          deviceId: 'device-1',
          isEligible: true,
          runAsCandidates: ['user1', 'user2'],
          supportedProxyTypes: [],
        },
        {
          deviceId: 'device-2',
          isEligible: true,
          runAsCandidates: ['user1', 'user3'],
          supportedProxyTypes: [],
        },
      ],
      sessionOptions: {
        maxDurationHours: 3,
        commonRunAsCandidates: ['user1'],
        commonProxyTypes: [DeviceProxyType.ADB_ONLY],
      },
    });
    tickAndDetectChanges();

    component.form.get('globalRunAs')?.setValue('user1');
    tickAndDetectChanges();

    expect(component.deviceConfigs.at(0).get('runAs')?.value).toBe('user1');
    expect(component.deviceConfigs.at(1).get('runAs')?.value).toBe('user1');
  });

  it('should update globalRunAs when all child runAs are identical', () => {
    initComponent({
      devices: [
        {id: 'device-1', model: 'Pixel 9', isTestbed: false, subDevices: []},
        {id: 'device-2', model: 'Pixel 8', isTestbed: false, subDevices: []},
      ],
      eligibilityResults: [
        {
          deviceId: 'device-1',
          isEligible: true,
          runAsCandidates: ['user1', 'user2'],
          supportedProxyTypes: [],
        },
        {
          deviceId: 'device-2',
          isEligible: true,
          runAsCandidates: ['user1', 'user3'],
          supportedProxyTypes: [],
        },
      ],
      sessionOptions: {
        maxDurationHours: 3,
        commonRunAsCandidates: ['user1'],
        commonProxyTypes: [DeviceProxyType.ADB_ONLY],
      },
    });
    tickAndDetectChanges();

    component.deviceConfigs.at(0).get('runAs')?.setValue('user1');
    component.deviceConfigs.at(1).get('runAs')?.setValue('user1');
    tickAndDetectChanges();

    expect(component.form.get('globalRunAs')?.value).toBe('user1');

    component.deviceConfigs.at(1).get('runAs')?.setValue('user3');
    tickAndDetectChanges();

    expect(component.form.get('globalRunAs')?.value).toBe('');
  });

  it('should toggle expanded device lists and remove devices correctly', () => {
    initComponent({
      devices: [
        {id: 'device-1', model: 'Pixel 9', isTestbed: false, subDevices: []},
      ],
      eligibilityResults: [
        {
          deviceId: 'device-1',
          isEligible: true,
          runAsCandidates: ['user1'],
          supportedProxyTypes: [],
        },
      ],
      sessionOptions: {
        maxDurationHours: 3,
        commonRunAsCandidates: ['user1'],
        commonProxyTypes: [],
      },
    });
    tickAndDetectChanges();

    expect(component.isExpanded('device-1')).toBeFalse();
    component.toggleSubDevices('device-1');
    expect(component.isExpanded('device-1')).toBeTrue();

    component.removeDevice(0);
    expect(component.deviceList().length).toBe(0);
    expect(dialogRef.close).toHaveBeenCalled();
  });

  it('startSession should validate form and open ConfirmDialog, then close with request', () => {
    initComponent({
      devices: [
        {id: 'device-1', model: 'Pixel 9', isTestbed: false, subDevices: []},
      ],
      eligibilityResults: [
        {
          deviceId: 'device-1',
          isEligible: true,
          runAsCandidates: ['user1'],
          supportedProxyTypes: [],
        },
      ],
      sessionOptions: {
        maxDurationHours: 3,
        commonRunAsCandidates: ['user1'],
        commonProxyTypes: [DeviceProxyType.ADB_ONLY],
      },
    });
    tickAndDetectChanges();

    component.deviceConfigs.at(0).get('runAs')?.setValue('user1');
    component.form.get('durationMinutes')?.setValue(60);
    component.form.get('proxyType')?.setValue(DeviceProxyType.ADB_ONLY);
    tickAndDetectChanges();

    expect(component.isFormValid()).toBeTrue();

    const mockConfirmDialogRef = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    mockConfirmDialogRef.afterClosed.and.returnValue(of('primary'));
    const dialogSpy = spyOn(
      (component as unknown as {dialog: MatDialog}).dialog,
      'open',
    ).and.returnValue(mockConfirmDialogRef);

    component.startSession();

    expect(dialogSpy).toHaveBeenCalledWith(ConfirmDialog, jasmine.any(Object));
    expect(dialogRef.close).toHaveBeenCalledWith(
      jasmine.objectContaining({
        durationSeconds: 3600,
        proxyType: DeviceProxyType.ADB_ONLY,
      }),
    );
  });

  it('should require flash build details when enableFlash is true', () => {
    initComponent({
      devices: [
        {
          id: 'device-1',
          model: 'Pixel 9',
          isTestbed: true,
          subDevices: [{id: 'sub-1', model: 'Pixel 8', types: []}],
        },
      ],
      eligibilityResults: [
        {
          deviceId: 'device-1',
          isEligible: true,
          runAsCandidates: ['user1'],
          supportedProxyTypes: [],
          subDeviceResults: [{deviceId: 'sub-1', isEligible: true}],
        },
      ],
      sessionOptions: {
        maxDurationHours: 3,
        commonRunAsCandidates: ['user1'],
        commonProxyTypes: [],
      },
    });
    tickAndDetectChanges();

    const enableFlashControl = component.form.get('enableFlash')!;
    const flashBranch = component.form.get('flashBranch')!;

    expect(flashBranch.valid).toBeTrue();

    enableFlashControl.setValue(true);
    tickAndDetectChanges();

    expect(flashBranch.invalid).toBeTrue();
    expect(flashBranch.errors?.['required']).toBeTrue();

    flashBranch.setValue('git_master');
    tickAndDetectChanges();

    expect(flashBranch.valid).toBeTrue();
  });

  it('isControlInvalid should return true only when control is invalid and touched/dirty', () => {
    initComponent({
      devices: [
        {
          id: 'device-1',
          model: 'Pixel 9',
          isTestbed: true,
          subDevices: [{id: 'sub-1', model: 'Pixel 8', types: []}],
        },
      ],
      eligibilityResults: [
        {
          deviceId: 'device-1',
          isEligible: true,
          runAsCandidates: ['user1'],
          supportedProxyTypes: [],
          subDeviceResults: [{deviceId: 'sub-1', isEligible: true}],
        },
      ],
      sessionOptions: {
        maxDurationHours: 3,
        commonRunAsCandidates: ['user1'],
        commonProxyTypes: [],
      },
    });
    tickAndDetectChanges();

    const enableFlashControl = component.form.get('enableFlash')!;
    const flashBranch = component.form.get('flashBranch')!;

    expect(component.isControlInvalid('flashBranch')).toBeFalse();

    enableFlashControl.setValue(true);
    tickAndDetectChanges();

    expect(component.isControlInvalid('flashBranch')).toBeTrue();

    flashBranch.setValue('git_master');
    tickAndDetectChanges();

    expect(component.isControlInvalid('flashBranch')).toBeFalse();
  });

  it('startSession with flash enabled should open ConfirmDialog with flashOptions', () => {
    initComponent({
      devices: [
        {
          id: 'device-1',
          model: 'Pixel 9',
          isTestbed: true,
          subDevices: [{id: 'sub-1', model: 'Pixel 8', types: []}],
        },
      ],
      eligibilityResults: [
        {
          deviceId: 'device-1',
          isEligible: true,
          runAsCandidates: ['user1'],
          supportedProxyTypes: [],
          subDeviceResults: [{deviceId: 'sub-1', isEligible: true}],
        },
      ],
      sessionOptions: {
        maxDurationHours: 3,
        commonRunAsCandidates: ['user1'],
        commonProxyTypes: [DeviceProxyType.ADB_ONLY],
      },
    });
    tickAndDetectChanges();

    component.deviceConfigs.at(0).get('runAs')?.setValue('user1');
    component.form.get('durationMinutes')?.setValue(60);
    component.form.get('proxyType')?.setValue(DeviceProxyType.ADB_ONLY);

    component.form.get('enableFlash')?.setValue(true);
    component.form.get('flashBranch')?.setValue('git_master');
    component.form.get('flashBuildId')?.setValue('12345');
    component.form.get('flashTarget')?.setValue('target');
    component.form.get('flashSubDeviceId')?.setValue('sub-1');
    tickAndDetectChanges();

    expect(component.isFormValid()).toBeTrue();

    const mockConfirmDialogRef = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    mockConfirmDialogRef.afterClosed.and.returnValue(of('primary'));
    const dialogSpy = spyOn(
      (component as unknown as {dialog: MatDialog}).dialog,
      'open',
    ).and.returnValue(mockConfirmDialogRef);

    component.startSession();

    expect(dialogSpy).toHaveBeenCalledWith(ConfirmDialog, jasmine.objectContaining({
      data: jasmine.objectContaining({
        contentComponentInputs: jasmine.objectContaining({
          request: jasmine.objectContaining({
            flashOptions: {
              branch: 'git_master',
              buildId: '12345',
              target: 'target',
              subDeviceId: 'sub-1',
            }
          })
        })
      })
    }));
  });
});
