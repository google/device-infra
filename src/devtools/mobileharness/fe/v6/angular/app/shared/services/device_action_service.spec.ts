import {TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {MatSnackBarRef} from '@angular/material/snack-bar';
import {of, throwError} from 'rxjs';

import {
  DeviceHeaderInfo,
  IneligibilityReasonCode,
} from '../../core/models/device_action';
import {
  DEVICE_SERVICE,
  DeviceService,
} from '../../core/services/device/device_service';
import {FlashDialog} from '../../features/device_detail/components/flash_dialog/flash_dialog';
import {LogcatLinkDialog} from '../../features/device_detail/components/logcat_link_dialog/logcat_link_dialog';
import {QuarantineDialog} from '../../features/device_detail/components/quarantine_dialog/quarantine_dialog';
import {ScreenshotDialog} from '../../features/device_detail/components/screenshot_dialog/screenshot_dialog';
import {ActionErrorContent} from '../components/action_error_content/action_error_content';
import {ConfirmDialog} from '../components/confirm_dialog/confirm_dialog';
import {AccessDeniedContent} from '../components/remote_control/feedback/access_denied_content';
import {SnackBar} from '../components/snackbar/snackbar';
import {DeviceActionService} from './device_action_service';
import {SnackBarService} from './snackbar_service';

describe('DeviceActionService', () => {
  let service: DeviceActionService;
  let deviceServiceSpy: jasmine.SpyObj<DeviceService>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;
  let snackBarSpy: jasmine.SpyObj<SnackBarService>;
  let mockSnackBarRef: jasmine.SpyObj<MatSnackBarRef<SnackBar>>;

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
      'showInProgress',
    ]);
    mockSnackBarRef = jasmine.createSpyObj('MatSnackBarRef', ['dismiss']);
    snackBarSpy.showInProgress.and.returnValue(mockSnackBarRef);

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

    expect(snackBarSpy.showInProgress).toHaveBeenCalledWith(
      'Taking screenshot...',
    );
    expect(mockSnackBarRef.dismiss).toHaveBeenCalled();
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

    expect(snackBarSpy.showInProgress).toHaveBeenCalledWith(
      'Taking screenshot...',
    );
    expect(mockSnackBarRef.dismiss).toHaveBeenCalled();
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

    expect(snackBarSpy.showInProgress).toHaveBeenCalledWith(
      'Getting logcat...',
    );
    expect(mockSnackBarRef.dismiss).toHaveBeenCalled();
    expect(deviceServiceSpy.getLogcat).toHaveBeenCalledWith('device-1');
    expect(snackBarSpy.showSuccess).toHaveBeenCalledWith(
      'Logcat retrieved successfully. And opened in a new browser tab.',
    );
    expect(window.open).toHaveBeenCalled();
    const openArgs = (window.open as jasmine.Spy).calls.mostRecent().args;
    expect(openArgs[0]).toBe('http://mock-log-url');
    expect(openArgs[1]).toBe('_blank');

    expect(dialogSpy.open).toHaveBeenCalledWith(
      LogcatLinkDialog,
      jasmine.objectContaining({
        data: {
          logUrl: 'http://mock-log-url',
        },
      }),
    );
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

    expect(snackBarSpy.showInProgress).toHaveBeenCalledWith(
      'Getting logcat...',
    );
    expect(mockSnackBarRef.dismiss).toHaveBeenCalled();
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

  describe('takeScreenshot error handling', () => {
    it('should open AccessDeniedContent when errorType is PERMISSION_DENIED', () => {
      const mockResponse = {
        screenshotUrl: '',
        capturedAt: '',
        errorType: 'PERMISSION_DENIED' as IneligibilityReasonCode,
        errorMessage: 'Lacks permission',
      };
      deviceServiceSpy.takeScreenshot.and.returnValue(of(mockResponse));

      service.takeScreenshot('device-1').subscribe({
        error: () => {},
      });

      expect(snackBarSpy.showInProgress).toHaveBeenCalledWith(
        'Taking screenshot...',
      );
      expect(mockSnackBarRef.dismiss).toHaveBeenCalled();
      expect(dialogSpy.open).toHaveBeenCalledWith(
        ConfirmDialog,
        jasmine.objectContaining({
          data: jasmine.objectContaining({
            title: 'Access Denied',
            contentComponent: AccessDeniedContent,
            contentComponentInputs: {
              devices: [{id: 'device-1'}],
              action: 'take screenshot of',
            },
          }),
        }),
      );
    });

    it('should open ActionErrorContent when errorType is other than PERMISSION_DENIED', () => {
      const mockResponse = {
        screenshotUrl: '',
        capturedAt: '',
        errorType: 'DEVICE_NOT_FOUND' as IneligibilityReasonCode,
        errorMessage: 'Device not found',
      };
      deviceServiceSpy.takeScreenshot.and.returnValue(of(mockResponse));

      service.takeScreenshot('device-1').subscribe({
        error: () => {},
      });

      expect(snackBarSpy.showInProgress).toHaveBeenCalledWith(
        'Taking screenshot...',
      );
      expect(mockSnackBarRef.dismiss).toHaveBeenCalled();
      expect(dialogSpy.open).toHaveBeenCalledWith(
        ActionErrorContent,
        jasmine.objectContaining({
          data: {
            errorMessage: 'Device not found',
            errorDetails: 'Error Type: DEVICE_NOT_FOUND',
          },
        }),
      );
    });

    it('should open ActionErrorContent when RPC fails', () => {
      const error = new Error('RPC Failure');
      error.stack = 'mock stack trace';
      deviceServiceSpy.takeScreenshot.and.returnValue(throwError(() => error));

      service.takeScreenshot('device-1').subscribe({
        error: () => {},
      });

      expect(snackBarSpy.showInProgress).toHaveBeenCalledWith(
        'Taking screenshot...',
      );
      expect(mockSnackBarRef.dismiss).toHaveBeenCalled();
      expect(dialogSpy.open).toHaveBeenCalledWith(
        ActionErrorContent,
        jasmine.objectContaining({
          data: {
            errorMessage: 'RPC Failure',
            errorDetails: 'mock stack trace',
          },
        }),
      );
    });
  });

  describe('getLogcat error handling', () => {
    it('should open AccessDeniedContent when errorType is PERMISSION_DENIED', () => {
      const mockResponse = {
        logUrl: '',
        capturedAt: '',
        errorType: 'PERMISSION_DENIED' as IneligibilityReasonCode,
        errorMessage: 'Lacks permission',
      };
      deviceServiceSpy.getLogcat.and.returnValue(of(mockResponse));

      service.getLogcat('device-1').subscribe({
        error: () => {},
      });

      expect(snackBarSpy.showInProgress).toHaveBeenCalledWith(
        'Getting logcat...',
      );
      expect(mockSnackBarRef.dismiss).toHaveBeenCalled();
      expect(dialogSpy.open).toHaveBeenCalledWith(
        ConfirmDialog,
        jasmine.objectContaining({
          data: jasmine.objectContaining({
            title: 'Access Denied',
            contentComponent: AccessDeniedContent,
            contentComponentInputs: {
              devices: [{id: 'device-1'}],
              action: 'get logcat of',
            },
          }),
        }),
      );
    });

    it('should open ActionErrorContent when errorType is other than PERMISSION_DENIED', () => {
      const mockResponse = {
        logUrl: '',
        capturedAt: '',
        errorType: 'DEVICE_NOT_FOUND' as IneligibilityReasonCode,
        errorMessage: 'Device not found',
      };
      deviceServiceSpy.getLogcat.and.returnValue(of(mockResponse));

      service.getLogcat('device-1').subscribe({
        error: () => {},
      });

      expect(snackBarSpy.showInProgress).toHaveBeenCalledWith(
        'Getting logcat...',
      );
      expect(mockSnackBarRef.dismiss).toHaveBeenCalled();
      expect(dialogSpy.open).toHaveBeenCalledWith(
        ActionErrorContent,
        jasmine.objectContaining({
          data: {
            errorMessage: 'Device not found',
            errorDetails: 'Error Type: DEVICE_NOT_FOUND',
          },
        }),
      );
    });

    it('should open ActionErrorContent when RPC fails', () => {
      const error = new Error('RPC Failure');
      error.stack = 'mock stack trace';
      deviceServiceSpy.getLogcat.and.returnValue(throwError(() => error));

      service.getLogcat('device-1').subscribe({
        error: () => {},
      });

      expect(snackBarSpy.showInProgress).toHaveBeenCalledWith(
        'Getting logcat...',
      );
      expect(mockSnackBarRef.dismiss).toHaveBeenCalled();
      expect(dialogSpy.open).toHaveBeenCalledWith(
        ActionErrorContent,
        jasmine.objectContaining({
          data: {
            errorMessage: 'RPC Failure',
            errorDetails: 'mock stack trace',
          },
        }),
      );
    });
  });
});
