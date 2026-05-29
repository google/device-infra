import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatTooltipModule} from '@angular/material/tooltip';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {APP_DATA} from '@deviceinfra/app/core/models/app_data';
import {ActionBarAction} from '../../../../core/constants/action_bar_config';
import {
  HostOverview,
  HostOverviewPageData,
} from '../../../../core/models/host_overview';
import {ComingSoonService} from '../../../../shared/services/coming_soon_service';
import {MatDialog} from '@angular/material/dialog';
import {HostActionBar} from './host_action_bar';
import {HostDebugDialog} from '../host_debug_dialog/host_debug_dialog';

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
          tooltip: 'Decommission tooltip',
          isReady: true,
        },
      },
    },
    overviewContent: {
      hostName: 'test-host',
      ip: '127.0.0.1',
    } as HostOverview,
  };

  beforeEach(async () => {
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    comingSoonService = jasmine.createSpyObj('ComingSoonService', ['show']);

    await TestBed.configureTestingModule({
      imports: [HostActionBar, MatTooltipModule, NoopAnimationsModule],
      providers: [
        {provide: MatDialog, useValue: dialog},
        {provide: ComingSoonService, useValue: comingSoonService},
        {provide: APP_DATA, useValue: {applicationId: 'test-app'}},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HostActionBar);
    component = fixture.componentInstance;
    component.pageData = mockPageData;
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

  it('should trigger Coming Soon service when clicking a not-ready action', () => {
    const configButton = fixture.debugElement.query(
      By.css('[data-testid="configuration-button-2xl"]'),
    );
    configButton.nativeElement.click();
    expect(comingSoonService.show).toHaveBeenCalledWith(
      ActionBarAction.HOST_CONFIGURATION,
      'default',
      component.legacyFeUrl ? jasmine.anything() : undefined,
    );
  });
});
