import {CommonModule} from '@angular/common';
import {ApplicationRef} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatDialogModule} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTabsModule} from '@angular/material/tabs';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of} from 'rxjs';
import {QuarantineDialogData} from '../../../../core/models/device_action';
import {
  DEVICE_SERVICE,
  DeviceService,
} from '../../../../core/services/device/device_service';
import {Dialog} from '../../../../shared/components/config_common/dialog/dialog';
import {dateUtils} from '../../../../shared/utils/date_utils';
import {QuarantineDialog} from './quarantine_dialog';

describe('QuarantineDialog', () => {
  let component: QuarantineDialog;
  let fixture: ComponentFixture<MatTestDialogOpener<QuarantineDialog>>;
  let deviceService: jasmine.SpyObj<DeviceService>;
  let appRef: ApplicationRef;

  const mockDialogData: QuarantineDialogData = {
    deviceId: 'test-device',
    isUpdate: true,
    currentExpiry: '2025-01-01T12:00:00Z',
    title: 'Update Quarantine',
    description: 'Update duration',
    confirmText: 'Update',
  };

  const detectChanges = () => {
    fixture.detectChanges();
    appRef.tick();
  };

  const switchToDurationMode = () => {
    component.selectedTabIndex = 0;
    component.onTabChange();
    detectChanges();
  };

  const switchToEndTimeMode = () => {
    component.selectedTabIndex = 1;
    component.onTabChange();
    detectChanges();
  };

  const setDuration = (minutes: number) => {
    const input = document.querySelector(
      'input#q-duration',
    ) as HTMLInputElement;
    input.value = minutes.toString();
    input.dispatchEvent(new Event('input'));
    detectChanges();
  };

  const setEndTime = (timeString: string) => {
    component.endTime = timeString;
    component.onEndTimeChange();
    detectChanges();
  };

  const getApplyButton = (): HTMLButtonElement => {
    return document.querySelector(
      '.dialog-button-primary',
    ) as HTMLButtonElement;
  };

  const mockCurrentTime = (date: Date) => {
    jasmine.clock().install();
    jasmine.clock().mockDate(date);
  };

  const restoreTime = () => {
    jasmine.clock().uninstall();
  };

  /**
   * Formats a Date object to a string compatible with <input type="datetime-local">.
   * Format: YYYY-MM-DDTHH:mm
   */
  const formatToLocalIsoString = (date: Date): string => {
    const YYYY = date.getFullYear();
    const MM = String(date.getMonth() + 1).padStart(2, '0');
    const DD = String(date.getDate()).padStart(2, '0');
    const hh = String(date.getHours()).padStart(2, '0');
    const mm = String(date.getMinutes()).padStart(2, '0');
    return `${YYYY}-${MM}-${DD}T${hh}:${mm}`;
  };

  beforeEach(async () => {
    const deviceServiceSpy = jasmine.createSpyObj('DeviceService', [
      'quarantineDevice',
    ]);

    await TestBed.configureTestingModule({
      imports: [
        QuarantineDialog,
        NoopAnimationsModule,
        CommonModule,
        Dialog,
        FormsModule,
        MatButtonModule,
        MatDialogModule,
        MatTestDialogOpenerModule,
        MatFormFieldModule,
        MatIconModule,
        MatInputModule,
        MatProgressSpinnerModule,
        MatTabsModule,
      ],
      providers: [{provide: DEVICE_SERVICE, useValue: deviceServiceSpy}],
    }).compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(QuarantineDialog, {
        data: mockDialogData,
      }),
    );
    fixture.detectChanges();
    component = fixture.componentInstance.dialogRef.componentInstance;
    deviceService = TestBed.inject(
      DEVICE_SERVICE,
    ) as jasmine.SpyObj<DeviceService>;
    appRef = TestBed.inject(ApplicationRef);
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display current expiry time in local format with timezone', () => {
    const expiryElement = document.querySelector('.current-expiry-text strong');
    const expectedText = dateUtils.format(mockDialogData.currentExpiry!);
    expect(expiryElement?.textContent).toBe(expectedText);
  });

  it('should calculate and display expiry preview in local format with timezone when duration changes', () => {
    const now = new Date();
    mockCurrentTime(now);

    setDuration(120); // 2 hours

    const previewElement = document.querySelector(
      '.expiry-preview-text strong',
    );

    const expectedDate = new Date(now.getTime() + 120 * 60 * 1000);
    const expectedText = dateUtils.format(expectedDate.toISOString());

    expect(previewElement?.textContent).toBe(expectedText);

    restoreTime();
  });

  it('should call quarantine service with UTC ISO string from "By Duration"', () => {
    deviceService.quarantineDevice.and.returnValue(of({quarantineExpiry: ''}));

    // Set a spy on Date.now to control the time
    const now = new Date('2025-01-01T10:00:00Z');
    mockCurrentTime(now);

    switchToDurationMode();
    setDuration(60);

    component.applyQuarantine();

    const expectedExpiry = new Date(now.getTime() + 60 * 60 * 1000);
    const expectedISOString = expectedExpiry.toISOString();

    expect(deviceService.quarantineDevice).toHaveBeenCalledWith(
      mockDialogData.deviceId,
      {endTime: expectedISOString},
    );

    restoreTime();
  });

  it('should call quarantine service with UTC ISO string from "By End Time"', () => {
    deviceService.quarantineDevice.and.returnValue(of({quarantineExpiry: ''}));

    // Mock Date.now() to control the validation window
    const now = new Date('2025-01-01T10:00:00Z');
    mockCurrentTime(now);

    switchToEndTimeMode();

    // Set end time to be 1 hour in the future from "now"
    const futureTime = new Date(now.getTime() + 60 * 60 * 1000);
    const futureTimeString = formatToLocalIsoString(futureTime);
    setEndTime(futureTimeString);

    component.applyQuarantine();

    // The component converts the local time string from the input to a UTC ISO string.
    const expectedISOString = new Date(futureTimeString).toISOString();

    expect(deviceService.quarantineDevice).toHaveBeenCalledWith(
      mockDialogData.deviceId,
      {endTime: expectedISOString},
    );

    restoreTime();
  });

  it('should disable the apply button for invalid duration', () => {
    switchToDurationMode();
    setDuration(5); // Invalid
    expect(getApplyButton().disabled).toBeTrue();
  });

  it('should enable the apply button for valid duration', () => {
    switchToDurationMode();
    setDuration(60); // Valid
    expect(getApplyButton().disabled).toBeFalse();
  });

  it('should disable the apply button for invalid end time', () => {
    switchToEndTimeMode();
    const now = new Date();
    const pastTime = new Date(now.getTime() - 1000); // In the past
    const pastTimeString = formatToLocalIsoString(pastTime);
    setEndTime(pastTimeString);
    expect(getApplyButton().disabled).toBeTrue();
  });

  it('should enable the apply button for valid end time', () => {
    switchToEndTimeMode();
    const now = new Date();
    const futureTime = new Date(now.getTime() + 20 * 60 * 1000); // 20 mins in future
    const futureTimeString = formatToLocalIsoString(futureTime);
    setEndTime(futureTimeString);
    expect(getApplyButton().disabled).toBeFalse();
  });

  it('should disable apply button on End Time tab after clearing the input', () => {
    // Start on the duration tab, which is valid by default
    switchToDurationMode();
    expect(getApplyButton().disabled).toBeFalse();

    // Switch to the end time tab
    switchToEndTimeMode();
    // Button should still be enabled because the value was synced from the duration tab
    expect(getApplyButton().disabled).toBeFalse();

    // Simulate user clearing the input
    setEndTime('');

    // Now the button should be disabled
    expect(getApplyButton().disabled).toBeTrue();
  });
});
