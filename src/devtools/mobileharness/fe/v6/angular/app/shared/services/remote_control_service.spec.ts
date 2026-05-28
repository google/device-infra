import {TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {of, throwError} from 'rxjs';

import {
  CheckRemoteControlEligibilityResponse,
  DeviceProxyType,
  EligibilityStatus,
  RemoteControlDevicesRequest,
  RemoteControlDevicesResponse,
} from '../../core/models/host_overview';
import {HOST_SERVICE, HostService} from '../../core/services/host/host_service';
import {ConfirmDialog} from '../components/confirm_dialog/confirm_dialog';
import {RemoteControlDialog} from '../components/remote_control/dialog/remote_control_dialog';
import {ConnectionErrorContent} from '../components/remote_control/feedback/connection_error_content';
import {RemoteControlService} from './remote_control_service';
import {SnackBarService} from './snackbar_service';

describe('RemoteControlService', () => {
  let service: RemoteControlService;
  let hostServiceSpy: jasmine.SpyObj<HostService>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;
  let snackBarSpy: jasmine.SpyObj<SnackBarService>;

  beforeEach(() => {
    dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);
    snackBarSpy = jasmine.createSpyObj('SnackBarService', [
      'showError',
      'showSuccess',
    ]);
    hostServiceSpy = jasmine.createSpyObj('HostService', [
      'checkRemoteControlEligibility',
      'remoteControlDevices',
    ]);

    TestBed.configureTestingModule({
      providers: [
        RemoteControlService,
        {provide: HOST_SERVICE, useValue: hostServiceSpy},
        {provide: MatDialog, useValue: dialogSpy},
        {provide: SnackBarService, useValue: snackBarSpy},
      ],
    });

    service = TestBed.inject(RemoteControlService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('startRemoteControl should show ConnectionError dialog with mapped capabilities when no common proxy', () => {
    const devices = [
      {id: 'device-1', model: 'Pixel 9', isTestbed: false, subDevices: []},
      {id: 'device-2', model: 'Pixel 8', isTestbed: false, subDevices: []},
      {id: 'device-4', model: 'Pixel 7', isTestbed: false, subDevices: []},
    ];

    // Mock backend response: BLOCK_NO_COMMON_PROXY
    // Device 1: eligible, supports ADB_ONLY
    // Device 2: eligible, supports SSH
    // Device 3: ineligible (PERMISSION_DENIED), supports VIDEO (should be filtered out)
    const mockResponse: CheckRemoteControlEligibilityResponse = {
      status: EligibilityStatus.BLOCK_NO_COMMON_PROXY,
      results: [
        {
          deviceId: 'device-1',
          isEligible: true,
          supportedProxyTypes: [DeviceProxyType.ADB_ONLY],
        },
        {
          deviceId: 'device-2',
          isEligible: true,
          supportedProxyTypes: [DeviceProxyType.SSH],
        },
        {
          deviceId: 'device-3',
          isEligible: false,
          ineligibilityReason: {
            code: 'PERMISSION_DENIED',
            message: 'Permission denied',
          },
          supportedProxyTypes: [DeviceProxyType.VIDEO],
        },
        {
          deviceId: 'device-4',
          isEligible: true,
          supportedProxyTypes: [99 as DeviceProxyType],
        },
      ],
    };

    hostServiceSpy.checkRemoteControlEligibility.and.returnValue(
      of(mockResponse),
    );

    service.startRemoteControl('test-host', devices).subscribe();

    expect(hostServiceSpy.checkRemoteControlEligibility).toHaveBeenCalled();
    expect(dialogSpy.open).toHaveBeenCalledWith(
      ConfirmDialog,
      jasmine.any(Object),
    );

    const dialogConfig = dialogSpy.open.calls.mostRecent().args[1] as {
      data: {
        title: string;
        contentComponent: unknown;
        contentComponentInputs: Record<string, unknown>;
      };
    };
    const dialogData = dialogConfig?.data;

    expect(dialogData.title).toBe('Connection Error');
    expect(dialogData.contentComponent).toBe(ConnectionErrorContent);

    // Verify capabilitiesList:
    // - device-1: "ADB Console" (mapped from ADB_ONLY)
    // - device-2: "SSH" (mapped from SSH)
    // - device-3: (filtered out)
    expect(dialogData.contentComponentInputs).toEqual({
      'capabilitiesList': [
        {id: 'device-1', modes: 'ADB Console'},
        {id: 'device-2', modes: 'SSH'},
        {id: 'device-4', modes: 'Unknown'},
      ],
    });
  });

  it('startRemoteControl should show error snackbar when 0 devices are selected', () => {
    service.startRemoteControl('test-host', []).subscribe();
    expect(snackBarSpy.showError).toHaveBeenCalledWith(
      'Please select at least one device.',
    );
  });

  it('startRemoteControl should show error snackbar when > 3 devices are selected', () => {
    const devices = [
      {id: 'd1', model: 'm', isTestbed: false, subDevices: []},
      {id: 'd2', model: 'm', isTestbed: false, subDevices: []},
      {id: 'd3', model: 'm', isTestbed: false, subDevices: []},
      {id: 'd4', model: 'm', isTestbed: false, subDevices: []},
    ];
    service.startRemoteControl('test-host', devices).subscribe();
    expect(snackBarSpy.showError).toHaveBeenCalledWith(
      'Can not remote control more than 3 devices at the same time.',
    );
  });

  it('startRemoteControl should show IncompatibleDevices dialog when status is BLOCK_DEVICES_INELIGIBLE', () => {
    const devices = [
      {id: 'device-1', model: 'Pixel 9', isTestbed: false, subDevices: []},
    ];
    const mockResponse: CheckRemoteControlEligibilityResponse = {
      status: EligibilityStatus.BLOCK_DEVICES_INELIGIBLE,
      results: [
        {
          deviceId: 'device-1',
          isEligible: false,
          ineligibilityReason: {
            code: 'DEVICE_NOT_IDLE',
            message: 'Device is busy',
          },
          supportedProxyTypes: [],
        },
      ],
    };
    hostServiceSpy.checkRemoteControlEligibility.and.returnValue(
      of(mockResponse),
    );

    service.startRemoteControl('test-host', devices).subscribe();

    expect(dialogSpy.open).toHaveBeenCalledWith(
      ConfirmDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          title: 'Unable to Start Remote Control',
        }),
      }),
    );
  });

  it('startRemoteControl should show AccessDenied dialog when status is BLOCK_ALL_PERMISSION_DENIED', () => {
    const devices = [
      {id: 'device-1', model: 'Pixel 9', isTestbed: false, subDevices: []},
    ];
    const mockResponse: CheckRemoteControlEligibilityResponse = {
      status: EligibilityStatus.BLOCK_ALL_PERMISSION_DENIED,
      results: [
        {
          deviceId: 'device-1',
          isEligible: false,
          ineligibilityReason: {
            code: 'PERMISSION_DENIED',
            message: 'No access',
          },
          supportedProxyTypes: [],
        },
      ],
    };
    hostServiceSpy.checkRemoteControlEligibility.and.returnValue(
      of(mockResponse),
    );

    service.startRemoteControl('test-host', devices).subscribe();

    expect(dialogSpy.open).toHaveBeenCalledWith(
      ConfirmDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          title: 'Access Denied',
        }),
      }),
    );
  });

  it('startRemoteControl should open RemoteControlDialog when READY, and launch sessions on confirm', () => {
    const devices = [
      {id: 'device-1', model: 'Pixel 9', isTestbed: false, subDevices: []},
    ];
    const mockResponse: CheckRemoteControlEligibilityResponse = {
      status: EligibilityStatus.READY,
      results: [
        {
          deviceId: 'device-1',
          isEligible: true,
          supportedProxyTypes: [DeviceProxyType.ADB_ONLY],
        },
      ],
      sessionOptions: {
        maxDurationHours: 3,
        commonRunAsCandidates: ['user'],
        commonProxyTypes: [DeviceProxyType.ADB_ONLY],
      },
    };
    hostServiceSpy.checkRemoteControlEligibility.and.returnValue(
      of(mockResponse),
    );

    // Mock RemoteControlDialog closure with request
    const mockRequest: RemoteControlDevicesRequest = {
      deviceConfigs: [{deviceId: 'device-1', runAs: 'user', subDeviceId: ''}],
      durationSeconds: 3600,
      proxyType: DeviceProxyType.ADB_ONLY,
    };
    const mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
    mockDialogRef.afterClosed.and.returnValue(of(mockRequest));

    dialogSpy.open.and.returnValue(mockDialogRef);

    // Mock remoteControlDevices response
    const mockLaunchResponse: RemoteControlDevicesResponse = {
      sessions: [{deviceId: 'device-1', sessionUrl: 'http://session-url'}],
    };
    hostServiceSpy.remoteControlDevices.and.returnValue(of(mockLaunchResponse));

    spyOn(window, 'open');

    service.startRemoteControl('test-host', devices).subscribe();

    expect(dialogSpy.open).toHaveBeenCalledWith(
      RemoteControlDialog,
      jasmine.any(Object),
    );
    expect(hostServiceSpy.remoteControlDevices).toHaveBeenCalledWith(
      'test-host',
      mockRequest,
    );
    expect(window.open).toHaveBeenCalled();
    const openArgs = (window.open as jasmine.Spy).calls.mostRecent().args;
    expect(openArgs[0]).toBe('http://session-url');
    expect(openArgs[1]).toBe('_blank');
    expect(snackBarSpy.showSuccess).toHaveBeenCalled();
  });

  it('startRemoteControl should show error snackbar when sessionUrl is missing in launch response', () => {
    const devices = [
      {id: 'device-1', model: 'Pixel 9', isTestbed: false, subDevices: []},
    ];
    const mockResponse: CheckRemoteControlEligibilityResponse = {
      status: EligibilityStatus.READY,
      results: [
        {
          deviceId: 'device-1',
          isEligible: true,
          supportedProxyTypes: [DeviceProxyType.ADB_ONLY],
        },
      ],
      sessionOptions: {
        maxDurationHours: 3,
        commonRunAsCandidates: ['user'],
        commonProxyTypes: [DeviceProxyType.ADB_ONLY],
      },
    };
    hostServiceSpy.checkRemoteControlEligibility.and.returnValue(
      of(mockResponse),
    );

    const mockRequest: RemoteControlDevicesRequest = {
      deviceConfigs: [{deviceId: 'device-1', runAs: 'user', subDeviceId: ''}],
      durationSeconds: 3600,
      proxyType: DeviceProxyType.ADB_ONLY,
    };
    const mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
    mockDialogRef.afterClosed.and.returnValue(of(mockRequest));
    dialogSpy.open.and.returnValue(mockDialogRef);

    // Mock launch response with missing sessionUrl
    const mockLaunchResponse: RemoteControlDevicesResponse = {
      sessions: [{deviceId: 'device-1', sessionUrl: ''}],
    };
    hostServiceSpy.remoteControlDevices.and.returnValue(of(mockLaunchResponse));

    service.startRemoteControl('test-host', devices).subscribe();

    expect(snackBarSpy.showError).toHaveBeenCalledWith(
      'Invalid session URL for device device-1',
    );
  });

  it('startRemoteControl should show error snackbar when checkRemoteControlEligibility fails', () => {
    const devices = [
      {id: 'device-1', model: 'Pixel 9', isTestbed: false, subDevices: []},
    ];
    hostServiceSpy.checkRemoteControlEligibility.and.returnValue(
      throwError(() => new Error('Network error')),
    );

    service.startRemoteControl('test-host', devices).subscribe({
      error: (err) => {
        expect(err.message).toBe('Network error');
      },
    });

    expect(snackBarSpy.showError).toHaveBeenCalledWith(
      'Failed to check proxy compatibility: Network error',
    );
  });

  it('startRemoteControl should show error snackbar when remoteControlDevices fails', () => {
    const devices = [
      {id: 'device-1', model: 'Pixel 9', isTestbed: false, subDevices: []},
    ];
    const mockResponse: CheckRemoteControlEligibilityResponse = {
      status: EligibilityStatus.READY,
      results: [
        {
          deviceId: 'device-1',
          isEligible: true,
          supportedProxyTypes: [DeviceProxyType.ADB_ONLY],
        },
      ],
      sessionOptions: {
        maxDurationHours: 3,
        commonRunAsCandidates: ['user'],
        commonProxyTypes: [DeviceProxyType.ADB_ONLY],
      },
    };
    hostServiceSpy.checkRemoteControlEligibility.and.returnValue(
      of(mockResponse),
    );

    const mockRequest: RemoteControlDevicesRequest = {
      deviceConfigs: [{deviceId: 'device-1', runAs: 'user', subDeviceId: ''}],
      durationSeconds: 3600,
      proxyType: DeviceProxyType.ADB_ONLY,
    };
    const mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
    mockDialogRef.afterClosed.and.returnValue(of(mockRequest));
    dialogSpy.open.and.returnValue(mockDialogRef);

    hostServiceSpy.remoteControlDevices.and.returnValue(
      throwError(() => new Error('Server error')),
    );

    service.startRemoteControl('test-host', devices).subscribe({
      error: (err) => {
        expect(err.message).toBe('Server error');
      },
    });

    expect(snackBarSpy.showError).toHaveBeenCalledWith(
      'Failed to start remote control: Server error',
    );
  });

  it('startRemoteControl should show single device ConnectionError dialog when 1 device has no common proxy', () => {
    const devices = [
      {id: 'device-1', model: 'Pixel 9', isTestbed: false, subDevices: []},
    ];
    const mockResponse: CheckRemoteControlEligibilityResponse = {
      status: EligibilityStatus.BLOCK_NO_COMMON_PROXY,
      results: [
        {
          deviceId: 'device-1',
          isEligible: true,
          supportedProxyTypes: [], // No common proxy types
        },
      ],
    };
    hostServiceSpy.checkRemoteControlEligibility.and.returnValue(
      of(mockResponse),
    );

    service.startRemoteControl('test-host', devices).subscribe();

    expect(dialogSpy.open).toHaveBeenCalledWith(
      ConfirmDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          title: 'Connection Error',
          contentComponentInputs: jasmine.objectContaining({
            device: {id: 'device-1'},
            isTestbed: false,
          }),
        }),
      }),
    );
  });
});
