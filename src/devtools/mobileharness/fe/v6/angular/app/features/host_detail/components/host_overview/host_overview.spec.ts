import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialog, MatDialogRef} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {Observable, of, Subject, throwError} from 'rxjs';

import {ActionBarAction} from '../../../../core/constants/action_bar_config';
import {APP_DATA} from '../../../../core/models/app_data';
import {
  GetLogcatResponse,
  TakeScreenshotResponse,
} from '../../../../core/models/device_action';
import {SubDeviceInfo} from '../../../../core/models/device_overview';
import {PreflightLabServerReleaseResponse} from '../../../../core/models/host_action';
import {
  DeviceSummary,
  HostOverview,
} from '../../../../core/models/host_overview';
import {FakeHostService} from '../../../../core/services/host/fake_host_service';
import {HOST_SERVICE} from '../../../../core/services/host/host_service';
import {ConfirmDialog} from '../../../../shared/components/confirm_dialog/confirm_dialog';
import {ComingSoonService} from '../../../../shared/services/coming_soon_service';
import {DeviceActionService} from '../../../../shared/services/device_action_service';
import {RemoteControlService} from '../../../../shared/services/remote_control_service';
import {SnackBarService} from '../../../../shared/services/snackbar_service';
import {HostOverviewPage} from './host_overview';
import {ReleaseDialog} from './release_dialog/release_dialog';

