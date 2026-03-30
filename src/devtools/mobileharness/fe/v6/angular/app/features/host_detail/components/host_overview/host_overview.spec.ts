import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatTooltip} from '@angular/material/tooltip';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {HostActions} from '../../../../core/models/host_action';
import {HostOverview} from '../../../../core/models/host_overview';
import {FakeHostService} from '../../../../core/services/host/fake_host_service';
import {HOST_SERVICE} from '../../../../core/services/host/host_service';
import {RemoteControlService} from '../../../../shared/services/remote_control_service';
import {SnackBarService} from '../../../../shared/services/snackbar_service';
import {HostOverviewPage} from './host_overview';

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
  };

  const mockActions: HostActions = {
    configuration: {visible: true, enabled: true, tooltip: 'Configure host'},
    debug: {visible: true, enabled: true, tooltip: 'Debug host'},
    deploy: {visible: true, enabled: true, tooltip: 'Deploy lab server'},
    start: {visible: true, enabled: true, tooltip: 'Start lab server'},
    restart: {visible: true, enabled: true, tooltip: 'Restart lab server'},
    stop: {visible: true, enabled: true, tooltip: 'Stop lab server'},
    decommission: {visible: true, enabled: true, tooltip: 'Decommission host'},
    updatePassThroughFlags: {
      visible: true,
      enabled: true,
      tooltip: 'Update pass through flags',
    },
    release: {visible: true, enabled: true, tooltip: 'Release lab server'},
  };

  let fixture: ComponentFixture<HostOverviewPage>;
  let component: HostOverviewPage;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        HostOverviewPage,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [
        provideRouter([]),
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
    }).compileComponents();

    fixture = TestBed.createComponent(HostOverviewPage);
    component = fixture.componentInstance;
    component.host = mockHost;
    component.actions = mockActions;
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

    expect(chips.length).toBe(5);
    expect(chips[0].textContent?.trim()).toBe('Lab1');
    expect(chips[1].textContent?.trim()).toBe('Lab2');
    expect(chips[2].textContent?.trim()).toBe('Lab3');
    expect(chips[3].textContent?.trim()).toBe('Lab4');
    expect(chips[4].textContent?.trim()).toBe('Lab5');
  });

  it('should hide Pass Through Flags when visible is false', () => {
    component.actions = {
      ...mockActions,
      updatePassThroughFlags: {visible: false, enabled: true, tooltip: ''},
    };
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const passThroughFlagsLabel = Array.from(compiled.querySelectorAll('dt')).find(
      (dt) => dt.textContent?.includes('Pass Through Flags'),
    );

    expect(passThroughFlagsLabel).toBeFalsy();
  });

  it('should disable edit button when enabled is false', () => {
    component.actions = {
      ...mockActions,
      updatePassThroughFlags: {visible: true, enabled: false, tooltip: ''},
    };
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const editButton = compiled.querySelector(
      '.edit-flags-button',
    ) as HTMLButtonElement;

    expect(editButton).toBeTruthy();
    expect(editButton.disabled).toBeTrue();
  });

  it('should display "Update available" button when canUpgrade is true', () => {
    component.host = {
      ...mockHost,
      canUpgrade: true,
    };
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const upgradeButton = compiled.querySelector('.m3-chip-suggestion');

    expect(upgradeButton).toBeTruthy();
    expect(upgradeButton?.textContent?.trim()).toBe('Update available');
  });

  it('should NOT display "Update available" button when canUpgrade is false', () => {
    component.host = {
      ...mockHost,
      canUpgrade: false,
    };
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const upgradeButton = compiled.querySelector('.m3-chip-suggestion');

    expect(upgradeButton).toBeFalsy();
  });

  it('should display correct tooltips on buttons', () => {
    component.host = {
      ...mockHost,
      canUpgrade: true,
    };
    fixture.detectChanges();

    const editFlagsButton = fixture.debugElement.query(
      By.css('.edit-flags-button')
    );
    const upgradeButton = fixture.debugElement.query(
      By.css('.upgrade-version-button')
    );

    const editFlagsTooltip = editFlagsButton.injector.get(MatTooltip);
    const upgradeTooltip = upgradeButton.injector.get(MatTooltip);

    expect(editFlagsTooltip.message).toBe('Update pass through flags');
    expect(upgradeTooltip.message).toBe('Release lab server');
  });
});
