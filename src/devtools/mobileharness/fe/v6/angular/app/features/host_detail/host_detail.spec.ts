import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import {of} from 'rxjs';
import {APP_DATA} from '../../core/models/app_data';
import {HostOverviewPageData} from '../../core/models/host_overview';
import {FakeHostService} from '../../core/services/host/fake_host_service';
import {HOST_SERVICE} from '../../core/services/host/host_service';
import {ClipboardService} from '../../shared/services/clipboard_service';
import {DeviceActionService} from '../../shared/services/device_action_service';
import {SnackBarService} from '../../shared/services/snackbar_service';

import {HostDetail} from './host_detail';

function createMockPageData(isAte: boolean): HostOverviewPageData {
  const hiddenAction = {
    visible: false,
    enabled: false,
    isReady: false,
    tooltip: '',
  };
  return {
    headerInfo: {
      hostName: 'host-a-1.example.com',
      actions: {
        configuration: hiddenAction,
        debug: hiddenAction,
        decommission: hiddenAction,
        advancedOperations: hiddenAction,
      },
    },
    overviewContent: {
      hostName: 'host-a-1.example.com',
      ip: '192.168.1.1',
      os: 'gLinux',
      labType: 'Satellite',
      deviceManagerType: 'MH',
      isAte,
      labServer: {
        connectivity: {
          state: 'RUNNING',
          title: 'Running',
          tooltip: 'Host is running.',
        },
        version: 'R123.45.6',
        passThroughFlags: '',
      },
      daemonServer: {
        status: {
          state: 'RUNNING',
          title: 'Running',
          tooltip: 'Daemon is running.',
        },
        version: '24.08.01',
      },
      properties: {},
      showPassThroughFlags: true,
    },
  };
}

describe('HostDetail Component', () => {
  let fixture: ComponentFixture<HostDetail>;
  let component: HostDetail;
  let mockSnackBarService: jasmine.SpyObj<SnackBarService>;
  let mockClipboardService: jasmine.SpyObj<ClipboardService>;
  let hostService: FakeHostService;

  beforeEach(async () => {
    mockSnackBarService = jasmine.createSpyObj('SnackBarService', [
      'showSuccess',
      'showError',
      'showInProgress',
    ]);
    mockClipboardService = jasmine.createSpyObj('ClipboardService', [
      'copyToClipboard',
    ]);
    await TestBed.configureTestingModule({
      imports: [
        HostDetail,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(
              convertToParamMap({'hostName': 'host-a-1.example.com'}),
            ),
            queryParamMap: of(convertToParamMap({})),
            queryParams: of({}),
          },
        },
        {provide: HOST_SERVICE, useClass: FakeHostService},
        {
          provide: DeviceActionService,
          useValue: jasmine.createSpyObj('DeviceActionService', [
            'takeScreenshot',
            'getLogcat',
            'flashDevice',
            'quarantineDevice',
          ]),
        },
        {provide: SnackBarService, useValue: mockSnackBarService},
        {provide: ClipboardService, useValue: mockClipboardService},
        {provide: APP_DATA, useValue: {applicationId: 'arsenal'}},
      ],
    }).compileComponents();

    hostService = TestBed.inject(HOST_SERVICE) as FakeHostService;
    fixture = TestBed.createComponent(HostDetail);
    component = fixture.componentInstance;
  });

  it('should be created', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should not render an ATE badge in the header for an ATE host', () => {
    spyOn(hostService, 'getHostOverview').and.returnValue(
      of(createMockPageData(true)),
    );

    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(
      compiled.querySelector('.host-title-group')?.textContent,
    ).not.toContain('ATE');
  });
});