describe('HostOverview Component', () => {
  const mockHost: HostOverview = {
    hostName: 'host-a-1.prod.example.com',
    ip: '192.168.1.101',
    labTypeDisplayNames: ['Core Lab'],
    labServer: {
      connectivity: {
        state: 'RUNNING',
        title: 'Running',
        tooltip: 'The lab server is running.',
      },
      version: '4.175.0',
      passThroughFlags: '--pass_through_flag_1=value_1',
      actions: {
        release: {visible: true, enabled: true, isReady: true, tooltip: ''},
        restart: {visible: true, enabled: true, isReady: true, tooltip: ''},
        start: {visible: false, enabled: false, isReady: false, tooltip: ''},
        stop: {
          visible: true,
          enabled: false,
          isReady: true,
          tooltip: 'Already stopped',
        },
        advancedOperations: {
          visible: true,
          enabled: true,
          isReady: true,
          tooltip: '',
        },
      },
    },
    daemonServer: {
      status: {
        state: 'RUNNING',
        title: 'Running',
        tooltip: 'The daemon server is running.',
      },
      version: '4.175.0',
    },
    properties: {
      'test-type': 'instrumentation',
      'max-run-time': '3600',
      'network-requirement': 'full',
      'encryption-state': 'encrypted',
    },
    os: 'gLinux',
    diagnosticLinks: [],
    canUpgrade: false,
    showPassThroughFlags: true,
  };

  let fixture: ComponentFixture<HostOverviewPage>;
  let component: HostOverviewPage;
  let dialogSpy: jasmine.SpyObj<MatDialog>;
  let snackBarSpy: jasmine.SpyObj<SnackBarService>;
  let comingSoonServiceSpy: jasmine.SpyObj<ComingSoonService>;
  let hostService: FakeHostService;

  beforeEach(async () => {
    dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);
    snackBarSpy = jasmine.createSpyObj('SnackBarService', [
      'showError',
      'showSuccess',
    ]);
    comingSoonServiceSpy = jasmine.createSpyObj('ComingSoonService', ['show']);

    await TestBed.configureTestingModule({
      imports: [HostOverviewPage, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        {provide: APP_DATA, useValue: {applicationId: 'arsenal'}},
        {provide: HOST_SERVICE, useClass: FakeHostService},
        {
          provide: RemoteControlService,
          useValue: jasmine.createSpyObj('RemoteControlService', [
            'startRemoteControl',
          ]),
        },
        {provide: SnackBarService, useValue: snackBarSpy},
        {provide: ComingSoonService, useValue: comingSoonServiceSpy},
        {
          provide: DeviceActionService,
          useValue: jasmine.createSpyObj('DeviceActionService', [
            'takeScreenshot',
            'getLogcat',
            'flashDevice',
            'quarantineDevice',
          ]),
        },
      ],
    })
      .overrideComponent(HostOverviewPage, {
        set: {
          providers: [{provide: MatDialog, useValue: dialogSpy}],
        },
      })
      .compileComponents();

    fixture = TestBed.createComponent(HostOverviewPage);
    component = fixture.componentInstance;
    component.host = mockHost;
    hostService = TestBed.inject(HOST_SERVICE) as FakeHostService;
  });

  it('should be created', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should display all lab types', () => {
    component.host = {
      ...mockHost,
      labTypeDisplayNames: ['Lab1', 'Lab2', 'Lab3', 'Lab4', 'Lab5'],
    };
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const chips = compiled.querySelectorAll('.lab-type-wrapper .lab-type-chip');
    if (component.isGoogle1p) {
      expect(chips.length).toBe(5);
      expect(chips[0].textContent?.trim()).toBe('Lab1');
      expect(chips[1].textContent?.trim()).toBe('Lab2');
      expect(chips[2].textContent?.trim()).toBe('Lab3');
      expect(chips[3].textContent?.trim()).toBe('Lab4');
      expect(chips[4].textContent?.trim()).toBe('Lab5');
    } else {
      expect(chips.length).toBe(0);
    }
  });

  it('should display all lab types from uiLabTypes', () => {
    component.host = {
      ...mockHost,
      uiLabTypes: ['SATELLITE', 'SLAAS'],
    };
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const chips = compiled.querySelectorAll('.lab-type-wrapper .lab-type-chip');
    if (component.isGoogle1p) {
      expect(chips.length).toBe(2);
      expect(chips[0].textContent?.trim()).toBe('Satellite');
      expect(chips[1].textContent?.trim()).toBe('SLaaS');
    } else {
      expect(chips.length).toBe(0);
    }
  });

  it('should display visible lab server actions', () => {
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const actionTexts = Array.from(
      compiled.querySelectorAll(
        '#lab-server .operation-button .operation-text',
      ),
      (el) => el.textContent?.trim().toLowerCase() ?? '',
    );

    expect(actionTexts).toContain('release');
    expect(actionTexts).toContain('restart');
    expect(actionTexts).toContain('stop');
    expect(actionTexts).not.toContain('start');

    const buttons = compiled.querySelectorAll(
      '#lab-server .operation-button button',
    );
    const stopButton = Array.from(buttons).find((b) =>
      b.textContent?.trim().toLowerCase().includes('stop'),
    ) as HTMLButtonElement;
    expect(stopButton.disabled).toBeTrue();
  });

  it('should handle onUpgrade click, show upgrade spinner, and open ReleaseDialog with preSelectLatest=true', () => {
    const subject = new Subject<PreflightLabServerReleaseResponse>();
    spyOn(hostService, 'preflightLabServerRelease').and.returnValue(subject);

    dialogSpy.open.and.returnValue({
      afterClosed: () => of('close'),
    } as unknown as MatDialogRef<unknown, unknown>);

    component.onUpgrade();

    expect(component.isOpeningUpgrade()).toBeTrue();
    expect(component.isOpeningRelease()).toBeFalse();

    // Emit versions to trigger dialog open
    const mockVersions = [
      {
        version: '2.0.0',
        name: 'v2',
        status: 'LATEST' as const,
        buildTime: '2026-05-14',
      },
    ];
    subject.next({ready: {versions: mockVersions}});
    subject.complete();

    expect(component.isOpeningUpgrade()).toBeFalse();
    expect(component.isOpeningRelease()).toBeFalse();
    expect(dialogSpy.open).toHaveBeenCalledWith(ReleaseDialog, {
      data: {
        hostName: component.host.hostName,
        releaseConfigs: mockVersions,
        passThroughFlags: component.passThroughFlags,
        preSelectLatest: true,
        preSelectCurrent: false,
      },
      autoFocus: false,
    });
  });

  it('should show coming soon popup on onUpgrade click when release action is not ready', () => {
    component.host = {
      ...mockHost,
      labServer: {
        ...mockHost.labServer,
        actions: {
          release: {
            visible: true,
            enabled: true,
            isReady: false,
            tooltip: '',
          },
          restart: mockHost.labServer.actions!.restart,
          start: mockHost.labServer.actions!.start,
          stop: mockHost.labServer.actions!.stop,
          advancedOperations: mockHost.labServer.actions!.advancedOperations,
        },
      },
    };
    fixture.detectChanges();

    component.onUpgrade();

    const expectedUrl = component.legacyFeUrl
      ? `${component.legacyFeUrl}/labdetailview/${component.host.hostName}/${component.host.ip}`
      : undefined;

    expect(comingSoonServiceSpy.show).toHaveBeenCalledWith(
      ActionBarAction.HOST_RELEASE,
      'hostDevices',
      expectedUrl,
    );
  });

  it('should handle onRelease click, show release spinner, and open ReleaseDialog with preSelectLatest=false', () => {
    const subject = new Subject<PreflightLabServerReleaseResponse>();
    spyOn(hostService, 'preflightLabServerRelease').and.returnValue(subject);

    dialogSpy.open.and.returnValue({
      afterClosed: () => of('close'),
    } as unknown as MatDialogRef<unknown, unknown>);

    component.onRelease({preSelectLatest: false});

    expect(component.isOpeningRelease()).toBeTrue();
    expect(component.isOpeningUpgrade()).toBeFalse();

    // Emit versions to trigger dialog open
    const mockVersions = [
      {
        version: '2.0.0',
        name: 'v2',
        status: 'LATEST' as const,
        buildTime: '2026-05-14',
      },
    ];
    subject.next({ready: {versions: mockVersions}});
    subject.complete();

    expect(component.isOpeningRelease()).toBeFalse();
    expect(component.isOpeningUpgrade()).toBeFalse();
    expect(dialogSpy.open).toHaveBeenCalledWith(ReleaseDialog, {
      data: {
        hostName: component.host.hostName,
        releaseConfigs: mockVersions,
        passThroughFlags: component.passThroughFlags,
        preSelectLatest: false,
        preSelectCurrent: false,
      },
      autoFocus: false,
    });
  });

  it('should reset loading states and trigger alert on preflight spec error', () => {
    spyOn(hostService, 'preflightLabServerRelease').and.returnValue(
      throwError(() => new Error('Failed speculation')),
    );

    component.onRelease({preSelectLatest: true}); // under upgrade loading context

    expect(component.isOpeningUpgrade()).toBeFalse();
    expect(component.isOpeningRelease()).toBeFalse();
    expect(snackBarSpy.showError).toHaveBeenCalledWith(
      'Failed to load release info: Failed speculation',
    );
  });

  it('onStart should trigger startLabServer, show success snackbar on success', () => {
    spyOn(hostService, 'startLabServer').and.returnValue(
      of({trackingUrl: 'http://start-url'}),
    );

    component.onStart();

    expect(hostService.startLabServer).toHaveBeenCalledWith(mockHost.hostName);
    expect(snackBarSpy.showSuccess).toHaveBeenCalledWith(
      'Lab Server starting...',
    );
  });

  it('onStart should show error snackbar on startLabServer error', () => {
    spyOn(hostService, 'startLabServer').and.returnValue(
      throwError(() => new Error('Start failed')),
    );

    component.onStart();

    expect(snackBarSpy.showError).toHaveBeenCalledWith(
      'Failed to start: Start failed',
    );
  });

  it('should show release confirm dialog and handle confirm', () => {
    const subject = new Subject<PreflightLabServerReleaseResponse>();
    spyOn(hostService, 'preflightLabServerRelease').and.returnValue(subject);

    component.showReleaseConfirmDialog();

    // Verify ConfirmDialog was opened
    expect(dialogSpy.open).toHaveBeenCalledWith(
      ConfirmDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          title: 'Flags Updated',
        }),
      }),
    );

    // Get the dialog config passed to open
    const dialogArgs = dialogSpy.open.calls.mostRecent().args;
    const dialogData = dialogArgs[1]?.data as
      | {onConfirm?: () => Observable<void>}
      | undefined;
    expect(dialogData?.onConfirm).toBeDefined();

    // Trigger onConfirm
    dialogData!.onConfirm!().subscribe({
      error: () => {},
    });

    // Verify preflight check was triggered
    expect(hostService.preflightLabServerRelease).toHaveBeenCalledWith(
      component.host.hostName,
    );
    expect(component.isOpeningRelease()).toBeTrue();
  });

  it('should show No Access dialog when preflight returns permissionDenied', () => {
    const subject = new Subject<PreflightLabServerReleaseResponse>();
    spyOn(hostService, 'preflightLabServerRelease').and.returnValue(subject);

    component.onRelease();

    // Emit permissionDenied
    subject.next({permissionDenied: {}});
    subject.complete();

    // Verify ConfirmDialog was opened with "No Access"
    expect(dialogSpy.open).toHaveBeenCalledWith(
      ConfirmDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          title: 'No Access',
          type: 'error',
        }),
      }),
    );
  });

  it('onDeploy should not show popup due to missing mapping', () => {
    component.onDeploy();

    expect(comingSoonServiceSpy.show).not.toHaveBeenCalled();
  });

  it('openFlagsDialog should open FlagsDialog and handle closed with valid flags', () => {
    const flagsDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    flagsDialogRefSpy.afterClosed.and.returnValue(of('--new-flags'));

    const restartDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    restartDialogRefSpy.afterClosed.and.returnValue(of(undefined));

    dialogSpy.open.and.returnValues(flagsDialogRefSpy, restartDialogRefSpy);

    spyOn(component, 'preflightAndOpenRelease').and.returnValue(of(undefined));

    component.openFlagsDialog();

    expect(dialogSpy.open).toHaveBeenCalledTimes(2);
    expect(component.passThroughFlags()).toBe('--new-flags');
    expect(component.host.labServer.passThroughFlags).toBe('--new-flags');

    const secondOpenCall = dialogSpy.open.calls.mostRecent();
    expect(secondOpenCall).toBeTruthy();
    expect(secondOpenCall!.args[0]).toBe(ConfirmDialog);
    expect(secondOpenCall!.args[1]).toBeTruthy();
    const dialogConfig = secondOpenCall!.args[1] as {
      data: {
        primaryButtonLabel: string;
        onConfirm: () => void;
      };
    };
    expect(dialogConfig.data).toBeTruthy();
    const dialogData = dialogConfig.data;
    expect(dialogData.primaryButtonLabel).toBe('Release Now');

    dialogData.onConfirm();
    expect(component.preflightAndOpenRelease).toHaveBeenCalledWith();
  });

  it('startRemoteControl should delegate to deviceActions', () => {
    const mockDevices: DeviceSummary[] = [
      {
        id: 'device-1',
        model: 'Pixel 9',
        types: [],
        subDevices: [],
      } as unknown as DeviceSummary,
    ];
    const remoteControlService = TestBed.inject(
      RemoteControlService,
    ) as jasmine.SpyObj<RemoteControlService>;
    remoteControlService.startRemoteControl.and.returnValue(of(undefined));

    component.startRemoteControl(mockDevices);

    expect(remoteControlService.startRemoteControl).toHaveBeenCalled();
  });

  it('startSubDeviceRemoteControl should delegate to deviceActions with sub-device options', () => {
    const mockParent: DeviceSummary = {
      id: 'parent-1',
      model: 'Pixel 9',
      types: [],
      subDevices: [],
    } as unknown as DeviceSummary;
    const mockSub: SubDeviceInfo = {
      id: 'sub-1',
      model: 'Pixel 8',
    } as unknown as SubDeviceInfo;
    const remoteControlService = TestBed.inject(
      RemoteControlService,
    ) as jasmine.SpyObj<RemoteControlService>;
    remoteControlService.startRemoteControl.and.returnValue(of(undefined));

    component.startSubDeviceRemoteControl(mockSub, mockParent);

    expect(remoteControlService.startRemoteControl).toHaveBeenCalledWith(
      component.host.hostName,
      jasmine.any(Array),
      true,
    );
  });

  it('takeScreenshot should delegate to deviceActions', () => {
    const mockDevice = {id: 'device-1'} as unknown as DeviceSummary;
    const deviceActionService = TestBed.inject(
      DeviceActionService,
    ) as jasmine.SpyObj<DeviceActionService>;
    deviceActionService.takeScreenshot.and.returnValue(
      of({} as TakeScreenshotResponse),
    );

    component.takeScreenshot(mockDevice);

    expect(deviceActionService.takeScreenshot).toHaveBeenCalledWith('device-1');
  });
  it('getLogcat should delegate to deviceActions', () => {
    const mockDevice = {id: 'device-1'} as unknown as DeviceSummary;
    const deviceActionService = TestBed.inject(
      DeviceActionService,
    ) as jasmine.SpyObj<DeviceActionService>;
    deviceActionService.getLogcat.and.returnValue(of({} as GetLogcatResponse));

    component.getLogcat(mockDevice);

    expect(deviceActionService.getLogcat).toHaveBeenCalledWith('device-1');
  });

  it('flashDevice should delegate to deviceActions', () => {
    const mockDevice = {
      id: 'device-1',
      actions: {
        flash: {params: {deviceType: 'Pixel'}},
      },
    } as unknown as DeviceSummary;
    const deviceActionService = TestBed.inject(
      DeviceActionService,
    ) as jasmine.SpyObj<DeviceActionService>;

    component.flashDevice(mockDevice);

    expect(deviceActionService.flashDevice).toHaveBeenCalledWith(
      'device-1',
      component.host.hostName,
      jasmine.objectContaining({deviceType: 'Pixel'}),
    );
  });

  it('quarantineDevice should delegate to deviceActions and reload devices on success', () => {
    const mockDevice = {id: 'device-1'} as unknown as DeviceSummary;
    const quarantineSpy = jasmine.createSpy('quarantineDevice');
    (component as unknown as {deviceActions: unknown}).deviceActions = {
      quarantineDevice: quarantineSpy,
    };

    spyOn(component, 'loadDevices');

    component.quarantineDevice(mockDevice);

    expect(quarantineSpy).toHaveBeenCalledWith(
      'device-1',
      jasmine.objectContaining({onSuccess: jasmine.any(Function)}),
    );

    const mostRecentCall = quarantineSpy.calls.mostRecent();
    expect(mostRecentCall).toBeTruthy();
    const options = mostRecentCall!.args[1] as {onSuccess: () => void};
    expect(options.onSuccess).toBeTruthy();

    options.onSuccess();

    expect(component.loadDevices).toHaveBeenCalled();
  });
});
