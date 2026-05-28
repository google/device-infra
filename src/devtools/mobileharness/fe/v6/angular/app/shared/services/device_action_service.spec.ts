import {TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {of, throwError} from 'rxjs';

import {DeviceHeaderInfo} from '../../core/models/device_action';
import {
  DEVICE_SERVICE,
  DeviceService,
} from '../../core/services/device/device_service';
import {FlashDialog} from '../../features/device_detail/components/flash_dialog/flash_dialog';
import {QuarantineDialog} from '../../features/device_detail/components/quarantine_dialog/quarantine_dialog';
import {ScreenshotDialog} from '../../features/device_detail/components/screenshot_dialog/screenshot_dialog';
import {ConfirmDialog} from '../components/confirm_dialog/confirm_dialog';
import {DeviceActionService} from './device_action_service';
import {SnackBarService} from './snackbar_service';

describe('DeviceActionService', () => {
  let service: DeviceActionService;
  let deviceServiceSpy: jasmine.SpyObj<DeviceService>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;
  let snackBarSpy: jasmine.SpyObj<SnackBarService>;

  beforeEach(() => {
    deviceServiceSpy = jasmine.createSpyObj('DeviceService', [
      'takeScreenshot',
      'getLogcat',
      'unquarantineDevice',
      'getDeviceHeaderInfo',
    ]);
    dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);
    snackBarSpy = jasmine.createSpyObj('SnackBarService', [
      'showInfo',
      'showSuccess',
      'showError',
    ]);

    TestBed.configureTestingModule({
      providers: [
        DeviceActionService,
        {provide: DEVICE_SERVICE, useValue: deviceServiceSpy},
        {provide: MatDialog, useValue: dialogSpy},
        {provide: SnackBarService, useValue: snackBarSpy},
      ],
    });

    service = TestBed.inject(DeviceActionService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should call takeScreenshot, open ScreenshotDialog and show success snackbar on success', () => {
    const mockResponse = {
      screenshotUrl: 'http://screenshot-url',
      capturedAt: '123456',
    };
    deviceServiceSpy.takeScreenshot.and.returnValue(of(mockResponse));

    service.takeScreenshot('device-1').subscribe();

    expect(snackBarSpy.showInfo).toHaveBeenCalledWith('Taking screenshot...');
    expect(deviceServiceSpy.takeScreenshot).toHaveBeenCalledWith('device-1');
    expect(snackBarSpy.showSuccess).toHaveBeenCalledWith(
      'Screenshot taken successfully.',
    );
    expect(dialogSpy.open).toHaveBeenCalledWith(
      ScreenshotDialog,
      jasmine.objectContaining({
        data: {
          deviceId: 'device-1',
          screenshotUrl: 'http://screenshot-url',
          capturedAt: '123456',
        },
      }),
    );
  });

  it('should show error snackbar when takeScreenshot fails', () => {
    deviceServiceSpy.takeScreenshot.and.returnValue(
      throwError(() => new Error('Screenshot error')),
    );

    service.takeScreenshot('device-1').subscribe({
      error: (err) => {
        expect(err.message).toBe('Screenshot error');
      },
    });

    expect(snackBarSpy.showError).toHaveBeenCalledWith(
      'Failed to take screenshot.',
    );
  });

  it('should fetch logcat, open in new tab and show success snackbar', () => {
    const mockResponse = {
      logUrl: 'http://mock-log-url',
      capturedAt: '123456',
    };
    deviceServiceSpy.getLogcat.and.returnValue(of(mockResponse));
    spyOn(window, 'open');

    service.getLogcat('device-1').subscribe();

    expect(snackBarSpy.showInfo).toHaveBeenCalledWith('Getting logcat...');
    expect(deviceServiceSpy.getLogcat).toHaveBeenCalledWith('device-1');
    expect(snackBarSpy.showSuccess).toHaveBeenCalledWith(
      'Logcat retrieved successfully. And opened in a new browser tab.',
    );
    expect(window.open).toHaveBeenCalled();
    const openArgs = (window.open as jasmine.Spy).calls.mostRecent().args;
    expect(openArgs[0]).toBe('http://mock-log-url');
    expect(openArgs[1]).toBe('_blank');
  });

  it('should show error snackbar when getLogcat fails', () => {
    deviceServiceSpy.getLogcat.and.returnValue(
      throwError(() => new Error('Logcat error')),
    );

    service.getLogcat('device-1').subscribe({
      error: (err) => {
        expect(err.message).toBe('Logcat error');
      },
    });

    expect(snackBarSpy.showError).toHaveBeenCalledWith('Failed to get logcat.');
  });

  it('should open FlashDialog with parameters', () => {
    service.flashDevice('device-1', 'host-1', {
      deviceType: 'Pixel',
      requiredDimensions: 'dim-1',
    });

    expect(dialogSpy.open).toHaveBeenCalledWith(
      FlashDialog,
      jasmine.objectContaining({
        data: {
          deviceId: 'device-1',
          hostName: 'host-1',
          deviceType: 'Pixel',
          requiredDimensions: 'dim-1',
        },
      }),
    );
  });

  it('should quarantine device by opening QuarantineDialog when not quarantined', () => {
    const mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
    mockDialogRef.afterClosed.and.returnValue(of(true));
    dialogSpy.open.and.returnValue(mockDialogRef);

    service
      .quarantineDevice('device-1', {
        quarantineInfo: {isQuarantined: false},
      })
      .subscribe();

    expect(dialogSpy.open).toHaveBeenCalledWith(
      QuarantineDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          deviceId: 'device-1',
          isUpdate: false,
        }),
      }),
    );
  });

  it('should unquarantine device on confirm when already quarantined', () => {
    const mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
    mockDialogRef.afterClosed.and.returnValue(of('primary'));
    dialogSpy.open.and.returnValue(mockDialogRef);

    deviceServiceSpy.unquarantineDevice.and.returnValue(of(undefined));

    service
      .quarantineDevice('device-1', {
        quarantineInfo: {isQuarantined: true},
      })
      .subscribe();

    expect(dialogSpy.open).toHaveBeenCalledWith(
      ConfirmDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          title: 'Unquarantine Device device-1?',
        }),
      }),
    );
    expect(deviceServiceSpy.unquarantineDevice).toHaveBeenCalledWith(
      'device-1',
    );
    expect(snackBarSpy.showSuccess).toHaveBeenCalled();
  });

  it('should show error snackbar when unquarantine fails', () => {
    const mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
    mockDialogRef.afterClosed.and.returnValue(of('primary'));
    dialogSpy.open.and.returnValue(mockDialogRef);

    deviceServiceSpy.unquarantineDevice.and.returnValue(
      throwError(() => new Error('Unquarantine failed')),
    );

    service
      .quarantineDevice('device-1', {
        quarantineInfo: {isQuarantined: true},
      })
      .subscribe({
        error: (err) => {
          expect(err.message).toBe('Unquarantine failed');
        },
      });

    expect(snackBarSpy.showError).toHaveBeenCalledWith(
      'Failed to unquarantine device.',
    );
  });

  it('should check status, and unquarantine device when retrieved status is quarantined', () => {
    const mockHeader = {quarantine: {isQuarantined: true}};
    deviceServiceSpy.getDeviceHeaderInfo.and.returnValue(
      of(mockHeader as unknown as DeviceHeaderInfo),
    );

    const mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
    mockDialogRef.afterClosed.and.returnValue(of('primary'));
    dialogSpy.open.and.returnValue(mockDialogRef);

    deviceServiceSpy.unquarantineDevice.and.returnValue(of(undefined));

    service.quarantineDevice('device-1').subscribe();

    expect(deviceServiceSpy.getDeviceHeaderInfo).toHaveBeenCalledWith(
      'device-1',
    );
    expect(deviceServiceSpy.unquarantineDevice).toHaveBeenCalledWith(
      'device-1',
    );
  });

  it('should show error snackbar when fetching status fails', () => {
    deviceServiceSpy.getDeviceHeaderInfo.and.returnValue(
      throwError(() => new Error('Status fetch failed')),
    );

    service.quarantineDevice('device-1').subscribe({
      error: (err) => {
        expect(err.message).toBe('Status fetch failed');
      },
    });

    expect(snackBarSpy.showError).toHaveBeenCalledWith(
      'Failed to fetch device quarantine status.',
    );
  });

  it('should open QuarantineDialog with update options', () => {
    const mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
    dialogSpy.open.and.returnValue(mockDialogRef);

    service.changeQuarantine('device-1', 'expiry-time');

    expect(dialogSpy.open).toHaveBeenCalledWith(
      QuarantineDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          deviceId: 'device-1',
          isUpdate: true,
          currentExpiry: 'expiry-time',
        }),
      }),
    );
  });

  it('should open FlashDialog with default empty strings when parameters are omitted', () => {
    service.flashDevice('device-1', 'host-1');

    expect(dialogSpy.open).toHaveBeenCalledWith(
      FlashDialog,
      jasmine.objectContaining({
        data: {
          deviceId: 'device-1',
          hostName: 'host-1',
          deviceType: '',
          requiredDimensions: '',
        },
      }),
    );
  });

  it('should check status, and open QuarantineDialog when retrieved status is not quarantined', () => {
    const mockHeader = {}; // missing quarantine!
    deviceServiceSpy.getDeviceHeaderInfo.and.returnValue(
      of(mockHeader as unknown as DeviceHeaderInfo),
    );

    const mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
    mockDialogRef.afterClosed.and.returnValue(of(true));
    dialogSpy.open.and.returnValue(mockDialogRef);

    service.quarantineDevice('device-1').subscribe();

    expect(deviceServiceSpy.getDeviceHeaderInfo).toHaveBeenCalledWith(
      'device-1',
    );
    expect(dialogSpy.open).toHaveBeenCalledWith(
      QuarantineDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          deviceId: 'device-1',
          isUpdate: false,
        }),
      }),
    );
  });

  it('should open QuarantineDialog with empty currentExpiry when expiry is omitted in changeQuarantine', () => {
    const mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
    dialogSpy.open.and.returnValue(mockDialogRef);

    service.changeQuarantine('device-1');

    expect(dialogSpy.open).toHaveBeenCalledWith(
      QuarantineDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          deviceId: 'device-1',
          isUpdate: true,
          currentExpiry: '',
        }),
      }),
    );
  });
});
