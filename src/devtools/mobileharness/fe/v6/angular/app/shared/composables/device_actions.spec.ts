import {TestBed} from '@angular/core/testing';

import {of, Subject, throwError} from 'rxjs';

import {
  GetLogcatResponse,
  TakeScreenshotResponse,
} from '../../core/models/device_action';
import {DeviceOverview, SubDeviceInfo} from '../../core/models/device_overview';
import {DeviceSummary} from '../../core/models/host_overview';
import {DeviceActionService} from '../services/device_action_service';
import {RemoteControlService} from '../services/remote_control_service';
import {useDeviceActions} from './device_actions';

describe('useDeviceActions', () => {
  let actionServiceSpy: jasmine.SpyObj<DeviceActionService>;
  let remoteControlServiceSpy: jasmine.SpyObj<RemoteControlService>;
  beforeEach(() => {
    actionServiceSpy = jasmine.createSpyObj('DeviceActionService', [
      'takeScreenshot',
      'getLogcat',
      'quarantineDevice',
      'flashDevice',
      'changeQuarantine',
    ]);
    remoteControlServiceSpy = jasmine.createSpyObj('RemoteControlService', [
      'startRemoteControl',
    ]);
    remoteControlServiceSpy.startRemoteControl.and.returnValue(of(undefined));

    TestBed.configureTestingModule({
      providers: [
        {provide: DeviceActionService, useValue: actionServiceSpy},
        {provide: RemoteControlService, useValue: remoteControlServiceSpy},
      ],
    });
  });

  it('should return cached computed signal when isRunning is called multiple times (Line 29)', () => {
    TestBed.runInInjectionContext(() => {
      const {isRunning} = useDeviceActions();

      const signal1 = isRunning('screenshot');
      const signal2 = isRunning('screenshot');

      expect(signal1).toBe(signal2); // Exactly the same instance!
    });
  });

  it('should transition loading state through true/false during takeScreenshot action lifecycle (Line 38, 41)', () => {
    const screenshotSubject = new Subject<TakeScreenshotResponse>();
    actionServiceSpy.takeScreenshot.and.returnValue(
      screenshotSubject.asObservable(),
    );

    TestBed.runInInjectionContext(() => {
      const {isRunning, takeScreenshot} = useDeviceActions();

      const loading = isRunning('screenshot');

      // 1. Initial state -> false
      expect(loading()).toBeFalse();

      // 2. Action started -> true
      takeScreenshot('device-1');
      expect(loading()).toBeTrue();

      // 3. Action completed -> false
      screenshotSubject.next({
        screenshotUrl: 'http://mock-url',
        capturedAt: '123456',
      });
      screenshotSubject.complete();
      expect(loading()).toBeFalse();
    });
  });

  it('should transition loading state back to false if action errors out (Line 41)', () => {
    const screenshotSubject = new Subject<TakeScreenshotResponse>();
    actionServiceSpy.takeScreenshot.and.returnValue(
      screenshotSubject.asObservable(),
    );

    TestBed.runInInjectionContext(() => {
      const {isRunning, takeScreenshot} = useDeviceActions();

      const loading = isRunning('screenshot');

      takeScreenshot('device-1');
      expect(loading()).toBeTrue();

      // Action errored -> false
      screenshotSubject.error(new Error('Failed'));
      expect(loading()).toBeFalse();
    });
  });

  it('should map DeviceSummary testbed devices correctly (Line 78)', () => {
    TestBed.runInInjectionContext(() => {
      const {startRemoteControl} = useDeviceActions();

      const mockDeviceSummaryTestbed = {
        id: 'device-1',
        model: 'Pixel 9 TestBed',
        types: [{type: 'TestbedDevice'}],
        subDevices: [{id: 'sub-1', model: 'Pixel 8'}],
      };

      startRemoteControl(
        'host-1',
        mockDeviceSummaryTestbed as unknown as DeviceSummary,
      );

      expect(remoteControlServiceSpy.startRemoteControl).toHaveBeenCalledWith(
        'host-1',
        [
          {
            id: 'device-1',
            model: 'Pixel 9 TestBed',
            isTestbed: true,
            subDevices: [
              {id: 'sub-1', model: 'Pixel 8'},
            ] as unknown as SubDeviceInfo[],
          },
        ],
        false,
      );
    });
  });

  it('should map regular DeviceSummary devices with isTestbed: false', () => {
    TestBed.runInInjectionContext(() => {
      const {startRemoteControl} = useDeviceActions();

      const mockDeviceSummaryRegular = {
        id: 'device-2',
        model: 'Pixel 9',
        types: [{type: 'AndroidDevice'}],
        subDevices: [],
      };

      startRemoteControl(
        'host-1',
        mockDeviceSummaryRegular as unknown as DeviceSummary,
      );

      expect(remoteControlServiceSpy.startRemoteControl).toHaveBeenCalledWith(
        'host-1',
        [
          {
            id: 'device-2',
            model: 'Pixel 9',
            isTestbed: false,
            subDevices: [],
          },
        ],
        false,
      );
    });
  });

  it('should map DeviceOverview testbed devices correctly', () => {
    TestBed.runInInjectionContext(() => {
      const {startRemoteControl} = useDeviceActions();

      const mockDeviceOverviewTestbed = {
        id: 'device-3',
        basicInfo: {model: 'Pixel 9 TestBed'},
        healthAndActivity: {deviceTypes: [{type: 'TestbedDevice'}]},
        subDevices: [{id: 'sub-1', model: 'Pixel 8'}],
      };

      startRemoteControl(
        'host-1',
        mockDeviceOverviewTestbed as unknown as DeviceOverview,
      );

      expect(remoteControlServiceSpy.startRemoteControl).toHaveBeenCalledWith(
        'host-1',
        [
          {
            id: 'device-3',
            model: 'Pixel 9 TestBed',
            isTestbed: true,
            subDevices: [
              {id: 'sub-1', model: 'Pixel 8'},
            ] as unknown as SubDeviceInfo[],
          },
        ],
        false,
      );
    });
  });

  it('should map sub-device remote control options correctly (Line 72, 84, 85)', () => {
    TestBed.runInInjectionContext(() => {
      const {startRemoteControl} = useDeviceActions();

      const mockDeviceSummary = {
        id: 'device-1',
        model: 'Pixel 9',
        types: [{type: 'AndroidDevice'}],
        subDevices: [],
      };
      const mockSubDevice = {id: 'sub-1', model: 'Pixel 8'};

      startRemoteControl(
        'host-1',
        mockDeviceSummary as unknown as DeviceSummary,
        {
          isSubDevice: true,
          subDeviceOnly: mockSubDevice as unknown as SubDeviceInfo,
        },
      );

      expect(remoteControlServiceSpy.startRemoteControl).toHaveBeenCalledWith(
        'host-1',
        [
          {
            id: 'device-1',
            model: 'Pixel 9',
            isTestbed: true, // Force true because sub-device is active!
            subDevices: [mockSubDevice] as unknown as SubDeviceInfo[],
          },
        ],
        true, // isSubDevice is true!
      );
    });
  });

  it('should map regular DeviceOverview devices with isTestbed: false', () => {
    TestBed.runInInjectionContext(() => {
      const {startRemoteControl} = useDeviceActions();

      const mockDeviceOverviewRegular = {
        id: 'device-4',
        basicInfo: {model: 'Pixel 9'},
        healthAndActivity: {deviceTypes: [{type: 'AndroidDevice'}]},
        subDevices: [],
      };

      startRemoteControl(
        'host-1',
        mockDeviceOverviewRegular as unknown as DeviceOverview,
      );

      expect(remoteControlServiceSpy.startRemoteControl).toHaveBeenCalledWith(
        'host-1',
        [
          {
            id: 'device-4',
            model: 'Pixel 9',
            isTestbed: false,
            subDevices: [],
          },
        ],
        false,
      );
    });
  });

  it('getLogcat should delegate to DeviceActionService and transition loading states', () => {
    const logcatSubject = new Subject<GetLogcatResponse>();
    actionServiceSpy.getLogcat.and.returnValue(logcatSubject.asObservable());

    TestBed.runInInjectionContext(() => {
      const {isRunning, getLogcat} = useDeviceActions();

      const loading = isRunning('logcat');
      expect(loading()).toBeFalse();

      getLogcat('device-1');
      expect(loading()).toBeTrue();
      expect(actionServiceSpy.getLogcat).toHaveBeenCalledWith('device-1');

      logcatSubject.next({
        logUrl: 'http://mock-log-url',
        capturedAt: '123456',
      });
      logcatSubject.complete();
      expect(loading()).toBeFalse();
    });
  });

  it('flashDevice should delegate to DeviceActionService', () => {
    TestBed.runInInjectionContext(() => {
      const {flashDevice} = useDeviceActions();

      flashDevice('device-1', 'host-1', {deviceType: 'Pixel'});

      expect(actionServiceSpy.flashDevice).toHaveBeenCalledWith(
        'device-1',
        'host-1',
        {deviceType: 'Pixel'},
      );
    });
  });

  it('quarantineDevice should delegate to DeviceActionService and trigger onSuccess', () => {
    const quarantineSubject = new Subject<unknown>();
    actionServiceSpy.quarantineDevice.and.returnValue(
      quarantineSubject.asObservable(),
    );

    TestBed.runInInjectionContext(() => {
      const {isRunning, quarantineDevice} = useDeviceActions();

      const loading = isRunning('quarantine');
      expect(loading()).toBeFalse();

      const onSuccessSpy = jasmine.createSpy('onSuccess');

      quarantineDevice('device-1', {
        quarantineInfo: {isQuarantined: false},
        onSuccess: onSuccessSpy,
      });

      expect(loading()).toBeTrue();
      expect(actionServiceSpy.quarantineDevice).toHaveBeenCalledWith(
        'device-1',
        jasmine.objectContaining({quarantineInfo: {isQuarantined: false}}),
      );

      quarantineSubject.next({});
      quarantineSubject.complete();

      expect(loading()).toBeFalse();
      expect(onSuccessSpy).toHaveBeenCalled();
    });
  });

  it('changeQuarantine should delegate to DeviceActionService', () => {
    actionServiceSpy.changeQuarantine.and.returnValue(of(undefined));

    TestBed.runInInjectionContext(() => {
      const {changeQuarantine} = useDeviceActions();

      changeQuarantine('device-1', 'expiry-time');

      expect(actionServiceSpy.changeQuarantine).toHaveBeenCalledWith(
        'device-1',
        'expiry-time',
      );
    });
  });

  it('should unsubscribe from action stream when context is destroyed', () => {
    const screenshotSubject = new Subject<TakeScreenshotResponse>();
    actionServiceSpy.takeScreenshot.and.returnValue(
      screenshotSubject.asObservable(),
    );

    TestBed.runInInjectionContext(() => {
      const {takeScreenshot} = useDeviceActions();

      takeScreenshot('device-1');
      expect(screenshotSubject.observers.length).toBeGreaterThan(0); // Active subscription exists!
    });

    // Reset testing module -> destroys the TestBed injector and triggers unsubscription!
    TestBed.resetTestingModule();

    expect(screenshotSubject.observers.length).toBe(0); // Subscription unsubscribed!
  });

  it('should map DeviceSummary devices with TestbedDevice type but no sub-devices as isTestbed: false (Line 78)', () => {
    TestBed.runInInjectionContext(() => {
      const {startRemoteControl} = useDeviceActions();

      const mockDeviceSummaryInvalidTestbed = {
        id: 'device-1',
        model: 'Pixel 9 TestBed',
        types: [{type: 'TestbedDevice'}],
        subDevices: [],
      };

      startRemoteControl(
        'host-1',
        mockDeviceSummaryInvalidTestbed as unknown as DeviceSummary,
      );

      expect(remoteControlServiceSpy.startRemoteControl).toHaveBeenCalledWith(
        'host-1',
        [
          {
            id: 'device-1',
            model: 'Pixel 9 TestBed',
            isTestbed: false,
            subDevices: [],
          },
        ],
        false,
      );
    });
  });

  it('quarantineDevice should transition loading states through true/false during action lifecycle (Line 38, 41)', () => {
    const quarantineSubject = new Subject<unknown>();
    actionServiceSpy.quarantineDevice.and.returnValue(
      quarantineSubject.asObservable(),
    );

    TestBed.runInInjectionContext(() => {
      const {isRunning, quarantineDevice} = useDeviceActions();

      const loading = isRunning('quarantine');
      expect(loading()).toBeFalse();

      quarantineDevice('device-1');
      expect(loading()).toBeTrue();

      quarantineSubject.next(true);
      quarantineSubject.complete();
      expect(loading()).toBeFalse();
    });
  });

  it('should map an array of DeviceSummary devices correctly', () => {
    TestBed.runInInjectionContext(() => {
      const {startRemoteControl} = useDeviceActions();

      const mockDevice1 = {
        id: 'device-1',
        model: 'Pixel 9',
        types: [],
        subDevices: [],
      };
      const mockDevice2 = {
        id: 'device-2',
        model: 'Pixel 8',
        types: [],
        subDevices: [],
      };

      startRemoteControl('host-1', [
        mockDevice1,
        mockDevice2,
      ] as unknown as DeviceSummary[]);

      expect(remoteControlServiceSpy.startRemoteControl).toHaveBeenCalledWith(
        'host-1',
        [
          {id: 'device-1', model: 'Pixel 9', isTestbed: false, subDevices: []},
          {id: 'device-2', model: 'Pixel 8', isTestbed: false, subDevices: []},
        ],
        false,
      );
    });
  });

  it('should map regular DeviceOverview with missing healthAndActivity as isTestbed: false', () => {
    TestBed.runInInjectionContext(() => {
      const {startRemoteControl} = useDeviceActions();

      const mockDeviceOverviewMissing = {
        id: 'device-5',
        basicInfo: {model: 'Pixel 9'},
        subDevices: [],
      };

      startRemoteControl(
        'host-1',
        mockDeviceOverviewMissing as unknown as DeviceOverview,
      );

      expect(remoteControlServiceSpy.startRemoteControl).toHaveBeenCalledWith(
        'host-1',
        [{id: 'device-5', model: 'Pixel 9', isTestbed: false, subDevices: []}],
        false,
      );
    });
  });

  it('should map DeviceSummary devices with sub-devices but not TestbedDevice type as isTestbed: false', () => {
    TestBed.runInInjectionContext(() => {
      const {startRemoteControl} = useDeviceActions();

      const mockDeviceSummaryWithSubDevicesNotTestbed = {
        id: 'device-1',
        model: 'Pixel 9 with Sub',
        types: [{type: 'AndroidDevice'}],
        subDevices: [{id: 'sub-1', model: 'Pixel 8'}],
      };

      startRemoteControl(
        'host-1',
        mockDeviceSummaryWithSubDevicesNotTestbed as unknown as DeviceSummary,
      );

      expect(remoteControlServiceSpy.startRemoteControl).toHaveBeenCalledWith(
        'host-1',
        [
          {
            id: 'device-1',
            model: 'Pixel 9 with Sub',
            isTestbed: false,
            subDevices: [
              {id: 'sub-1', model: 'Pixel 8'},
            ] as unknown as SubDeviceInfo[],
          },
        ],
        false,
      );
    });
  });

  it('changeQuarantine should handle error gracefully', () => {
    actionServiceSpy.changeQuarantine.and.returnValue(
      throwError(() => new Error('Change failed')),
    );

    TestBed.runInInjectionContext(() => {
      const {changeQuarantine} = useDeviceActions();

      expect(() => {
        changeQuarantine('device-1', 'expiry-time');
      }).not.toThrow();
    });
  });
});
