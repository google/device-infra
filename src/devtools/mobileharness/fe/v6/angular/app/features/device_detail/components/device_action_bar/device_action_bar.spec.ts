import {ChangeDetectorRef} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {ActionBarAction} from '@deviceinfra/app/core/constants/action_bar_config';
import {APP_DATA} from '@deviceinfra/app/core/models/app_data';
import {of, Subject} from 'rxjs';
import {
  GetLogcatResponse,
  TakeScreenshotResponse,
} from '../../../../core/models/device_action';
import {DeviceOverviewPageData} from '../../../../core/models/device_overview';
import {Environment} from '../../../../core/services/environment';
import {FakeHostService} from '../../../../core/services/host/fake_host_service';
import {HOST_SERVICE} from '../../../../core/services/host/host_service';
import {ComingSoonService} from '../../../../shared/services/coming_soon_service';
import {DeviceActionService} from '../../../../shared/services/device_action_service';
import {RemoteControlService} from '../../../../shared/services/remote_control_service';

import {DeviceActionBar} from './device_action_bar';

describe('DeviceActionBar', () => {
  let component: DeviceActionBar;
  let fixture: ComponentFixture<DeviceActionBar>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let hostService: FakeHostService;
  let remoteControlService: jasmine.SpyObj<RemoteControlService>;
  let comingSoonService: jasmine.SpyObj<ComingSoonService>;
  let deviceActionService: jasmine.SpyObj<DeviceActionService>;

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
        screenshot: {enabled: true, visible: true, tooltip: '', isReady: true},
        logcat: {enabled: true, visible: true, tooltip: '', isReady: true},
        flash: {
          state: {
            enabled: true,
            visible: true,
            tooltip: '',
            isReady: true,
          },
          params: {deviceType: 'AndroidRealDevice', requiredDimensions: ''},
        },
        remoteControl: {
          enabled: true,
          visible: true,
          tooltip: '',
          isReady: true,
        },
        quarantine: {enabled: true, visible: true, tooltip: '', isReady: true},
        configuration: {
          enabled: true,
          visible: true,
          tooltip: 'Configure device',
          isReady: true,
        },
        decommission: {
          enabled: true,
          visible: true,
          tooltip: '',
          isReady: true,
        },
      },
    },
  };

  let mockEnvironment: jasmine.SpyObj<Environment>;

  beforeEach(async () => {
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    hostService = new FakeHostService();
    comingSoonService = jasmine.createSpyObj('ComingSoonService', [
      'show',
      'showForDevice',
      'showForHost',
    ]);

    mockEnvironment = jasmine.createSpyObj('Environment', ['isGoogleInternal']);
    mockEnvironment.isGoogleInternal.and.returnValue(true);

    remoteControlService = jasmine.createSpyObj('RemoteControlService', [
      'startRemoteControl',
    ]);
    remoteControlService.startRemoteControl.and.returnValue(of(undefined));

    deviceActionService = jasmine.createSpyObj('DeviceActionService', [
      'takeScreenshot',
      'flashDevice',
      'getLogcat',
      'quarantineDevice',
      'changeQuarantine',
      'configureDevice',
    ]);
    deviceActionService.takeScreenshot.and.returnValue(
      of({} as TakeScreenshotResponse),
    );
    deviceActionService.getLogcat.and.returnValue(of({} as GetLogcatResponse));
    deviceActionService.quarantineDevice.and.returnValue(of(undefined));
    deviceActionService.changeQuarantine.and.returnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [DeviceActionBar, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        {provide: HOST_SERVICE, useValue: hostService},
        {provide: MatDialog, useValue: dialog},
        {provide: RemoteControlService, useValue: remoteControlService},
        {provide: ComingSoonService, useValue: comingSoonService},
        {provide: DeviceActionService, useValue: deviceActionService},
        {provide: APP_DATA, useValue: {applicationId: 'test-app'}},
        {provide: Environment, useValue: mockEnvironment},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DeviceActionBar);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('pageData', mockPageData);
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
    fixture.componentRef.setInput('pageData', {
      ...mockPageData,
      headerInfo: {
        ...mockPageData.headerInfo,
        actions: {
          ...mockPageData.headerInfo.actions!,
          configuration: {
            enabled: false,
            visible: true,
            tooltip: 'Disabled',
            isReady: true,
          },
        },
      },
    });
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
    fixture.componentRef.setInput('pageData', {
      ...mockPageData,
      headerInfo: {
        ...mockPageData.headerInfo,
        actions: {
          ...mockPageData.headerInfo.actions!,
          flash: {
            state: {
              enabled: false,
              visible: true,
              tooltip: 'Disabled',
              isReady: true,
            },
            params: {deviceType: 'AndroidRealDevice', requiredDimensions: ''},
          },
        },
      },
    });
    const cdr = fixture.debugElement.injector.get(ChangeDetectorRef);
    cdr.markForCheck();
    fixture.detectChanges();
    const flashButton = fixture.nativeElement.querySelector(
      '[data-testid="flash-button-2xl"]',
    ) as HTMLButtonElement;
    expect(flashButton).toBeTruthy();
    expect(flashButton.disabled).toBeTrue();
  });

  it('should show coming soon popup if an action is not ready', () => {
    fixture.componentRef.setInput('pageData', {
      ...mockPageData,
      headerInfo: {
        ...mockPageData.headerInfo,
        actions: {
          ...mockPageData.headerInfo.actions!,
          screenshot: {
            enabled: true,
            visible: true,
            tooltip: 'Coming Soon',
            isReady: false,
          },
        },
      },
    });
    const cdr = fixture.debugElement.injector.get(ChangeDetectorRef);
    cdr.markForCheck();
    fixture.detectChanges();

    const screenshotButton = fixture.nativeElement.querySelector(
      '[data-testid="screenshot-button-2xl"]',
    );
    screenshotButton.click();

    expect(comingSoonService.showForDevice).toHaveBeenCalledWith(
      ActionBarAction.DEVICE_SCREENSHOT,
      component.legacyFeUrl,
      'test-host',
      '1.2.3.4',
      'test-device',
    );

    expect(deviceActionService.takeScreenshot).not.toHaveBeenCalled();
  });

  it('should call deviceActionService.takeScreenshot when screenshot button is clicked', () => {
    const screenshotButton = fixture.nativeElement.querySelector(
      '[data-testid="screenshot-button-2xl"]',
    );
    screenshotButton.click();

    expect(deviceActionService.takeScreenshot).toHaveBeenCalledWith(
      'test-device',
    );
  });

  it('should show loading spinner and disable button while taking screenshot', () => {
    const screenshotSubject = new Subject<TakeScreenshotResponse>();
    deviceActionService.takeScreenshot.and.returnValue(
      screenshotSubject.asObservable(),
    );

    const screenshotButton = fixture.nativeElement.querySelector(
      '[data-testid="screenshot-button-2xl"]',
    ) as HTMLButtonElement;

    expect(screenshotButton.disabled).toBeFalse();
    expect(screenshotButton.querySelector('.spin-animation')).toBeNull();

    screenshotButton.click();
    fixture.detectChanges();

    expect(screenshotButton.disabled).toBeTrue();
    expect(screenshotButton.querySelector('.spin-animation')).toBeTruthy();
    expect(screenshotButton.textContent).toContain('Taking...');

    screenshotSubject.next({
      screenshotUrl: 'http://mock-url',
      capturedAt: '123456',
    });
    screenshotSubject.complete();
    fixture.detectChanges();

    expect(screenshotButton.disabled).toBeFalse();
    expect(screenshotButton.querySelector('.spin-animation')).toBeNull();
    expect(screenshotButton.textContent).toContain('Screenshot');
  });

  it('should call startRemoteControl when remote control button is clicked', () => {
    const remoteControlButton = fixture.nativeElement.querySelector(
      '[data-testid="remoteControl-button-2xl"]',
    );
    remoteControlButton.click();

    expect(remoteControlService.startRemoteControl).toHaveBeenCalledWith(
      'test-host',
      jasmine.any(Array),
      false,
    );
  });

  it('should call deviceActionService.flashDevice when flash button is clicked', () => {
    const flashButton = fixture.nativeElement.querySelector(
      '[data-testid="flash-button-2xl"]',
    );
    flashButton.click();

    expect(deviceActionService.flashDevice).toHaveBeenCalledWith(
      'test-device',
      'test-host',
      mockPageData.headerInfo.actions!.flash!.params,
    );
  });

  it('should call deviceActionService.quarantineDevice when quarantine button is clicked', () => {
    const quarantineButton = fixture.nativeElement.querySelector(
      '[data-testid="quarantine-button-2xl"]',
    );
    quarantineButton.click();

    expect(deviceActionService.quarantineDevice).toHaveBeenCalledWith(
      'test-device',
      {
        quarantineInfo: {
          isQuarantined: false,
          expiry: '',
        },
      },
    );
  });

  it('should call deviceActionService.quarantineDevice with isQuarantined: true when unquarantine button is clicked', () => {
    fixture.componentRef.setInput('pageData', {
      ...mockPageData,
      headerInfo: {
        ...mockPageData.headerInfo,
        quarantine: {isQuarantined: true, expiry: '2025-12-31T23:59:59Z'},
      },
    });
    const cdr = fixture.debugElement.injector.get(ChangeDetectorRef);
    cdr.markForCheck();
    fixture.detectChanges();

    const unquarantineButton = fixture.nativeElement.querySelector(
      '[data-testid="quarantine-group-2xl"] .main-action',
    );
    unquarantineButton.click();

    expect(deviceActionService.quarantineDevice).toHaveBeenCalledWith(
      'test-device',
      {
        quarantineInfo: {
          isQuarantined: true,
          expiry: '2025-12-31T23:59:59Z',
        },
      },
    );
  });

  it('should call deviceActionService.changeQuarantine on changeQuarantine', () => {
    component.onChangeQuarantine();

    expect(deviceActionService.changeQuarantine).toHaveBeenCalledWith(
      'test-device',
      '',
    );
  });

  it('should call deviceActionService.configureDevice on onConfiguration', () => {
    component.onConfiguration();

    expect(deviceActionService.configureDevice).toHaveBeenCalledWith(
      'test-device',
      'test-host',
      '1.2.3.4',
      '',
    );
  });
});
