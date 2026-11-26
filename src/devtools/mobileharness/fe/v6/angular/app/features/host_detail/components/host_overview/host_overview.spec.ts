import {TestbedHarnessEnvironment} from '@angular/cdk/testing/testbed';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatCheckboxHarness} from '@angular/material/checkbox/testing';
import {MatDialog, MatDialogRef} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import {BehaviorSubject, Observable, of, Subject, throwError} from 'rxjs';

import {ActionBarAction} from '../../../../core/constants/action_bar_config';
import {DEVICE_VERSION_DESCRIPTION} from '../../../../core/constants/device_version_constants';
import {APP_DATA} from '../../../../core/models/app_data';
import {
  DeviceActions,
  GetLogcatResponse,
  TakeScreenshotResponse,
} from '../../../../core/models/device_action';
import {
  ConfigurableDimension,
  DimensionList,
} from '../../../../core/models/device_config_models';
import {SubDeviceInfo} from '../../../../core/models/device_overview';
import {PreflightLabServerReleaseResponse} from '../../../../core/models/host_action';
import {
  DeviceSummary,
  HostOverview,
} from '../../../../core/models/host_overview';
import {
  CONFIG_SERVICE,
  ConfigService,
} from '../../../../core/services/config/config_service';
import {EnvUniverseService} from '../../../../core/services/env_universe_service';
import {FeatureEnablementService} from '../../../../core/services/feature_enablement_service';
import {FakeHostService} from '../../../../core/services/host/fake_host_service';
import {HOST_SERVICE} from '../../../../core/services/host/host_service';
import {ConfirmDialog} from '../../../../shared/components/confirm_dialog/confirm_dialog';
import {DeviceErrorDialog} from '../../../../shared/components/device_error_dialog/device_error_dialog';
import {ComingSoonService} from '../../../../shared/services/coming_soon_service';
import {DeviceActionService} from '../../../../shared/services/device_action_service';
import {RemoteControlService} from '../../../../shared/services/remote_control_service';
import {SnackBarService} from '../../../../shared/services/snackbar_service';
import {BulkConfigDimensionDialog} from '../../../search_page/components/bulk_config/dimension_dialog/bulk_config_dimension_dialog';
import {BulkConfigWifiDialog} from '../../../search_page/components/bulk_config/wifi_dialog/bulk_config_wifi_dialog';
import {HostOverviewPage} from './host_overview';
import {LabServerActionService} from './lab_server_action_service';
import {ReleaseDialog} from './release_dialog/release_dialog';

