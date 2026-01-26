import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

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
});
