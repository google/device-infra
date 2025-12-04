import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {QuarantineDialogData} from '../../../../core/models/device_action';
import {DEVICE_SERVICE} from '../../../../core/services/device/device_service';
import {FakeDeviceService} from '../../../../core/services/device/fake_device_service';
import {QuarantineDialog} from './quarantine_dialog';

describe('QuarantineDialog', () => {
  let component: QuarantineDialog;
  let fixture: ComponentFixture<MatTestDialogOpener<QuarantineDialog>>;
  let deviceService: FakeDeviceService;

  const dialogData: QuarantineDialogData = {
    deviceId: 'test-device',
    isUpdate: false,
    title: 'Test Quarantine',
    description: 'Test description',
    confirmText: 'Confirm',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        QuarantineDialog,
        NoopAnimationsModule, // This makes test faster and more stable.
        MatTestDialogOpenerModule,
      ],
      providers: [
        {provide: MAT_DIALOG_DATA, useValue: dialogData},
        {provide: DEVICE_SERVICE, useClass: FakeDeviceService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(QuarantineDialog, {data: dialogData}),
    );
    fixture.detectChanges();
    component = fixture.componentInstance.dialogRef.componentInstance;
    deviceService = TestBed.inject(DEVICE_SERVICE) as FakeDeviceService;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should validate duration correctly', () => {
    component.durationHours = 0;
    expect(component.validateDuration()).toBeFalse();
    expect(component.durationError).toBe(
      'Duration must be an integer between 1 and 168.',
    );

    component.durationHours = 169;
    expect(component.validateDuration()).toBeFalse();

    component.durationHours = 1.5;
    expect(component.validateDuration()).toBeFalse();

    component.durationHours = 24;
    expect(component.validateDuration()).toBeTrue();
    expect(component.durationError).toBe('');
  });

  it('should not call quarantineDevice if duration is invalid', () => {
    spyOn(deviceService, 'quarantineDevice');
    component.durationHours = 0;
    component.applyQuarantine();
    expect(deviceService.quarantineDevice).not.toHaveBeenCalled();
  });

  it('should update duration and expiry time preview when setDuration is called', () => {
    component.setDuration(8);
    fixture.detectChanges();
    expect(component.durationHours).toBe(8);
    // We can't reliably check for "8 hours" because the formatted date depends on the test execution time.
    // Instead, we'll just check that the expiryTime string is not empty.
    expect(component.expiryTime).not.toBe('');
  });
});