describe('HostOverview Component', () => {
  let mockHost: HostOverview;
  let fixture: ComponentFixture<HostOverviewPage>;
  let component: HostOverviewPage;
  let dialogSpy: jasmine.SpyObj<MatDialog>;
  let snackBarSpy: jasmine.SpyObj<SnackBarService>;
  let comingSoonServiceSpy: jasmine.SpyObj<ComingSoonService>;
  let configServiceSpy: jasmine.SpyObj<ConfigService>;
  let featureEnablementSpy: jasmine.SpyObj<FeatureEnablementService>;
  let hostService: FakeHostService;

  beforeEach(async () => {
    mockHost = {
      hostName: 'host-a-1.prod.example.com',
      ip: '192.168.1.101',
      labTypeDisplayNames: ['Core Lab'],
      uiLabTypes: ['CORE'],
      labType: 'Core',
      deviceManagerType: 'MH',
      isAte: false,
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
    dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);
    snackBarSpy = jasmine.createSpyObj('SnackBarService', [
      'showError',
      'showSuccess',
      'showWarning',
    ]);
    comingSoonServiceSpy = jasmine.createSpyObj('ComingSoonService', [
      'show',
      'showForHost',
      'showForDevice',
    ]);
    configServiceSpy = jasmine.createSpyObj('ConfigService', [
      'getConfigurableDimensions',
    ]);
    configServiceSpy.getConfigurableDimensions.and.returnValue(
      of({
        enabled: true,
        dimensions: [
          {
            dimensionName: 'pool',
            dimensionList: DimensionList.DIMENSION_LIST_REQUIRED,
          },
        ],
        allowCustomDimensions: true,
      }),
    );
    featureEnablementSpy = jasmine.createSpyObj('FeatureEnablementService', [
      'isBulkWifiConfigEnabled',
      'isBulkDimensionConfigEnabled',
      'isDeviceConfigEnabled',
    ]);
    featureEnablementSpy.isBulkWifiConfigEnabled.and.returnValue(false);
    featureEnablementSpy.isBulkDimensionConfigEnabled.and.returnValue(false);
    featureEnablementSpy.isDeviceConfigEnabled.and.returnValue(true);

    await TestBed.configureTestingModule({
      imports: [HostOverviewPage, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        {provide: APP_DATA, useValue: {applicationId: 'arsenal'}},
        {provide: HOST_SERVICE, useClass: FakeHostService},
        {provide: CONFIG_SERVICE, useValue: configServiceSpy},
        {
          provide: FeatureEnablementService,
          useValue: featureEnablementSpy,
        },
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
          providers: [
            LabServerActionService,
            {provide: MatDialog, useValue: dialogSpy},
          ],
        },
      })
      .compileComponents();

    fixture = TestBed.createComponent(HostOverviewPage);
    component = fixture.componentInstance;
    // For Angular Signal inputs, we must use `fixture.componentRef.setInput`
    // to trigger reactivity and update the signal value in tests.
    // Calling `component.host = ...` directly is not allowed as Signal inputs are read-only getters.
    fixture.componentRef.setInput('host', mockHost);
    hostService = TestBed.inject(HOST_SERVICE) as FakeHostService;
  });

  it('should be created', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should display single lab type and device manager type', () => {
    fixture.componentRef.setInput('host', {
      ...mockHost,
      labType: 'SLaaS',
      deviceManagerType: 'Fusion',
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const chips = compiled.querySelectorAll('.lab-type-wrapper .lab-type-chip');
    if (component.isGoogle1p) {
      expect(chips.length).toBe(2);
      expect(chips[0].textContent?.trim()).toBe('SLaaS');
      expect(chips[1].textContent?.trim()).toBe('Fusion');
      expect(chips[1].classList.contains('lab-type-chip-fusion')).toBeTrue();
    } else {
      expect(chips.length).toBe(0);
    }
  });

  it('should display Is ATE as Yes or No', () => {
    for (const [isAte, expected] of [
      [true, 'Yes'],
      [false, 'No'],
    ] as const) {
      fixture.componentRef.setInput('host', {...mockHost, isAte});
      fixture.detectChanges();

      const value = (fixture.nativeElement as HTMLElement).querySelector(
        '.is-ate-value',
      );
      if (component.isGoogle1p) {
        expect(value?.textContent?.trim()).toBe(expected);
      } else {
        expect(value).toBeNull();
      }
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
        hostName: component.host().hostName,
        releaseConfigs: mockVersions,
        passThroughFlags: component.passThroughFlags,
        preSelectLatest: true,
        preSelectCurrent: false,
      },
      autoFocus: false,
    });
  });

  it('should show coming soon popup on onUpgrade click when release action is not ready', () => {
    fixture.componentRef.setInput('host', {
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
        },
      },
    });
    fixture.detectChanges();

    component.onUpgrade();

    expect(comingSoonServiceSpy.showForHost).toHaveBeenCalledWith(
      ActionBarAction.HOST_RELEASE,
      component.legacyFeUrl,
      component.host().hostName,
      component.host().ip,
      'hostDevices',
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
        hostName: component.host().hostName,
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

  it('onStart should trigger preflight, and open ConfirmDialog if ready', () => {
    spyOn(hostService, 'preflightLabServerLifecycle').and.returnValue(
      of({ready: {actualActivity: 'RUNNING'}}),
    );

    component.onStart();

    expect(hostService.preflightLabServerLifecycle).toHaveBeenCalledWith(
      mockHost.hostName,
      'START',
    );
    expect(dialogSpy.open).toHaveBeenCalledWith(
      ConfirmDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          title: 'Start Server?',
          primaryButtonLabel: 'Start',
        }),
      }),
    );
  });

  it('onStart should show No Access dialog when preflight returns permissionDenied', () => {
    spyOn(hostService, 'preflightLabServerLifecycle').and.returnValue(
      of({permissionDenied: {}}),
    );

    component.onStart();

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
      component.host().hostName,
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
    expect(
      (component as unknown as {localHost(): HostOverview}).localHost()
        .labServer.passThroughFlags,
    ).toBe('--new-flags');

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
      component.host().hostName,
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

    expect(deviceActionService.takeScreenshot).toHaveBeenCalledWith(
      'device-1',
      component.host().hostName,
    );
  });
  it('getLogcat should delegate to deviceActions', () => {
    const mockDevice = {id: 'device-1'} as unknown as DeviceSummary;
    const deviceActionService = TestBed.inject(
      DeviceActionService,
    ) as jasmine.SpyObj<DeviceActionService>;
    deviceActionService.getLogcat.and.returnValue(of({} as GetLogcatResponse));

    component.getLogcat(mockDevice);

    expect(deviceActionService.getLogcat).toHaveBeenCalledWith(
      'device-1',
      component.host().hostName,
    );
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
      component.host().hostName,
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

  it('getAction should return correct action state', () => {
    const mockActions = {
      flash: {
        state: {
          visible: true,
          enabled: true,
          isReady: true,
          tooltip: 'Flash IT',
        },
        params: {},
      },
      screenshot: {
        visible: true,
        enabled: true,
        isReady: true,
        tooltip: 'Screenshot IT',
      },
    } as unknown as DeviceActions;

    // Test 'flash' specially
    expect(component.getAction(mockActions, 'flash')).toEqual({
      visible: true,
      enabled: true,
      isReady: true,
      tooltip: 'Flash IT',
    });

    // Test other actions
    expect(component.getAction(mockActions, 'screenshot')).toEqual({
      visible: true,
      enabled: true,
      isReady: true,
      tooltip: 'Screenshot IT',
    });
  });

  it('getAction should hide the device Configuration action when it is not enabled', () => {
    const configuration = {
      visible: true,
      enabled: true,
      isReady: true,
      tooltip: 'Configuration',
    };
    const mockActions = {configuration} as unknown as DeviceActions;

    expect(component.getAction(mockActions, 'configuration')).toEqual(
      configuration,
    );
    expect(component.getVisibleDeviceActions(mockActions)).toEqual([
      'configuration',
    ]);

    featureEnablementSpy.isDeviceConfigEnabled.and.returnValue(false);
    expect(component.getAction(mockActions, 'configuration')).toBeUndefined();
    expect(component.getVisibleDeviceActions(mockActions)).toEqual([]);
  });

  it('onStop should trigger stop action', () => {
    spyOn(hostService, 'preflightLabServerLifecycle').and.returnValue(
      of({ready: {actualActivity: 'RUNNING'}}),
    );
    component.onStop();
    expect(hostService.preflightLabServerLifecycle).toHaveBeenCalledWith(
      mockHost.hostName,
      'STOP',
    );
  });

  it('onRestart should trigger preflight, and open ConfirmDialog if ready', () => {
    spyOn(hostService, 'preflightLabServerLifecycle').and.returnValue(
      of({ready: {actualActivity: 'RUNNING'}}),
    );

    component.onRestart();

    expect(hostService.preflightLabServerLifecycle).toHaveBeenCalledWith(
      mockHost.hostName,
      'RESTART',
    );
    expect(dialogSpy.open).toHaveBeenCalledWith(
      ConfirmDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          title: 'Restart Server?',
          primaryButtonLabel: 'Restart',
        }),
      }),
    );
  });

  it('handlePreflightLifecycle should open ReleaseDialog when versionIssue is present', () => {
    const mockVersions = [
      {
        version: '2.0.0',
        name: 'v2',
        status: 'LATEST' as const,
        buildTime: '2026-05-14',
      },
    ];
    spyOn(hostService, 'preflightLabServerLifecycle').and.returnValue(
      of({
        versionIssue: {
          type: 'CURRENT_VERSION_UNAVAILABLE',
          currentVersion: 'v1.0.0',
          availableVersions: mockVersions,
        },
      }),
    );

    component.onStart();

    expect(dialogSpy.open).toHaveBeenCalledWith(ReleaseDialog, {
      data: {
        hostName: component.host().hostName,
        releaseConfigs: mockVersions,
        passThroughFlags: jasmine.anything(),
      },
      panelClass: 'release-dialog-panel',
      disableClose: true,
    });
    expect(snackBarSpy.showWarning).toHaveBeenCalledWith(
      'The current version is no longer supported or recognized: CURRENT_VERSION_UNAVAILABLE',
    );
  });

  it('onAction should handle restart action', () => {
    spyOn(component, 'onRestart');
    component.onLabServerAction('restart');
    expect(component.onRestart).toHaveBeenCalled();
  });

  it('onAction should handle stop action', () => {
    spyOn(component, 'onStop');
    component.onLabServerAction('stop');
    expect(component.onStop).toHaveBeenCalled();
  });

  it('toggleRow should set expandedElement to element if not already expanded, or null if it is', () => {
    const mockDevice = {id: 'device-1'} as unknown as DeviceSummary;
    expect(component.expandedElement()).toBeNull();

    component.toggleRow(mockDevice);
    expect(component.expandedElement()).toBe(mockDevice);

    component.toggleRow(mockDevice);
    expect(component.expandedElement()).toBeNull();
  });

  it('defaults deviceDataSource.data to empty array when getHostDeviceSummaries omits deviceSummaries', () => {
    spyOn(hostService, 'getHostDeviceSummaries').and.returnValue(
      of({} as {deviceSummaries: DeviceSummary[]}),
    );

    component.loadDevices();

    expect(component.deviceDataSource.data).toEqual([]);
    expect(component.isDeviceLoading()).toBeFalse();
  });

  it('clears device selection when device filter changes or devices are reloaded', () => {
    const mockDevice = {
      id: 'device-1',
      deviceStatus: {status: 'IDLE', isCritical: false},
    } as unknown as DeviceSummary;
    component.deviceDataSource.data = [mockDevice];
    component.selection.select(mockDevice);
    expect(component.selection.selected.length).toBe(1);

    // Applying the same empty filter preserves selection
    component.deviceFilterInput = '   ';
    component.applyDeviceFilter();
    expect(component.selection.selected.length).toBe(1);

    // Changing the filter value clears selection
    component.deviceFilterInput = 'pixel';
    component.applyDeviceFilter();
    expect(component.selection.selected.length).toBe(0);

    // Clearing the filter when active clears selection
    component.selection.select(mockDevice);
    expect(component.selection.selected.length).toBe(1);
    component.clearDeviceFilter();
    expect(component.selection.selected.length).toBe(0);

    // Reloading devices clears selection
    component.selection.select(mockDevice);
    expect(component.selection.selected.length).toBe(1);
    spyOn(hostService, 'getHostDeviceSummaries').and.returnValue(
      of({deviceSummaries: [mockDevice]}),
    );
    component.loadDevices();
    expect(component.selection.selected.length).toBe(0);
  });

  it('renders Title Case headers and version-info icons with DEVICE_VERSION_DESCRIPTION in device and sub-device tables', () => {
    const testbedDevice = {
      id: 'testbed-1',
      healthState: {
        health: 'IN_SERVICE_IDLE',
        title: 'Idle',
        tooltip: 'Healthy',
      },
      types: [{type: 'TestbedDevice', isAbnormal: false}],
      deviceStatus: {status: 'IDLE', isCritical: false},
      label: '',
      requiredDims: '',
      model: 'Pixel Testbed',
      version: '16',
      subDevices: [
        {
          id: 'sub-1',
          types: [{type: 'AndroidRealDevice', isAbnormal: false}],
          model: 'Pixel 9',
          version: '16',
        },
      ],
    } as unknown as DeviceSummary;

    fixture.detectChanges();
    component.isDeviceLoading.set(false);
    component.deviceDataSource.data = [testbedDevice];
    component.expandedElement.set(testbedDevice);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const mainHeaderCells = Array.from<HTMLElement>(
      compiled.querySelectorAll(
        '.device-table-container > table > thead th.mat-mdc-header-cell',
      ),
    );
    const mainHeaders = mainHeaderCells.map(
      (el) => el.textContent?.trim() ?? '',
    );
    expect(mainHeaders).toContain('ID');
    expect(mainHeaders).toContain('Health');
    expect(mainHeaders).toContain('Type');
    expect(mainHeaders).toContain('Status');
    expect(mainHeaders).toContain('Label');
    expect(mainHeaders).toContain('Required Dims');
    expect(mainHeaders).toContain('Model');
    for (const cell of mainHeaderCells) {
      expect(getComputedStyle(cell).textTransform).toBe('none');
    }

    const subHeaderCells = Array.from<HTMLElement>(
      compiled.querySelectorAll('.sub-device-table thead th'),
    );
    const subHeaders = subHeaderCells.map((el) => el.textContent?.trim() ?? '');
    expect(subHeaders).toContain('Sub-Device ID');
    expect(subHeaders).toContain('Model');
    expect(subHeaders).toContain('Type');
    expect(subHeaders).toContain('Battery');
    expect(subHeaders).toContain('Wi-Fi');
    expect(subHeaders).toContain('Action');
    for (const cell of subHeaderCells) {
      expect(getComputedStyle(cell).textTransform).toBe('none');
    }

    const versionHeaders = Array.from(
      compiled.querySelectorAll('.device-table-container th .header-with-info'),
      (el) => el.childNodes[0]?.textContent?.trim() ?? '',
    );
    expect(versionHeaders).toEqual(['Version', 'Version']);

    const versionIcons = compiled.querySelectorAll(
      '.device-table-container th .version-info',
    );
    expect(versionIcons.length).toBe(2);
    expect(versionIcons[0].getAttribute('aria-label')).toBe(
      DEVICE_VERSION_DESCRIPTION,
    );
    expect(versionIcons[1].getAttribute('aria-label')).toBe(
      DEVICE_VERSION_DESCRIPTION,
    );

    const emptySubDevicesRow = {...testbedDevice, subDevices: []};
    component.deviceDataSource.data = [emptySubDevicesRow];
    component.expandedElement.set(emptySubDevicesRow);
    fixture.detectChanges();
    expect(compiled.querySelector('.sub-device-table')).toBeNull();
  });

  describe('lab server upgrade affordances', () => {
    /**
     * Re-creates the component under a spied `isGoogleInternal()` and renders
     * it with `host`. The component reads `isGoogleInternal()` only once at
     * construction, so the spy must be set up before the component is created.
     */
    function recreateComponent({
      isGoogleInternal,
      host,
    }: {
      isGoogleInternal: boolean;
      host: HostOverview;
    }) {
      spyOn(
        TestBed.inject(EnvUniverseService),
        'isGoogleInternal',
      ).and.returnValue(isGoogleInternal);
      fixture = TestBed.createComponent(HostOverviewPage);
      component = fixture.componentInstance;
      fixture.componentRef.setInput('host', host);
      fixture.detectChanges();
    }

    it('renders enabled upgrade buttons in Google internal when canUpgrade is true', () => {
      recreateComponent({
        isGoogleInternal: true,
        host: {...mockHost, canUpgrade: true},
      });

      const compiled = fixture.nativeElement as HTMLElement;
      const upgradeButton = compiled.querySelector<HTMLButtonElement>(
        '#lab-server .upgrade-version-button',
      );
      const updateAvailableButton = compiled.querySelector<HTMLButtonElement>(
        '#lab-server .m3-chip-suggestion',
      );
      expect(upgradeButton).not.toBeNull();
      expect(upgradeButton?.disabled).toBeFalse();
      expect(updateAvailableButton).not.toBeNull();
      expect(updateAvailableButton?.disabled).toBeFalse();
      expect(updateAvailableButton?.textContent).toContain('Update available');
    });

    it('never renders upgrade buttons in OSS even when canUpgrade is forced on', () => {
      // Mirrors real OSS data (release action hidden and disabled) with
      // canUpgrade forced on client-side, e.g. by force_all_ready=true.
      recreateComponent({
        isGoogleInternal: false,
        host: {
          ...mockHost,
          canUpgrade: true,
          labServer: {
            ...mockHost.labServer,
            actions: {
              release: {
                visible: false,
                enabled: false,
                isReady: true,
                tooltip: '',
              },
              restart: mockHost.labServer.actions!.restart,
              start: mockHost.labServer.actions!.start,
              stop: mockHost.labServer.actions!.stop,
            },
          },
        },
      });

      const compiled = fixture.nativeElement as HTMLElement;
      expect(
        compiled.querySelector('#lab-server .upgrade-version-button'),
      ).toBeNull();
      expect(
        compiled.querySelector('#lab-server .m3-chip-suggestion'),
      ).toBeNull();
    });
  });

  describe('bulk device configuration', () => {
    const mockDevice1 = {
      id: 'device-1',
      healthState: {
        health: 'IN_SERVICE_IDLE',
        title: 'Idle',
        tooltip: 'Healthy',
      },
      deviceStatus: {status: 'IDLE', isCritical: false},
    } as unknown as DeviceSummary;
    const mockDevice2 = {
      id: 'device-2',
      healthState: {
        health: 'IN_SERVICE_IDLE',
        title: 'Idle',
        tooltip: 'Healthy',
      },
      deviceStatus: {status: 'IDLE', isCritical: false},
    } as unknown as DeviceSummary;

    let getHostDeviceSummariesSpy: jasmine.Spy<
      FakeHostService['getHostDeviceSummaries']
    >;

    beforeEach(() => {
      // FakeHostService answers after a delay and has no devices for
      // mockHost. A test that awaits stability would see its devices replaced
      // by that empty list, so the device list is served from this spy.
      getHostDeviceSummariesSpy = spyOn(
        hostService,
        'getHostDeviceSummaries',
      ).and.returnValue(of({deviceSummaries: []}));
    });

    function initComponentWithDevices(devices: DeviceSummary[]) {
      getHostDeviceSummariesSpy.and.returnValue(of({deviceSummaries: devices}));
      fixture = TestBed.createComponent(HostOverviewPage);
      component = fixture.componentInstance;
      fixture.componentRef.setInput('host', mockHost);
      fixture.detectChanges();
      fixture.detectChanges();
    }

    it('hides bulk config actions and skips fetching dimensions when bulk config is disabled', async () => {
      featureEnablementSpy.isBulkWifiConfigEnabled.and.returnValue(false);
      featureEnablementSpy.isBulkDimensionConfigEnabled.and.returnValue(false);
      initComponentWithDevices([mockDevice1]);
      component.selection.select(mockDevice1);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.bulkConfig.dimensionConfig().enabled).toBeFalse();
      expect(configServiceSpy.getConfigurableDimensions).not.toHaveBeenCalled();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).not.toContain('Configure Wi-Fi');
      expect(
        compiled.querySelector(
          'button[aria-label="Configure device dimensions"]',
        ),
      ).toBeNull();

      component.bulkConfig.openWifiDialog();
      component.bulkConfig.openDimensionDialog('supported');
      expect(dialogSpy.open).not.toHaveBeenCalled();
    });

    it('fetches configurable dimensions on selection and opens Wi-Fi and dimension dialogs when enabled', async () => {
      featureEnablementSpy.isBulkWifiConfigEnabled.and.returnValue(true);
      featureEnablementSpy.isBulkDimensionConfigEnabled.and.returnValue(true);
      initComponentWithDevices([mockDevice1, mockDevice2]);
      component.selection.select(mockDevice1, mockDevice2);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(
        configServiceSpy.getConfigurableDimensions,
      ).toHaveBeenCalledTimes(1);
      expect(component.bulkConfig.dimensionConfig().enabled).toBeTrue();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).toContain('Configure Wi-Fi');
      const configureBtn = compiled.querySelector<HTMLButtonElement>(
        'button[aria-label="Configure device dimensions"]',
      );
      expect(configureBtn).toBeTruthy();
      expect(configureBtn!.getAttribute('title')).toBeNull();
      expect(configureBtn!.textContent?.trim()).toBe(
        'tuneConfigurearrow_drop_down',
      );

      spyOn(component, 'loadDevices');
      dialogSpy.open.and.returnValue({
        afterClosed: () => of({updated: true}),
      } as unknown as MatDialogRef<unknown, unknown>);

      component.bulkConfig.openWifiDialog();
      expect(dialogSpy.open).toHaveBeenCalledWith(
        BulkConfigWifiDialog,
        jasmine.objectContaining({
          autoFocus: false,
          data: {
            devices: [
              {deviceId: 'device-1', hostName: mockHost.hostName},
              {deviceId: 'device-2', hostName: mockHost.hostName},
            ],
          },
        }),
      );
      expect(component.selection.isEmpty()).toBeTrue();
      expect(component.loadDevices).toHaveBeenCalledTimes(1);

      // Re-select and test dimension dialog without update
      component.selection.select(mockDevice1);
      fixture.detectChanges();
      dialogSpy.open.calls.reset();
      (component.loadDevices as jasmine.Spy).calls.reset();
      dialogSpy.open.and.returnValue({
        afterClosed: () => of({updated: false}),
      } as unknown as MatDialogRef<unknown, unknown>);

      const curatedDim: ConfigurableDimension = {
        dimensionName: 'pool',
        dimensionList: DimensionList.DIMENSION_LIST_REQUIRED,
      };
      component.bulkConfig.openDimensionDialog(curatedDim);
      expect(dialogSpy.open).toHaveBeenCalledWith(
        BulkConfigDimensionDialog,
        jasmine.objectContaining({
          autoFocus: false,
          data: {
            devices: [{deviceId: 'device-1', hostName: mockHost.hostName}],
            dimension: curatedDim,
          },
        }),
      );
      expect(component.selection.isEmpty()).toBeFalse();
      expect(component.loadDevices).not.toHaveBeenCalled();
    });

    it('hides bulk config actions on Partner OEM universes and supports retry on dimension fetch error', async () => {
      featureEnablementSpy.isBulkWifiConfigEnabled.and.returnValue(true);
      featureEnablementSpy.isBulkDimensionConfigEnabled.and.returnValue(true);
      const envUniverseService = TestBed.inject(EnvUniverseService);
      spyOn(envUniverseService, 'isGoogleInternal').and.returnValue(true);
      const oemSpy = spyOn(envUniverseService, 'isGoogleOEM').and.returnValue(
        true,
      );

      initComponentWithDevices([mockDevice1]);
      component.selection.select(mockDevice1);
      fixture.detectChanges();
      await fixture.whenStable();

      expect(component.bulkConfig.dimensionConfig().enabled).toBeFalse();
      expect(configServiceSpy.getConfigurableDimensions).not.toHaveBeenCalled();

      // Create component on own fleet with an initial RPC error, then retry
      configServiceSpy.getConfigurableDimensions.and.returnValue(
        throwError(() => new Error('RPC timeout')),
      );
      oemSpy.and.returnValue(false);
      initComponentWithDevices([mockDevice1]);
      component.selection.select(mockDevice1);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.bulkConfig.dimensionConfig().error).toBe('RPC timeout');

      configServiceSpy.getConfigurableDimensions.and.returnValue(
        of({
          enabled: true,
          dimensions: [],
          allowCustomDimensions: false,
        }),
      );
      component.bulkConfig.retryDimensions();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.bulkConfig.dimensionConfig().error).toBeNull();
    });

    it('computes decommissionMissingCount and renders Remove Missing button only for selected MISSING devices', async () => {
      const missingDevice1 = {
        ...mockDevice1,
        id: 'missing-1',
        deviceStatus: {status: 'MISSING', isCritical: true},
      } as unknown as DeviceSummary;
      const missingDevice2 = {
        ...mockDevice1,
        id: 'missing-2',
        deviceStatus: {status: 'MISSING', isCritical: true},
      } as unknown as DeviceSummary;
      spyOn(
        TestBed.inject(EnvUniverseService),
        'isGoogleInternal',
      ).and.returnValue(true);
      initComponentWithDevices([mockDevice1, missingDevice1, missingDevice2]);

      const loader = TestbedHarnessEnvironment.loader(fixture);
      const rowCheckboxes = await loader.getAllHarnesses(
        MatCheckboxHarness.with({selector: 'td mat-checkbox'}),
      );
      const compiled = fixture.nativeElement as HTMLElement;

      await rowCheckboxes[0].check();
      fixture.detectChanges();

      expect(component.decommissionMissingCount()).toBe(0);
      expect(compiled.textContent).toContain('1 of 3 selected');
      expect(compiled.textContent).not.toContain('Remove Missing');

      await rowCheckboxes[1].check();
      await rowCheckboxes[2].check();
      fixture.detectChanges();

      expect(component.decommissionMissingCount()).toBe(2);
      expect(compiled.textContent).toContain('Remove Missing (2)');

      await rowCheckboxes[1].uncheck();
      fixture.detectChanges();

      expect(component.decommissionMissingCount()).toBe(1);
      expect(compiled.textContent).toContain('Remove Missing (1)');
    });

    it('opens Remove Missing confirmation dialog with Remove wording for single and multiple devices', () => {
      const missingDevice1 = {
        ...mockDevice1,
        id: 'missing-1',
        deviceStatus: {status: 'MISSING', isCritical: true},
      } as unknown as DeviceSummary;
      const missingDevice2 = {
        ...mockDevice1,
        id: 'missing-2',
        deviceStatus: {status: 'MISSING', isCritical: true},
      } as unknown as DeviceSummary;
      const decommissionSpy = spyOn(
        hostService,
        'decommissionMissingDevices',
      ).and.returnValue(of(undefined));
      initComponentWithDevices([mockDevice1, missingDevice1, missingDevice2]);

      component.decommission(missingDevice1);
      expect(dialogSpy.open).toHaveBeenCalledWith(
        ConfirmDialog,
        jasmine.objectContaining({
          data: jasmine.objectContaining({
            title: 'Remove Missing Device missing-1?',
            primaryButtonLabel: 'Remove',
            secondaryButtonLabel: 'Cancel',
          }),
        }),
      );

      const singleDialogConfig = dialogSpy.open.calls.mostRecent().args[1] as {
        data: {onConfirm: () => Observable<void>};
      };
      singleDialogConfig.data.onConfirm().subscribe();
      expect(decommissionSpy).toHaveBeenCalledWith('host-a-1.prod.example.com', [
        'missing-1',
      ]);
      expect(snackBarSpy.showSuccess).toHaveBeenCalledWith(
        '1 device removed successfully.\nIt may take a few minutes to take effect on the UI side.',
      );

      initComponentWithDevices([mockDevice1, missingDevice1, missingDevice2]);
      component.decommission([missingDevice1, missingDevice2]);
      expect(dialogSpy.open).toHaveBeenCalledWith(
        ConfirmDialog,
        jasmine.objectContaining({
          data: jasmine.objectContaining({
            title: 'Remove 2 Missing Devices?',
            primaryButtonLabel: 'Remove',
            secondaryButtonLabel: 'Cancel',
          }),
        }),
      );

      const multiDialogConfig = dialogSpy.open.calls.mostRecent().args[1] as {
        data: {onConfirm: () => Observable<void>};
      };
      multiDialogConfig.data.onConfirm().subscribe();
      expect(decommissionSpy).toHaveBeenCalledWith('host-a-1.prod.example.com', [
        'missing-1',
        'missing-2',
      ]);
      expect(snackBarSpy.showSuccess).toHaveBeenCalledWith(
        '2 devices removed successfully.\nIt may take a few minutes to take effect on the UI side.',
      );
    });

    it('keeps bulk config disabled when CONFIG_SERVICE is not provided', async () => {
      TestBed.resetTestingModule();
      featureEnablementSpy.isBulkWifiConfigEnabled.and.returnValue(true);
      featureEnablementSpy.isBulkDimensionConfigEnabled.and.returnValue(true);
      await TestBed.configureTestingModule({
        imports: [HostOverviewPage, NoopAnimationsModule],
        providers: [
          provideRouter([]),
          {provide: APP_DATA, useValue: {applicationId: 'arsenal'}},
          {provide: HOST_SERVICE, useValue: hostService},
          {
            provide: FeatureEnablementService,
            useValue: featureEnablementSpy,
          },
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
            providers: [
              LabServerActionService,
              {provide: MatDialog, useValue: dialogSpy},
            ],
          },
        })
        .compileComponents();

      initComponentWithDevices([mockDevice1]);
      component.selection.select(mockDevice1);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.bulkConfig.dimensionConfig().enabled).toBeFalse();
      component.bulkConfig.openWifiDialog();
      component.bulkConfig.openDimensionDialog('supported');
      expect(dialogSpy.open).not.toHaveBeenCalled();
    });

    it('hides the toolbar separator in OSS when bulk config returns enabled=false', async () => {
      featureEnablementSpy.isBulkWifiConfigEnabled.and.returnValue(true);
      featureEnablementSpy.isBulkDimensionConfigEnabled.and.returnValue(true);
      configServiceSpy.getConfigurableDimensions.and.returnValue(
        of({
          enabled: false,
          dimensions: [],
          allowCustomDimensions: false,
        }),
      );
      spyOn(
        TestBed.inject(EnvUniverseService),
        'isGoogleInternal',
      ).and.returnValue(false);

      initComponentWithDevices([mockDevice1]);
      component.selection.select(mockDevice1);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(component.bulkConfig.dimensionConfig().enabled).toBeFalse();
      expect(
        compiled.querySelector('.device-toolbar-content.selected .separator'),
      ).toBeNull();
    });

    it('hides bulk config when navigating via SPA to a Partner ATS universe query param', async () => {
      const queryParamMap$ = new BehaviorSubject(
        convertToParamMap({'universe': 'ats_partner'}),
      );
      const mockRoute = {
        queryParamMap: queryParamMap$.asObservable(),
        snapshot: {queryParamMap: queryParamMap$.value},
      };
      TestBed.resetTestingModule();
      featureEnablementSpy.isBulkWifiConfigEnabled.and.returnValue(true);
      featureEnablementSpy.isBulkDimensionConfigEnabled.and.returnValue(true);
      await TestBed.configureTestingModule({
        imports: [HostOverviewPage, NoopAnimationsModule],
        providers: [
          provideRouter([]),
          {provide: ActivatedRoute, useValue: mockRoute},
          {provide: APP_DATA, useValue: {applicationId: 'arsenal'}},
          {provide: HOST_SERVICE, useValue: hostService},
          {provide: CONFIG_SERVICE, useValue: configServiceSpy},
          {
            provide: FeatureEnablementService,
            useValue: featureEnablementSpy,
          },
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
            providers: [
              LabServerActionService,
              {provide: MatDialog, useValue: dialogSpy},
            ],
          },
        })
        .compileComponents();

      spyOn(
        TestBed.inject(EnvUniverseService),
        'isGoogleInternal',
      ).and.returnValue(true);
      initComponentWithDevices([mockDevice1]);
      component.selection.select(mockDevice1);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.bulkConfig.dimensionConfig().enabled).toBeFalse();
      expect(configServiceSpy.getConfigurableDimensions).not.toHaveBeenCalled();

      // Switching to google_1p enables bulk config
      queryParamMap$.next(convertToParamMap({'universe': 'google_1p'}));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.bulkConfig.dimensionConfig().enabled).toBeTrue();
      expect(configServiceSpy.getConfigurableDimensions).toHaveBeenCalledTimes(
        1,
      );
    });
  });

  it('openDeviceErrorDialog opens DeviceErrorDialog immediately with deviceId and hostName', () => {
    const mockDevice = {id: 'device-err-1'} as unknown as DeviceSummary;
    component.openDeviceErrorDialog(mockDevice);

    expect(dialogSpy.open).toHaveBeenCalledWith(
      DeviceErrorDialog,
      jasmine.objectContaining({
        data: {
          deviceId: 'device-err-1',
          hostName: mockHost.hostName,
        },
        autoFocus: false,
      }),
    );
  });
});
