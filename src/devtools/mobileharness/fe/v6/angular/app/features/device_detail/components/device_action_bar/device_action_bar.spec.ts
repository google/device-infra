import {ChangeDetectorRef} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of} from 'rxjs';
import {DeviceOverviewPageData} from '../../../../core/models/device_overview';
import {DEVICE_SERVICE} from '../../../../core/services/device/device_service';
import {FakeDeviceService} from '../../../../core/services/device/fake_device_service';
import {FakeHostService} from '../../../../core/services/host/fake_host_service';
import {HOST_SERVICE} from '../../../../core/services/host/host_service';
import {ConfirmDialog} from '../../../../shared/components/confirm_dialog/confirm_dialog';
import {RemoteControlService} from '../../../../shared/services/remote_control_service';
import {SnackBarService} from '../../../../shared/services/snackbar_service';
import {FlashDialog} from '../flash_dialog/flash_dialog';
import {LogcatDialog} from '../logcat_dialog/logcat_dialog';
import {QuarantineDialog} from '../quarantine_dialog/quarantine_dialog';
import {ScreenshotDialog} from '../screenshot_dialog/screenshot_dialog';
import {DeviceActionBar} from './device_action_bar';

describe('DeviceActionBar', () => {
  let component: DeviceActionBar;
  let fixture: ComponentFixture<DeviceActionBar>;
  let snackBarService: jasmine.SpyObj<SnackBarService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let deviceService: FakeDeviceService;
  let hostService: FakeHostService;
  let remoteControlService: jasmine.SpyObj<RemoteControlService>;

  const mockPageData: DeviceOverviewPageData = {
    overview: {
      id: 'test-device',
      host: {name: 'test-host', ip: '1.2.3.4'},
      healthAndActivity: {
        title: 'Idle',
        subtitle: 'Device is idle',
        state: 'IN_SERVICE_IDLE',
        deviceStatus: {status: 'IDLE', isCritical: false},
        deviceTypes: [{type: 'AndroidRealDevice', isAbnormal: false}],
        lastInServiceTime: null,
      },
      basicInfo: {
        model: 'Pixel 8',
        version: '14',
        form: 'physical',
        os: 'Android',
        batteryLevel: 100,
        network: {wifiRssi: -50, hasInternet: true},
        hardware: 'husky',
        build: 'latest',
      },
      permissions: {owners: [], executors: []},
      capabilities: {supportedDrivers: [], supportedDecorators: []},
      dimensions: {supported: {}, required: {}},
      properties: {},
    },
    headerInfo: {
      id: 'test-device',
      quarantine: {isQuarantined: false, expiry: ''},
      actions: {
        screenshot: {enabled: true, visible: true, tooltip: ''},
        logcat: {enabled: true, visible: true, tooltip: ''},
        flash: {
          enabled: true,
          visible: true,
          tooltip: '',
          params: {deviceType: 'AndroidRealDevice', requiredDimensions: ''},
        },
        remoteControl: {
          enabled: true,
          visible: true,
          tooltip: '',
        },
        quarantine: {enabled: true, visible: true, tooltip: ''},
        configuration: {
          enabled: true,
          visible: true,
          tooltip: 'Configure device',
        },
      },
    },
  };

  beforeEach(async () => {
    snackBarService = jasmine.createSpyObj('SnackBarService', [
      'showError',
      'showSuccess',
      'showInProgress',
    ]);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    deviceService = new FakeDeviceService();
    hostService = new FakeHostService();

    remoteControlService = jasmine.createSpyObj('RemoteControlService', [
      'startRemoteControl',
    ]);

    await TestBed.configureTestingModule({
      imports: [DeviceActionBar, NoopAnimationsModule],
      providers: [
        {provide: DEVICE_SERVICE, useValue: deviceService},
        {provide: HOST_SERVICE, useValue: hostService},
        {provide: SnackBarService, useValue: snackBarService},
        {provide: MatDialog, useValue: dialog},
        {provide: RemoteControlService, useValue: remoteControlService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DeviceActionBar);
    component = fixture.componentInstance;
    component.pageData = mockPageData;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render configuration button', () => {
    const configButton = fixture.nativeElement.querySelector(
      '[data-testid="configuration-button-2xl"]',
    );
    expect(configButton).toBeTruthy();
    expect(configButton.disabled).toBeFalse();
  });

  it('should disable configuration button if enabled is false', () => {
    component.pageData = {
      ...mockPageData,
      headerInfo: {
        ...mockPageData.headerInfo,
        actions: {
          ...mockPageData.headerInfo.actions!,
          configuration: {enabled: false, visible: true, tooltip: 'Disabled'},
        },
      },
    };
    const cdr = fixture.debugElement.injector.get(ChangeDetectorRef);
    cdr.markForCheck();
    fixture.detectChanges();
    const configButton = fixture.nativeElement.querySelector(
      '[data-testid="configuration-button-2xl"]',
    ) as HTMLButtonElement;
    expect(configButton).toBeTruthy();
    expect(configButton.disabled).toBeTrue();
  });

  it('should disable flash button if enabled is false', () => {
    component.pageData = {
      ...mockPageData,
      headerInfo: {
        ...mockPageData.headerInfo,
        actions: {
          ...mockPageData.headerInfo.actions!,
          flash: {
            enabled: false,
            visible: true,
            tooltip: 'Disabled',
            params: {deviceType: 'AndroidRealDevice', requiredDimensions: ''},
          },
        },
      },
    };
    const cdr = fixture.debugElement.injector.get(ChangeDetectorRef);
    cdr.markForCheck();
    fixture.detectChanges();
    const flashButton = fixture.nativeElement.querySelector(
      '[data-testid="flash-button-2xl"]',
    ) as HTMLButtonElement;
    expect(flashButton).toBeTruthy();
    expect(flashButton.disabled).toBeTrue();
  });

  it('should call takeScreenshot and open dialog on success', () => {
    spyOn(deviceService, 'takeScreenshot').and.returnValue(
      of({
        screenshotUrl: 'http://example.com/screenshot.png',
        capturedAt: new Date().toISOString(),
      }),
    );
    const screenshotButton = fixture.nativeElement.querySelector(
      '[data-testid="screenshot-button-2xl"]',
    );
    screenshotButton.click();

    expect(deviceService.takeScreenshot).toHaveBeenCalledWith('test-device');
    expect(snackBarService.showInProgress).toHaveBeenCalledWith(
      'Taking screenshot...',
    );
    expect(snackBarService.showSuccess).toHaveBeenCalledWith(
      'Screenshot taken successfully.',
    );
    expect(dialog.open).toHaveBeenCalledWith(ScreenshotDialog, {
      data: {
        deviceId: 'test-device',
        screenshotUrl: 'http://example.com/screenshot.png',
        capturedAt: jasmine.any(String),
      },
    });
  });

  it('should call startRemoteControl when remote control button is clicked', () => {
    const remoteControlButton = fixture.nativeElement.querySelector(
      '[data-testid="remoteControl-button-2xl"]',
    );
    remoteControlButton.click();

    expect(remoteControlService.startRemoteControl).toHaveBeenCalledWith(
      'test-host',
      jasmine.any(Array),
    );
  });

  it('should open FlashDialog when flash button is clicked', () => {
    const flashButton = fixture.nativeElement.querySelector(
      '[data-testid="flash-button-2xl"]',
    );
    flashButton.click();

    expect(dialog.open).toHaveBeenCalledWith(FlashDialog, {
      data: {
        deviceId: 'test-device',
        hostName: 'test-host',
        deviceType: 'AndroidRealDevice',
        requiredDimensions: '',
      },
    });
  });

  it('should call getLogcat and open dialog on success', async () => {
    spyOn(deviceService, 'getLogcat').and.returnValue(
      of({
        logUrl: 'http://example.com/logcat.log',
        capturedAt: new Date().toISOString(),
      }),
    );
    const fetchResponse = Promise.resolve({
      text: () => Promise.resolve('some log content'),
    } as Response);
    spyOn(window, 'fetch').and.returnValue(fetchResponse);

    const logcatButton = fixture.nativeElement.querySelector(
      '[data-testid="logcat-button-2xl"]',
    );
    logcatButton.click();
    fixture.detectChanges();

    expect(deviceService.getLogcat).toHaveBeenCalledWith('test-device');
    expect(snackBarService.showInProgress).toHaveBeenCalledWith(
      'Getting logcat...',
    );

    await fixture.whenStable(); // Wait for the initial part of the action
    await fetchResponse; // Ensure the fetch mock is resolved
    await fixture.whenStable(); // Wait for the .then() block to execute
    fixture.detectChanges();

    // Add a small delay to allow the final promise resolution to complete
    await new Promise((resolve) => {
      setTimeout(resolve, 0);
    });
    fixture.detectChanges();

    expect(snackBarService.showSuccess).toHaveBeenCalledWith(
      'Logcat retrieved successfully.',
    );
    expect(dialog.open).toHaveBeenCalledWith(LogcatDialog, {
      data: {
        deviceId: 'test-device',
        logContent: 'some log content',
        capturedAt: jasmine.any(String),
        logUrl: 'http://example.com/logcat.log',
      },
    });
  });

  it('should open QuarantineDialog when quarantine button is clicked', () => {
    const quarantineButton = fixture.nativeElement.querySelector(
      '[data-testid="quarantine-button-2xl"]',
    );
    quarantineButton.click();

    expect(dialog.open).toHaveBeenCalledWith(QuarantineDialog, {
      data: {
        deviceId: 'test-device',
        isUpdate: false,
        title: jasmine.any(String),
        description: jasmine.any(String),
        confirmText: 'Quarantine',
      },
    });
  });

  it('should open ConfirmDialog when unquarantine button is clicked', () => {
    component.pageData = {
      ...mockPageData,
      headerInfo: {
        ...mockPageData.headerInfo,
        quarantine: {isQuarantined: true, expiry: '2025-12-31T23:59:59Z'},
      },
    };
    const cdr = fixture.debugElement.injector.get(ChangeDetectorRef);
    cdr.markForCheck();
    fixture.detectChanges();

    // Mock the dialog.open to return an object with afterClosed() observable
    const mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
    mockDialogRef.afterClosed.and.returnValue(of('primary'));
    dialog.open.and.returnValue(mockDialogRef);

    spyOn(deviceService, 'unquarantineDevice').and.returnValue(of(undefined));

    const unquarantineButton = fixture.nativeElement.querySelector(
      '[data-testid="quarantine-group-2xl"] .main-action',
    );
    unquarantineButton.click();

    expect(dialog.open).toHaveBeenCalledWith(ConfirmDialog, {
      data: {
        title: 'Unquarantine Device test-device?',
        content: jasmine.any(String),
        type: 'info',
        primaryButtonLabel: 'Unquarantine',
        secondaryButtonLabel: 'Cancel',
      },
    });

    expect(deviceService.unquarantineDevice).toHaveBeenCalledWith(
      'test-device',
    );
    expect(snackBarService.showInProgress).toHaveBeenCalledWith(
      'Unquarantining device...',
    );
    expect(snackBarService.showSuccess).toHaveBeenCalledWith(
      'Device unquarantined successfully.',
    );
  });
});
