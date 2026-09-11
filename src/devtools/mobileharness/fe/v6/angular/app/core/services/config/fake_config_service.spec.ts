import {TestBed} from '@angular/core/testing';
import {
  ConfigSection,
  DeviceConfig,
  DeviceConfigUiStatus,
  UpdateDeviceConfigRequest,
  UpdateDeviceConfigResult,
} from '../../models/device_config_models';
import {
  HostConfig,
  HostConfigUiStatus,
  UpdateHostConfigRequest,
  UpdateHostConfigResult,
} from '../../models/host_config_models';
import {CONFIG_SERVICE} from './config_service';
import {FakeConfigService} from './fake_config_service';

interface FakeUpdateDeviceConfigResult extends UpdateDeviceConfigResult {
  deviceConfig?: DeviceConfig;
  uiStatus?: Partial<DeviceConfigUiStatus>;
}

interface FakeUpdateHostConfigResult extends UpdateHostConfigResult {
  hostConfig?: HostConfig;
  uiStatus?: HostConfigUiStatus;
}

describe('FakeConfigService', () => {
  let service: FakeConfigService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: CONFIG_SERVICE,
          useClass: FakeConfigService,
        },
      ],
    });
    service = TestBed.inject(CONFIG_SERVICE) as FakeConfigService;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should update device config with ConfigSection.ALL and return uiStatus', (done) => {
    const request: UpdateDeviceConfigRequest = {
      id: '43021FDAQ000UM',
      section: ConfigSection.ALL,
      config: {
        permissions: {owners: ['derekchen'], executors: []},
        wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
        dimensions: {supported: [], required: []},
        settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 1000},
      },
    };

    service
      .updateDeviceConfig(request)
      .subscribe((res: FakeUpdateDeviceConfigResult) => {
        expect(res).toBeDefined();
        expect(res.success).toBeTrue();
        expect(res.deviceConfig).toBeDefined();
        expect(res.uiStatus).toBeDefined();
        done();
      });
  });

  it('should update device config with ConfigSection.WIFI and return uiStatus', (done) => {
    const request: UpdateDeviceConfigRequest = {
      id: '43021FDAQ000UM',
      section: ConfigSection.WIFI,
      config: {
        permissions: {owners: ['derekchen'], executors: []},
        wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
        dimensions: {supported: [], required: []},
        settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 1000},
      },
    };

    service
      .updateDeviceConfig(request)
      .subscribe((res: FakeUpdateDeviceConfigResult) => {
        expect(res).toBeDefined();
        expect(res.success).toBeTrue();
        expect(res.deviceConfig).toBeDefined();
        expect(res.uiStatus).toBeDefined();
        done();
      });
  });

  it('should update device config for WIFI_DIMENSIONS_ONLY_DEVICE and return specific uiStatus', (done) => {
    const request: UpdateDeviceConfigRequest = {
      id: 'WIFI_DIMENSIONS_ONLY_DEVICE',
      section: ConfigSection.ALL,
      config: {
        permissions: {owners: ['derekchen'], executors: []},
        wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
        dimensions: {supported: [], required: []},
        settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 1000},
      },
    };

    service
      .updateDeviceConfig(request)
      .subscribe((res: FakeUpdateDeviceConfigResult) => {
        expect(res).toBeDefined();
        expect(res.success).toBeTrue();
        expect(res.uiStatus!.permissions!.visible).toBeFalse();
        expect(res.uiStatus!.wifi!.visible).toBeTrue();
        done();
      });
  });

  it('should update host config and return hostConfig and uiStatus', (done) => {
    const request: UpdateHostConfigRequest = {
      hostName: 'host-basic-editable.example.com',
      config: {
        permissions: {hostAdmins: ['derekchen']},
        deviceConfigMode: 'PER_DEVICE',
        deviceConfig: {
          permissions: {owners: ['derekchen'], executors: []},
          wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
          dimensions: {supported: [], required: []},
          settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 1000},
        },
        hostProperties: [],
        deviceDiscovery: {
          monitoredDeviceUuids: [],
          testbedUuids: [],
          miscDeviceUuids: [],
          overTcpIps: [],
          overSshDevices: [],
          manekiSpecs: [],
        },
      },
    };

    service
      .updateHostConfig(request)
      .subscribe((res: FakeUpdateHostConfigResult) => {
        expect(res).toBeDefined();
        expect(res.success).toBeTrue();
        expect(res.hostConfig).toBeDefined();
        expect(res.uiStatus).toBeDefined();
        done();
      });
  });

  it('should unlock host properties', (done) => {
    service
      .unlockHostProperties('host-basic-editable.example.com')
      .subscribe((res) => {
        expect(res).toBeDefined();
        expect(res.success).toBeTrue();
        done();
      });
  });

  it('should retrieve wifi suggestions', (done) => {
    service.getWifiSuggestions().subscribe((res) => {
      expect(res).toBeDefined();
      expect(res.candidateWifis).toBeDefined();
      expect(res.candidateWifis!.length).toBeGreaterThan(0);
      expect(res.candidateWifis![0].ssid).toBe('lab-guest-5g');
      done();
    });
  });

  it('should batch get device wifi configs from scenarios', (done) => {
    service
      .batchGetDeviceWifiConfigs({
        deviceIds: ['43021FDAQ000UM', 'HOST-DEVICE-001-ineligible-no-acid'],
      })
      .subscribe((res) => {
        expect(res).toBeDefined();
        expect(res.devices).toBeDefined();
        expect(res.devices!.length).toBe(2);
        expect(res.devices![0].deviceId).toBe('43021FDAQ000UM');
        expect(res.devices![0].wifi?.ssid).toBe('GoogleGuest');
        expect(res.devices![0].writable).toBeTrue();
        expect(res.devices![1].deviceId).toBe(
          'HOST-DEVICE-001-ineligible-no-acid',
        );
        expect(res.devices![1].writable).toBeFalse();
        expect(res.devices![1].unwritableReason).toContain(
          'Managed by host shared config',
        );
        done();
      });
  });

  it('should batch update device wifi configs and return scenario errors', (done) => {
    service
      .batchUpdateDeviceWifiConfigs({
        deviceIds: ['43021FDAQ000UM', 'android-logical-error-01'],
        wifi: {ssid: 'new-wifi', psk: 'pass', scanSsid: false},
      })
      .subscribe((res) => {
        expect(res.errors).toBeDefined();
        expect(res.errors!['43021FDAQ000UM']).toBeUndefined();
        expect(res.errors!['android-logical-error-01']).toEqual({
          code: 'VALIDATION_ERROR',
          message: 'Device offline or unreachable',
        });
        done();
      });
  });
});
