import {ChangeDetectionStrategy, Component, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {MatTooltipModule} from '@angular/material/tooltip';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {ActivatedRoute, provideRouter} from '@angular/router';
import {APP_DATA} from '@deviceinfra/app/core/models/app_data';
import {of} from 'rxjs';
import {ActionBarAction} from '../../../../core/constants/action_bar_config';
import {
  HostOverview,
  HostOverviewPageData,
} from '../../../../core/models/host_overview';
import {Environment} from '../../../../core/services/environment';
import {FeatureEnablementService} from '../../../../core/services/feature_enablement_service';
import {ComingSoonService} from '../../../../shared/services/coming_soon_service';
import {HostAdvancedOpsDialog} from '../host_advanced_ops_dialog/host_advanced_ops_dialog';
import {HostConfig} from '../host_config/host_config';
import {HostEmpty} from '../host_config/host_empty/host_empty';
import {HostSettings} from '../host_config/host_settings/host_settings';
import {HostWizard} from '../host_config/host_wizard/host_wizard';
import {HostDebugDialog} from '../host_debug_dialog/host_debug_dialog';
import {HostDecommissionDialog} from '../host_decommission_dialog/host_decommission_dialog';
import {HostActionBar} from './host_action_bar';

@Component({
  standalone: true,
  imports: [HostActionBar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      style="container: detail-sheet / inline-size;"
      [style.width.px]="containerWidth()"
    >
      <app-host-action-bar [pageData]="pageData()"></app-host-action-bar>
    </div>
  `,
})
class HostActionBarContainerHostComponent {
  readonly containerWidth = signal(600);
  readonly pageData = signal<HostOverviewPageData>({} as HostOverviewPageData);
}

describe('HostActionBar', () => {
  let component: HostActionBar;
  let fixture: ComponentFixture<HostActionBar>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let comingSoonService: jasmine.SpyObj<ComingSoonService>;

  const mockPageData: HostOverviewPageData = {
    headerInfo: {
      hostName: 'test-host',
      actions: {
        configuration: {
          enabled: true,
          visible: true,
          tooltip: 'Config tooltip',
          isReady: false,
        },
        debug: {
          enabled: true,
          visible: true,
          tooltip: 'Debug tooltip',
          isReady: true,
        },
        decommission: {
          enabled: true,
          visible: true,
          tooltip: 'Remove the missing host',
          isReady: true,
        },
        advancedOperations: {
          enabled: true,
          visible: true,
          tooltip: 'Advanced operations tooltip',
          isReady: true,
        },
      },
    },
    overviewContent: {
      hostName: 'test-host',
      ip: '127.0.0.1',
    } as HostOverview,
  };

  let mockEnvironment: jasmine.SpyObj<Environment>;
  let featureEnablementService: jasmine.SpyObj<FeatureEnablementService>;

  beforeEach(async () => {
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    comingSoonService = jasmine.createSpyObj('ComingSoonService', [
      'show',
      'showForDevice',
      'showForHost',
    ]);

    mockEnvironment = jasmine.createSpyObj('Environment', ['isGoogleInternal']);
    mockEnvironment.isGoogleInternal.and.returnValue(true);
    featureEnablementService = jasmine.createSpyObj(
      'FeatureEnablementService',
      ['isHostConfigEnabled'],
    );
    featureEnablementService.isHostConfigEnabled.and.returnValue(true);

    await TestBed.configureTestingModule({
      imports: [HostActionBar, MatTooltipModule, NoopAnimationsModule],
      providers: [
        {provide: MatDialog, useValue: dialog},
        {provide: ComingSoonService, useValue: comingSoonService},
        {provide: APP_DATA, useValue: {applicationId: 'test-app'}},
        {provide: Environment, useValue: mockEnvironment},
        {
          provide: FeatureEnablementService,
          useValue: featureEnablementService,
        },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParams: of({}),
          },
        },
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HostActionBar);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('pageData', mockPageData);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show visible actions', () => {
    const configButton = fixture.debugElement.query(
      By.css('[data-testid="configuration-button-2xl"]'),
    );
    const debugButton = fixture.debugElement.query(
      By.css('[data-testid="debug-button-2xl"]'),
    );
    const decommissionButton = fixture.debugElement.query(
      By.css('[data-testid="decommission-button-2xl"]'),
    );

    expect(configButton).toBeTruthy();
    expect(debugButton).toBeTruthy();
    expect(decommissionButton).toBeTruthy();
    expect(decommissionButton.nativeElement.textContent).toContain('Remove');
    expect(decommissionButton.nativeElement.textContent).not.toContain(
      'Decommission',
    );
  });

  it('should open debug dialog on action click', () => {
    const debugButton = fixture.debugElement.query(
      By.css('[data-testid="debug-button-2xl"]'),
    );
    debugButton.nativeElement.click();
    expect(dialog.open).toHaveBeenCalledWith(
      HostDebugDialog,
      jasmine.objectContaining({
        data: {hostName: 'test-host'},
      }),
    );
  });

  it('should open advanced operations dialog on action click', () => {
    const advancedOpsButton = fixture.debugElement.query(
      By.css('[data-testid="advancedOperations-button-2xl"]'),
    );
    advancedOpsButton.nativeElement.click();
    expect(dialog.open).toHaveBeenCalledWith(
      HostAdvancedOpsDialog,
      jasmine.objectContaining({
        data: {hostName: 'test-host'},
      }),
    );
  });

  it('should open decommission dialog on action click', () => {
    const decommissionButton = fixture.debugElement.query(
      By.css('[data-testid="decommission-button-2xl"]'),
    );
    decommissionButton.nativeElement.click();
    expect(dialog.open).toHaveBeenCalledWith(
      HostDecommissionDialog,
      jasmine.objectContaining({
        data: {hostName: 'test-host'},
      }),
    );
  });

  it('should trigger Coming Soon service when clicking a not-ready action', () => {
    const configButton = fixture.debugElement.query(
      By.css('[data-testid="configuration-button-2xl"]'),
    );
    configButton.nativeElement.click();
    expect(comingSoonService.showForHost).toHaveBeenCalledWith(
      ActionBarAction.HOST_CONFIGURATION,
      component.legacyFeUrl,
      component.pageData().headerInfo.hostName,
      component.pageData().overviewContent.ip,
    );
  });

  it('should open HostConfig dialog on openConfiguration, handle reset and call HostEmpty and HostWizard', () => {
    mockEnvironment.isGoogleInternal.and.returnValue(true);
    const configDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    const emptyDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    const wizardDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);

    configDialogRefSpy.afterClosed.and.returnValue(
      of({action: 'reset', hostName: 'test-host'}),
    );
    emptyDialogRefSpy.afterClosed.and.returnValue(
      of({action: 'new', hostName: 'test-host', config: null}),
    );

    dialog.open.and.callFake((component: unknown) => {
      if (component === HostConfig) {
        return configDialogRefSpy;
      }
      if (component === HostEmpty) {
        return emptyDialogRefSpy;
      }
      return wizardDialogRefSpy;
    });

    component.onConfiguration();

    expect(dialog.open).toHaveBeenCalledWith(HostConfig, jasmine.any(Object));
    expect(dialog.open).toHaveBeenCalledWith(HostEmpty, jasmine.any(Object));
    expect(dialog.open).toHaveBeenCalledWith(HostWizard, jasmine.any(Object));
  });

  it('should open HostSettings dialog if non-internal, and call HostEmpty on reset result', () => {
    mockEnvironment.isGoogleInternal.and.returnValue(false);
    const configDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    const settingsDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    const emptyDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);

    configDialogRefSpy.afterClosed.and.returnValue(
      of({action: 'new', hostName: 'test-host', config: null}),
    );
    settingsDialogRefSpy.afterClosed.and.returnValue(
      of({action: 'reset', hostName: 'test-host'}),
    );
    emptyDialogRefSpy.afterClosed.and.returnValue(of(undefined));

    dialog.open.and.callFake((component: unknown) => {
      if (component === HostConfig) {
        return configDialogRefSpy;
      }
      if (component === HostSettings) {
        return settingsDialogRefSpy;
      }
      return emptyDialogRefSpy;
    });

    component.onConfiguration();

    expect(dialog.open).toHaveBeenCalledWith(HostConfig, jasmine.any(Object));
    expect(dialog.open).toHaveBeenCalledWith(HostSettings, jasmine.any(Object));
    expect(dialog.open).toHaveBeenCalledWith(HostEmpty, jasmine.any(Object));
  });

  it('should open HostWizard dialog but not HostSettings for copy action if non-internal', () => {
    mockEnvironment.isGoogleInternal.and.returnValue(false);
    const configDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    const wizardDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);

    configDialogRefSpy.afterClosed.and.returnValue(
      of({action: 'copy', hostName: 'test-host', config: null}),
    );

    dialog.open.and.callFake((component: unknown) => {
      if (component === HostConfig) {
        return configDialogRefSpy;
      }
      return wizardDialogRefSpy;
    });

    component.onConfiguration();

    expect(dialog.open).toHaveBeenCalledWith(HostConfig, jasmine.any(Object));
    expect(dialog.open).toHaveBeenCalledWith(HostWizard, jasmine.any(Object));
    expect(dialog.open).not.toHaveBeenCalledWith(
      HostSettings,
      jasmine.any(Object),
    );
  });

  it('getAction should return correct action state', () => {
    expect(component.getAction('configuration')).toEqual({
      enabled: true,
      visible: true,
      tooltip: 'Config tooltip',
      isReady: false,
    });

    expect(component.getAction('debug')).toEqual({
      enabled: true,
      visible: true,
      tooltip: 'Debug tooltip',
      isReady: true,
    });

    expect(component.getAction('advancedOperations')).toEqual({
      enabled: true,
      visible: true,
      tooltip: 'Advanced operations tooltip',
      isReady: true,
    });
  });

  describe('when the host configuration is not enabled', () => {
    beforeEach(() => {
      featureEnablementService.isHostConfigEnabled.and.returnValue(false);
      fixture = TestBed.createComponent(HostActionBar);
      component = fixture.componentInstance;
      fixture.componentRef.setInput('pageData', mockPageData);
      fixture.detectChanges();
    });

    it('should hide the Configuration button but keep the other actions', () => {
      expect(component.getAction('configuration')).toBeUndefined();
      expect(
        fixture.debugElement.query(
          By.css('[data-testid="configuration-button-2xl"]'),
        ),
      ).toBeNull();
      expect(
        fixture.debugElement.query(By.css('[data-testid="debug-button-2xl"]')),
      ).toBeTruthy();
    });

    it('should ignore onConfiguration, e.g. from the openHostConfig deep link', () => {
      component.onConfiguration();
      expect(dialog.open).not.toHaveBeenCalled();
    });
  });

  it('folds secondary actions based on detail-sheet container width', () => {
    const hostFixture = TestBed.createComponent(
      HostActionBarContainerHostComponent,
    );
    hostFixture.componentInstance.pageData.set(mockPageData);
    hostFixture.detectChanges();
    const compiled = hostFixture.nativeElement as HTMLElement;
    const smEl = compiled.querySelector<HTMLElement>('.header-actions-sm')!;
    const xlEl = compiled.querySelector<HTMLElement>('.header-actions-xl')!;
    const twoXlEl = compiled.querySelector<HTMLElement>('.header-actions-2xl')!;

    expect(getComputedStyle(smEl).display).toBe('flex');
    expect(getComputedStyle(xlEl).display).toBe('none');
    expect(getComputedStyle(twoXlEl).display).toBe('none');

    hostFixture.componentInstance.containerWidth.set(780);
    hostFixture.detectChanges();
    expect(getComputedStyle(smEl).display).toBe('none');
    expect(getComputedStyle(xlEl).display).toBe('flex');
    expect(getComputedStyle(twoXlEl).display).toBe('none');

    hostFixture.componentInstance.containerWidth.set(920);
    hostFixture.detectChanges();
    expect(getComputedStyle(smEl).display).toBe('none');
    expect(getComputedStyle(xlEl).display).toBe('none');
    expect(getComputedStyle(twoXlEl).display).toBe('flex');
  });
});
