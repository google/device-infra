import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA, MatDialog, MatDialogRef} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of} from 'rxjs';

import {DeployableVersion} from '@deviceinfra/app/core/models/host_action';
import {FakeHostService} from '@deviceinfra/app/core/services/host/fake_host_service';
import {HOST_SERVICE} from '@deviceinfra/app/core/services/host/host_service';
import {TrackingDialog} from '@deviceinfra/app/features/host_detail/components/host_overview/tracking_dialog/tracking_dialog';
import {FlagsDialog} from '@deviceinfra/app/features/host_detail/components/host_overview/flags_dialog/flags_dialog';
import {SnackBarService} from '@deviceinfra/app/shared/services/snackbar_service';
import {ReleaseDialog, ReleaseDialogData} from './release_dialog';

describe('ReleaseDialog', () => {
  let component: ReleaseDialog;
  let fixture: ComponentFixture<ReleaseDialog>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;

  const dialogData: ReleaseDialogData = {
    hostName: 'test-host',
    passThroughFlags: signal(''),
  };

  beforeEach(async () => {
    dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      imports: [ReleaseDialog, NoopAnimationsModule],
      providers: [
        {provide: MAT_DIALOG_DATA, useValue: dialogData},
        {provide: HOST_SERVICE, useClass: FakeHostService},
        {
          provide: SnackBarService,
          useValue: jasmine.createSpyObj('SnackBarService', [
            'showSuccess',
            'showError',
          ]),
        },
        {provide: MatDialog, useValue: dialogSpy},
      ],
    })
      .overrideComponent(ReleaseDialog, {
        set: {
          providers: [{provide: MatDialog, useValue: dialogSpy}],
        },
      })
      .compileComponents();

    fixture = TestBed.createComponent(ReleaseDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should format available versions with v prefix', () => {
    const versions: DeployableVersion[] = [
      {version: '1.0.0', name: 'v1', status: 'LATEST', buildTime: '2026-05-14'},
    ];
    component.availableVersions.set(versions);

    const formatted = component.formattedAvailableVersions();
    expect(formatted.length).toEqual(1);
    expect(formatted[0].version).toEqual('v1.0.0');
  });

  it('should handle version segments of different lengths', () => {
    component.selectedVersion.set({
      version: 'v1.1',
      name: 'v1',
      status: 'LATEST',
      buildTime: '2026-05-14',
    });
    component.availableVersions.set([
      {
        version: '1.1.0',
        name: 'v2',
        status: 'CURRENT',
        buildTime: '2026-05-14',
      },
    ]);

    const delta = component.versionDeltaInfo();
    expect(delta.type).toEqual('redeploy');
  });

  it('should handle version difference for line 126 coverage', () => {
    component.selectedVersion.set({
      version: 'v1.2.0',
      name: 'v1',
      status: 'LATEST',
      buildTime: '2026-05-14',
    });
    component.availableVersions.set([
      {
        version: '1.1.0',
        name: 'v2',
        status: 'CURRENT',
        buildTime: '2026-05-14',
      },
    ]);

    const delta = component.versionDeltaInfo();
    expect(delta.type).toEqual('upgrade');
  });

  it('should select a version and update selectedVersion signal', () => {
    const version: DeployableVersion = {
      version: '1.0.0',
      name: 'v1',
      status: 'LATEST',
      buildTime: '2026-05-14',
    };
    component.selectVersion(version);
    expect(component.selectedVersion()).toEqual(version);
  });

  it('should pre-select latest version and proceed to step 2 when preSelectLatest is true', () => {
    const versions: DeployableVersion[] = [
      {
        version: '1.0.0',
        name: 'v1',
        status: 'CURRENT',
        buildTime: '2026-05-14',
      },
      {version: '2.0.0', name: 'v2', status: 'LATEST', buildTime: '2026-05-14'},
    ];

    const testDialogData: ReleaseDialogData = {
      hostName: 'test-host',
      passThroughFlags: signal(''),
      releaseConfigs: versions,
      preSelectLatest: true,
    };

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ReleaseDialog, NoopAnimationsModule],
      providers: [
        {provide: MAT_DIALOG_DATA, useValue: testDialogData},
        {provide: HOST_SERVICE, useClass: FakeHostService},
        {
          provide: SnackBarService,
          useValue: jasmine.createSpyObj('SnackBarService', [
            'showSuccess',
            'showError',
          ]),
        },
        {
          provide: MatDialog,
          useValue: jasmine.createSpyObj('MatDialog', ['open']),
        },
      ],
    }).compileComponents();

    const localFixture = TestBed.createComponent(ReleaseDialog);
    const localComponent = localFixture.componentInstance;
    localFixture.detectChanges();

    expect(localComponent.selectedVersion()?.version).toEqual('2.0.0');
    expect(localComponent.currentStep()).toEqual(2);
  });

  it('should pre-select current version and proceed to step 2 when preSelectCurrent is true', () => {
    const versions: DeployableVersion[] = [
      {version: '2.0.0', name: 'v2', status: 'LATEST', buildTime: '2026-05-14'},
      {
        version: '1.0.0',
        name: 'v1',
        status: 'CURRENT',
        buildTime: '2026-05-14',
      },
    ];

    const testDialogData: ReleaseDialogData = {
      hostName: 'test-host',
      passThroughFlags: signal(''),
      releaseConfigs: versions,
      preSelectCurrent: true,
    };

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ReleaseDialog, NoopAnimationsModule],
      providers: [
        {provide: MAT_DIALOG_DATA, useValue: testDialogData},
        {provide: HOST_SERVICE, useClass: FakeHostService},
        {
          provide: SnackBarService,
          useValue: jasmine.createSpyObj('SnackBarService', [
            'showSuccess',
            'showError',
          ]),
        },
        {
          provide: MatDialog,
          useValue: jasmine.createSpyObj('MatDialog', ['open']),
        },
      ],
    }).compileComponents();

    const localFixture = TestBed.createComponent(ReleaseDialog);
    const localComponent = localFixture.componentInstance;
    localFixture.detectChanges();

    expect(localComponent.selectedVersion()?.version).toEqual('1.0.0');
    expect(localComponent.currentStep()).toEqual(2);
  });

  it('should pre-select latest version when status is LATEST_AND_CURRENT and preSelectLatest is true', () => {
    const versions: DeployableVersion[] = [
      {
        version: '1.0.0',
        name: 'v1',
        status: 'LATEST_AND_CURRENT',
        buildTime: '2026-05-14',
      },
    ];

    const testDialogData: ReleaseDialogData = {
      hostName: 'test-host',
      passThroughFlags: signal(''),
      releaseConfigs: versions,
      preSelectLatest: true,
    };

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ReleaseDialog, NoopAnimationsModule],
      providers: [
        {provide: MAT_DIALOG_DATA, useValue: testDialogData},
        {provide: HOST_SERVICE, useClass: FakeHostService},
        {
          provide: SnackBarService,
          useValue: jasmine.createSpyObj('SnackBarService', [
            'showSuccess',
            'showError',
          ]),
        },
        {
          provide: MatDialog,
          useValue: jasmine.createSpyObj('MatDialog', ['open']),
        },
      ],
    }).compileComponents();

    const localFixture = TestBed.createComponent(ReleaseDialog);
    const localComponent = localFixture.componentInstance;
    localFixture.detectChanges();

    expect(localComponent.selectedVersion()?.version).toEqual('1.0.0');
    expect(localComponent.currentStep()).toEqual(2);
  });

  it('should pre-select current version when status is LATEST_AND_CURRENT and preSelectCurrent is true', () => {
    const versions: DeployableVersion[] = [
      {
        version: '1.0.0',
        name: 'v1',
        status: 'LATEST_AND_CURRENT',
        buildTime: '2026-05-14',
      },
    ];

    const testDialogData: ReleaseDialogData = {
      hostName: 'test-host',
      passThroughFlags: signal(''),
      releaseConfigs: versions,
      preSelectCurrent: true,
    };

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ReleaseDialog, NoopAnimationsModule],
      providers: [
        {provide: MAT_DIALOG_DATA, useValue: testDialogData},
        {provide: HOST_SERVICE, useClass: FakeHostService},
        {
          provide: SnackBarService,
          useValue: jasmine.createSpyObj('SnackBarService', [
            'showSuccess',
            'showError',
          ]),
        },
        {
          provide: MatDialog,
          useValue: jasmine.createSpyObj('MatDialog', ['open']),
        },
      ],
    }).compileComponents();

    const localFixture = TestBed.createComponent(ReleaseDialog);
    const localComponent = localFixture.componentInstance;
    localFixture.detectChanges();

    expect(localComponent.selectedVersion()?.version).toEqual('1.0.0');
    expect(localComponent.currentStep()).toEqual(2);
  });

  it('should show error if deploy is called without selected version', () => {
    const snackBar = TestBed.inject(
      SnackBarService,
    ) as jasmine.SpyObj<SnackBarService>;
    component.selectedVersion.set(null);

    component.deploy();

    expect(snackBar.showError).toHaveBeenCalledWith('No version selected');
    expect(dialogSpy.open).not.toHaveBeenCalled();
  });

  it('should call releaseLabServer, open TrackingDialog, and manage isDeploying state on deploy', () => {
    const hostService = TestBed.inject(HOST_SERVICE);

    const version: DeployableVersion = {
      version: '2.0.0',
      name: 'v2',
      status: 'LATEST',
      buildTime: '2026-05-14',
      releaseName: 'rel-v2',
    };
    component.selectedVersion.set(version);
    component.tempFlags.set('--pass_through_flag_1=value_1');

    const mockResponse$ = of({trackingUrl: 'http://rollout/track-123'});

    let isDeployingDuringCall = false;
    spyOn(hostService, 'releaseLabServer').and.callFake(() => {
      isDeployingDuringCall = component.isDeploying();
      return mockResponse$;
    });

    let isDeployingDuringDialogOpen = false;
    dialogSpy.open.and.callFake((componentType, config) => {
      isDeployingDuringDialogOpen = component.isDeploying();
      return {} as unknown as MatDialogRef<unknown, unknown>;
    });

    expect(component.isDeploying()).toBeFalse();

    component.deploy();

    expect(isDeployingDuringCall).toBeTrue();
    expect(isDeployingDuringDialogOpen).toBeTrue();
    expect(component.isDeploying()).toBeFalse();

    expect(hostService.releaseLabServer).toHaveBeenCalledWith('test-host', {
      version: '2.0.0',
      flags: '--pass_through_flag_1=value_1',
      releaseName: 'rel-v2',
    });

    expect(dialogSpy.open).toHaveBeenCalledWith(TrackingDialog, {
      data: {
        hostName: 'test-host',
        version: '2.0.0',
        flags: '--pass_through_flag_1=value_1',
        response$: mockResponse$,
      },
      panelClass: 'tracking-dialog-panel',
      autoFocus: false,
    });
  });


  it('should handle downgrade version difference', () => {
    component.selectedVersion.set({
      version: 'v1.0.0',
      name: 'v1',
      status: 'LATEST',
      buildTime: '2026-05-14',
    });
    component.availableVersions.set([
      {
        version: '1.1.0',
        name: 'v2',
        status: 'CURRENT',
        buildTime: '2026-05-14',
      },
    ]);

    const delta = component.versionDeltaInfo();
    expect(delta.type).toEqual('downgrade');
  });

  it('should handle exact match version as redeploy', () => {
    component.selectedVersion.set({
      version: 'v1.1.0',
      name: 'v1',
      status: 'LATEST',
      buildTime: '2026-05-14',
    });
    component.availableVersions.set([
      {
        version: '1.1.0',
        name: 'v2',
        status: 'CURRENT',
        buildTime: '2026-05-14',
      },
    ]);

    const delta = component.versionDeltaInfo();
    expect(delta.type).toEqual('redeploy');
  });

  it('should return default deploy delta when target or current version is missing/invalid', () => {
    // Case 1: No selected version
    component.selectedVersion.set(null);
    expect(component.versionDeltaInfo().type).toEqual('deploy');

    // Case 2: Current version is Unknown
    component.selectedVersion.set({
      version: 'v1.0.0',
      name: 'v1',
      status: 'LATEST',
      buildTime: '2026-05-14',
    });
    component.availableVersions.set([
      {
        version: 'Unknown',
        name: 'v2',
        status: 'CURRENT',
        buildTime: '2026-05-14',
      },
    ]);
    expect(component.versionDeltaInfo().type).toEqual('deploy');

    // Case 3: Current version is N/A
    component.availableVersions.set([
      {
        version: 'N/A',
        name: 'v2',
        status: 'CURRENT',
        buildTime: '2026-05-14',
      },
    ]);
    expect(component.versionDeltaInfo().type).toEqual('deploy');
  });

  it('should handle lexicographical comparison for non-numeric version segments', () => {
    component.selectedVersion.set({
      version: 'v1.a.0',
      name: 'v1',
      status: 'LATEST',
      buildTime: '2026-05-14',
    });
    component.availableVersions.set([
      {
        version: '1.b.0',
        name: 'v2',
        status: 'CURRENT',
        buildTime: '2026-05-14',
      },
    ]);

    // 'a' < 'b' -> downgrade
    expect(component.versionDeltaInfo().type).toEqual('downgrade');

    component.selectedVersion.set({
      version: 'v1.b.0',
      name: 'v1',
      status: 'LATEST',
      buildTime: '2026-05-14',
    });
    component.availableVersions.set([
      {
        version: '1.a.0',
        name: 'v2',
        status: 'CURRENT',
        buildTime: '2026-05-14',
      },
    ]);

    // 'b' > 'a' -> upgrade
    expect(component.versionDeltaInfo().type).toEqual('upgrade');
  });

  it('should toggle details view in viewDetails', () => {
    const version: DeployableVersion = {
      version: '1.0.0',
      name: 'v1',
      status: 'LATEST',
      buildTime: '2026-05-14',
    };

    // Initial state: showDetails is false, selectedVersion is null (or default)
    expect(component.showDetails()).toBeFalse();

    // View details for first time -> select version and show details
    component.viewDetails(version);
    expect(component.selectedVersion()).toEqual(version);
    expect(component.showDetails()).toBeTrue();

    // View details for same version again -> hide details
    component.viewDetails(version);
    expect(component.showDetails()).toBeFalse();

    // View details for different version -> select new version and show details
    const version2: DeployableVersion = {
      version: '2.0.0',
      name: 'v2',
      status: 'LATEST',
      buildTime: '2026-05-14',
    };
    component.viewDetails(version2);
    expect(component.selectedVersion()).toEqual(version2);
    expect(component.showDetails()).toBeTrue();
  });

  it('should switch tab in switchTab', () => {
    expect(component.currentTab()).toEqual('metadata');
    component.switchTab('commands');
    expect(component.currentTab()).toEqual('commands');
  });

  it('should proceed and go back between steps', () => {
    expect(component.currentStep()).toEqual(1);

    // Cannot proceed without selected version
    component.selectedVersion.set(null);
    component.proceed();
    expect(component.currentStep()).toEqual(1);

    // Can proceed with selected version
    component.selectedVersion.set({
      version: '1.0.0',
      name: 'v1',
      status: 'LATEST',
      buildTime: '2026-05-14',
    });
    component.proceed();
    expect(component.currentStep()).toEqual(2);

    // Can go back
    component.back();
    expect(component.currentStep()).toEqual(1);
  });

  it('openFlagsDialog should update flags when different flags are saved', () => {
    const flagsDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    flagsDialogRefSpy.afterClosed.and.returnValue(of('--new-flags'));
    dialogSpy.open.and.returnValue(flagsDialogRefSpy);

    const snackBar = TestBed.inject(
      SnackBarService,
    ) as jasmine.SpyObj<SnackBarService>;

    component.tempFlags.set('--old-flags');
    component.openFlagsDialog();

    expect(dialogSpy.open).toHaveBeenCalledWith(FlagsDialog, jasmine.any(Object));
    expect(component.tempFlags()).toEqual('--new-flags');
    expect(dialogData.passThroughFlags()).toEqual('--new-flags');
    expect(component.flagsModifiedThisSession()).toBeTrue();
    expect(snackBar.showSuccess).toHaveBeenCalledWith(
      'Pass-through flags updated successfully',
    );
  });

  it('openFlagsDialog should not show success message if same flags are saved', () => {
    const flagsDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    flagsDialogRefSpy.afterClosed.and.returnValue(of('--same-flags'));
    dialogSpy.open.and.returnValue(flagsDialogRefSpy);

    const snackBar = TestBed.inject(
      SnackBarService,
    ) as jasmine.SpyObj<SnackBarService>;

    component.tempFlags.set('--same-flags');
    component.openFlagsDialog();

    expect(component.tempFlags()).toEqual('--same-flags');
    expect(component.flagsModifiedThisSession()).toBeFalse();
    expect(snackBar.showSuccess).not.toHaveBeenCalled();
  });

  it('openFlagsDialog should do nothing if closed without value or with close signal', () => {
    const flagsDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    flagsDialogRefSpy.afterClosed.and.returnValue(of('close'));
    dialogSpy.open.and.returnValue(flagsDialogRefSpy);

    component.tempFlags.set('--old-flags');
    component.openFlagsDialog();

    expect(component.tempFlags()).toEqual('--old-flags');

    // Also test non-string result
    flagsDialogRefSpy.afterClosed.and.returnValue(of(true));
    component.openFlagsDialog();
    expect(component.tempFlags()).toEqual('--old-flags');
  });
});
