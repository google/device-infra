import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialog, MatDialogRef} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {Observable, of, Subject, throwError} from 'rxjs';

import {APP_DATA} from '../../../../core/models/app_data';
import {
  DeployableVersion,
  PreflightLabServerReleaseResponse,
} from '../../../../core/models/host_action';
import {HostOverview} from '../../../../core/models/host_overview';
import {FakeHostService} from '../../../../core/services/host/fake_host_service';
import {HOST_SERVICE} from '../../../../core/services/host/host_service';
import {ConfirmDialog} from '../../../../shared/components/confirm_dialog/confirm_dialog';
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

  beforeEach(async () => {
    dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      imports: [
        HostOverviewPage,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
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
        {
          provide: SnackBarService,
          useValue: jasmine.createSpyObj('SnackBarService', ['showError']),
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

  describe('Release & Upgrade Actions', () => {
    let snackBarSpy: jasmine.SpyObj<SnackBarService>;
    let fakeHostService: FakeHostService;

    function getReleaseButton(compiled: HTMLElement): HTMLButtonElement {
      const buttons = compiled.querySelectorAll(
        '#lab-server .operation-button button',
      );
      return Array.from(buttons).find((b) =>
        b.textContent?.trim().toLowerCase().includes('release'),
      ) as HTMLButtonElement;
    }

    function getUpgradeButton(compiled: HTMLElement): HTMLButtonElement {
      return compiled.querySelector(
        '.upgrade-version-button',
      ) as HTMLButtonElement;
    }

    function getSuggestChip(compiled: HTMLElement): HTMLButtonElement {
      return compiled.querySelector('.m3-chip-suggestion') as HTMLButtonElement;
    }

    beforeEach(() => {
      // Re-use global dialogSpy, but configure return value for this suite
      dialogSpy.open.and.returnValue({
        afterClosed: () => of('close'),
      } as unknown as MatDialogRef<unknown, unknown>);
      snackBarSpy = TestBed.inject(
        SnackBarService,
      ) as jasmine.SpyObj<SnackBarService>;
      fakeHostService = TestBed.inject(HOST_SERVICE) as FakeHostService;
    });

    it('should handle onUpgrade click, show upgrade spinner, and open ReleaseDialog with preSelectLatest=true', () => {
      component.host = {
        ...mockHost,
        canUpgrade: true,
      };
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const upgradeButton = getUpgradeButton(compiled);
      const suggestChip = getSuggestChip(compiled);

      expect(upgradeButton.disabled).toBeFalse();
      expect(suggestChip.disabled).toBeFalse();

      const subject = new Subject<PreflightLabServerReleaseResponse>();
      spyOn(fakeHostService, 'preflightLabServerRelease').and.returnValue(
        subject,
      );

      upgradeButton.click();
      fixture.detectChanges();

      expect(component.isOpeningUpgrade()).toBeTrue();
      expect(upgradeButton.disabled).toBeTrue();
      expect(suggestChip.disabled).toBeTrue();

      // Emit versions to trigger dialog open
      const mockVersions: DeployableVersion[] = [
        {
          version: '2.0.0',
          name: 'v2',
          status: 'LATEST' as const,
          buildTime: '2026-05-14',
        },
      ];
      subject.next({ready: {versions: mockVersions}});
      subject.complete();
      fixture.detectChanges();

      expect(component.isOpeningUpgrade()).toBeFalse();
      expect(upgradeButton.disabled).toBeFalse();
      expect(suggestChip.disabled).toBeFalse();
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

    it('should handle onRelease click, show release spinner, and open ReleaseDialog with preSelectLatest=false', () => {
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;
      const releaseButton = getReleaseButton(compiled);
      expect(releaseButton.disabled).toBeFalse();

      const subject = new Subject<PreflightLabServerReleaseResponse>();
      spyOn(fakeHostService, 'preflightLabServerRelease').and.returnValue(
        subject,
      );

      component.onRelease();
      fixture.detectChanges();

      expect(component.isOpeningRelease()).toBeTrue();
      expect(releaseButton.disabled).toBeTrue();
      expect(component.isOpeningUpgrade()).toBeFalse();

      // Emit versions to trigger dialog open
      const mockVersions: DeployableVersion[] = [
        {
          version: '2.0.0',
          name: 'v2',
          status: 'LATEST' as const,
          buildTime: '2026-05-14',
        },
      ];
      subject.next({ready: {versions: mockVersions}});
      subject.complete();
      fixture.detectChanges();

      expect(component.isOpeningRelease()).toBeFalse();
      expect(releaseButton.disabled).toBeFalse();
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

    it('should handle onRelease with preSelectCurrent=true, show redeploy spinner, and open ReleaseDialog with preSelectCurrent=true', () => {
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;
      const releaseButton = getReleaseButton(compiled);
      expect(releaseButton.disabled).toBeFalse();

      const subject = new Subject<PreflightLabServerReleaseResponse>();
      spyOn(fakeHostService, 'preflightLabServerRelease').and.returnValue(
        subject,
      );

      component.onRelease({preSelectCurrent: true});
      fixture.detectChanges();

      expect(component.isOpeningRedeploy()).toBeTrue();
      expect(releaseButton.disabled).toBeTrue();
      expect(component.isOpeningRelease()).toBeFalse();
      expect(component.isOpeningUpgrade()).toBeFalse();

      // Emit versions to trigger dialog open
      const mockVersions: DeployableVersion[] = [
        {
          version: '2.0.0',
          name: 'v2',
          status: 'CURRENT' as const,
          buildTime: '2026-05-14',
        },
      ];
      subject.next({ready: {versions: mockVersions}});
      subject.complete();
      fixture.detectChanges();

      expect(component.isOpeningRedeploy()).toBeFalse();
      expect(releaseButton.disabled).toBeFalse();
      expect(component.isOpeningRelease()).toBeFalse();
      expect(component.isOpeningUpgrade()).toBeFalse();
      expect(dialogSpy.open).toHaveBeenCalledWith(ReleaseDialog, {
        data: {
          hostName: component.host.hostName,
          releaseConfigs: mockVersions,
          passThroughFlags: component.passThroughFlags,
          preSelectLatest: false,
          preSelectCurrent: true,
        },
        autoFocus: false,
      });
    });

    it('should reset isOpeningUpgrade loading state on preflight error', () => {
      spyOn(fakeHostService, 'preflightLabServerRelease').and.returnValue(
        throwError(() => new Error('Failed speculation')),
      );

      component.onRelease({preSelectLatest: true}); // under upgrade loading context

      expect(component.isOpeningUpgrade()).toBeFalse();
      expect(snackBarSpy.showError).toHaveBeenCalledWith(
        'Failed to load release info: Failed speculation',
      );
    });

    it('should reset isOpeningRelease loading state on preflight error', () => {
      spyOn(fakeHostService, 'preflightLabServerRelease').and.returnValue(
        throwError(() => new Error('Failed speculation')),
      );

      component.onRelease(); // under release loading context

      expect(component.isOpeningRelease()).toBeFalse();
      expect(snackBarSpy.showError).toHaveBeenCalledWith(
        'Failed to load release info: Failed speculation',
      );
    });

    it('should reset isOpeningRedeploy loading state on preflight error', () => {
      spyOn(fakeHostService, 'preflightLabServerRelease').and.returnValue(
        throwError(() => new Error('Failed speculation')),
      );

      component.onRelease({preSelectCurrent: true}); // under redeploy loading context

      expect(component.isOpeningRedeploy()).toBeFalse();
      expect(snackBarSpy.showError).toHaveBeenCalledWith(
        'Failed to load release info: Failed speculation',
      );
    });

    it('should show restart dialog and handle confirm', () => {
      const subject = new Subject<PreflightLabServerReleaseResponse>();
      spyOn(fakeHostService, 'preflightLabServerRelease').and.returnValue(
        subject,
      );

      component.showRestartDialog();

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
      expect(fakeHostService.preflightLabServerRelease).toHaveBeenCalledWith(
        component.host.hostName,
      );
      expect(component.isOpeningRedeploy()).toBeTrue();
    });

    it('should show No Access dialog when preflight returns permissionDenied', () => {
      const subject = new Subject<PreflightLabServerReleaseResponse>();
      spyOn(fakeHostService, 'preflightLabServerRelease').and.returnValue(
        subject,
      );

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

    it('should show No Valid Versions dialog when preflight returns empty versions', () => {
      const subject = new Subject<PreflightLabServerReleaseResponse>();
      spyOn(fakeHostService, 'preflightLabServerRelease').and.returnValue(
        subject,
      );

      component.onRelease();

      // Emit empty versions
      subject.next({ready: {versions: []}});
      subject.complete();

      // Verify ConfirmDialog was opened with "No Valid Versions"
      expect(dialogSpy.open).toHaveBeenCalledWith(
        ConfirmDialog,
        jasmine.objectContaining({
          data: jasmine.objectContaining({
            title: 'No Valid Versions',
            type: 'warning',
          }),
        }),
      );
    });
  });
});
