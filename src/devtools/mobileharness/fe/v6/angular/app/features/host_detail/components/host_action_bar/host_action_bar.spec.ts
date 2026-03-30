import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {MatTooltipModule} from '@angular/material/tooltip';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {
  HostOverview,
  HostOverviewPageData,
} from '../../../../core/models/host_overview';
import {
  HOST_SERVICE,
  HostService,
} from '../../../../core/services/host/host_service';
import {SnackBarService} from '../../../../shared/services/snackbar_service';
import {HostDecommissionDialog} from '../host_decommission_dialog/host_decommission_dialog';
import {HostActionBar} from './host_action_bar';

describe('HostActionBar', () => {
  let component: HostActionBar;
  let fixture: ComponentFixture<HostActionBar>;
  let snackBarService: jasmine.SpyObj<SnackBarService>;
  let hostService: jasmine.SpyObj<HostService>;
  let dialog: jasmine.SpyObj<MatDialog>;

  const mockPageData: HostOverviewPageData = {
    headerInfo: {
      hostName: 'test-host',
      actions: {
        configuration: {
          enabled: true,
          visible: true,
          tooltip: 'Config tooltip',
        },
        debug: {enabled: true, visible: true, tooltip: 'Debug tooltip'},
        deploy: {enabled: false, visible: true, tooltip: 'Deploy tooltip'},
        start: {enabled: true, visible: false, tooltip: 'Start tooltip'},
        restart: {enabled: true, visible: true, tooltip: 'Restart tooltip'},
        stop: {enabled: true, visible: true, tooltip: 'Stop tooltip'},
        decommission: {
          enabled: true,
          visible: true,
          tooltip: 'Decommission tooltip',
        },
        updatePassThroughFlags: {
          enabled: true,
          visible: true,
          tooltip: 'Flags tooltip',
        },
        release: {enabled: true, visible: true, tooltip: 'Release tooltip'},
      },
    },
    overviewContent: {
      hostName: 'test-host',
      ip: '127.0.0.1',
    } as HostOverview,
  };

  beforeEach(async () => {
    snackBarService = jasmine.createSpyObj('SnackBarService', [
      'showInfo',
      'showError',
    ]);
    hostService = jasmine.createSpyObj('HostService', ['decommissionHost']);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      imports: [HostActionBar, MatTooltipModule, NoopAnimationsModule],
      providers: [
        {provide: SnackBarService, useValue: snackBarService},
        {provide: HOST_SERVICE, useValue: hostService},
        provideRouter([]),
        {provide: MatDialog, useValue: dialog},
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

  it('should show visible actions and hide invisible ones', () => {
    const configButton = fixture.debugElement.query(
      By.css('[data-testid="configuration-button-2xl"]'),
    );
    const debugButton = fixture.debugElement.query(
      By.css('[data-testid="debug-button-2xl"]'),
    );
    const startButton = fixture.debugElement.query(
      By.css('[data-testid="start-button-2xl"]'),
    );
    const deployButton = fixture.debugElement.query(
      By.css('[data-testid="deploy-button-2xl"]'),
    );

    expect(configButton).toBeTruthy();
    expect(debugButton).toBeTruthy();
    expect(startButton).toBeFalsy();
    expect(deployButton).toBeFalsy();
  });

  it('should disable buttons based on enabled state', () => {
    const restartButton = fixture.debugElement.query(
      By.css('[data-testid="restart-button-2xl"]'),
    );
    expect(restartButton.nativeElement.disabled).toBeFalse();
  });

  it('should trigger snackbar on action click', () => {
    const debugButton = fixture.debugElement.query(
      By.css('[data-testid="debug-button-2xl"]'),
    );
    debugButton.nativeElement.click();
    expect(snackBarService.showInfo).toHaveBeenCalledWith(
      jasmine.stringMatching(/Debug action triggered for test-host/),
    );
  });

  it('should open decommission dialog when decommission button is clicked', () => {
    component.onDecommission();

    expect(dialog.open).toHaveBeenCalledWith(HostDecommissionDialog, {
      data: {hostName: 'test-host'},
      autoFocus: false,
    });
  });
});
