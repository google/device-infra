import {TestBed} from '@angular/core/testing';
import {
  ConfigSection,
  DeviceConfig,
  DeviceConfigChangeStatus,
  DeviceConfigUiStatus,
  DimensionList,
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
      expect(res.candidateWifis![0].wifi.ssid).toBe('GoogleGuest');
      done();
    });
  });

  it('should batch get device wifi configs from scenarios', (done) => {
    service
      .batchGetDeviceWifiConfigs({
        devices: [
          {deviceId: '43021FDAQ000UM', hostName: 'host-1'},
          {deviceId: 'HOST-DEVICE-001-ineligible-no-acid'},
        ],
      })
      .subscribe((res) => {
        expect(res).toBeDefined();
        expect(res.entries).toBeDefined();
        expect(res.entries!.length).toBe(2);
        expect(res.entries![0].deviceId).toBe('43021FDAQ000UM');
        expect(res.entries![0].hostName).toBe('host-1');
        expect(res.entries![0].currentWifi?.ssid).toBe('GoogleGuest');
        expect(res.entries![0].status).toBe(
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WRITABLE,
        );
        expect(res.entries![1].deviceId).toBe(
          'HOST-DEVICE-001-ineligible-no-acid',
        );
        expect(res.entries![1].status).toBe(
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        );
        expect(res.entries![1].readOnlyReason).toContain(
          'Managed by host shared config',
        );
        done();
      });
  });

  it('should retrieve recommended wifi list', (done) => {
    service.getRecommendedWifi().subscribe((res) => {
      expect(res).toBeDefined();
      expect(Array.isArray(res)).toBeTrue();
      expect(res.length).toBeGreaterThan(0);
      done();
    });
  });

  it('should evaluate target matching when target is specified in batchGetDeviceWifiConfigs', (done) => {
    service
      .batchGetDeviceWifiConfigs({
        devices: [
          {deviceId: '43021FDAQ000UM'},
          {deviceId: 'non-existent-device-id'},
        ],
        target: {
          wifi: {
            ssid: 'GoogleGuest',
            psk: 'some-secure-password',
            scanSsid: true,
          },
        },
      })
      .subscribe((res) => {
        expect(res.entries).toBeDefined();
        expect(res.entries!.length).toBe(2);
        // 43021FDAQ000UM has GoogleGuest with some-secure-password and scanSsid true
        expect(res.entries![0].deviceId).toBe('43021FDAQ000UM');
        expect(res.entries![0].status).toBe(
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
        );
        // Unknown device
        expect(res.entries![1].deviceId).toBe('non-existent-device-id');
        expect(res.entries![1].status).toBe(
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        );
        expect(res.entries![1].readOnlyReason).toBe('Device not found');
        done();
      });
  });

  it('should evaluate clearWifi target in batchGetDeviceWifiConfigs', (done) => {
    service
      .batchGetDeviceWifiConfigs({
        devices: [
          {deviceId: '43021FDAQ000UM'},
          {deviceId: 'mtt-host.example.com:NoOpDevice-4'},
        ],
        target: {clearWifi: {}},
      })
      .subscribe((res) => {
        expect(res.entries).toBeDefined();
        // 43021FDAQ000UM has wifi -> will update to clear
        expect(res.entries![0].deviceId).toBe('43021FDAQ000UM');
        expect(res.entries![0].status).toBe(
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
        );
        // NoOpDevice-4 is owned by tianch only -> not owner
        expect(res.entries![1].deviceId).toBe(
          'mtt-host.example.com:NoOpDevice-4',
        );
        expect(res.entries![1].status).toBe(
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        );
        expect(res.entries![1].readOnlyReason).toContain('No permission');
        done();
      });
  });

  it('should batch update device wifi configs and return scenario errors', (done) => {
    service
      .batchUpdateDeviceWifiConfigs({
        devices: [
          {deviceId: '43021FDAQ000UM'},
          {deviceId: 'android-logical-error-01'},
          {deviceId: 'unknown-device'},
          {deviceId: 'HOST-DEVICE-001-ineligible-no-acid'},
        ],
        target: {wifi: {ssid: 'new-wifi', psk: 'pass', scanSsid: false}},
      })
      .subscribe((res) => {
        expect(res.errors).toBeDefined();
        expect(res.errors!['43021FDAQ000UM']).toBeUndefined();
        expect(res.errors!['android-logical-error-01']).toEqual({
          code: 'VALIDATION_ERROR',
          message: 'Device offline or unreachable',
        });
        expect(res.errors!['unknown-device']).toEqual({
          code: 'UNKNOWN',
          message: 'Device not found',
        });
        expect(res.errors!['HOST-DEVICE-001-ineligible-no-acid']).toEqual({
          code: 'PERMISSION_DENIED',
          message: 'Managed by host shared config (update host config instead)',
        });
        done();
      });
  });

  it('should batch update device wifi configs with clearWifi', (done) => {
    service
      .batchUpdateDeviceWifiConfigs({
        devices: [{deviceId: '43021FDAQ000UM'}],
        target: {clearWifi: {}},
      })
      .subscribe((res) => {
        expect(res.errors).toEqual({});
        done();
      });
  });

  it('should mark device read-only when wifiStatus visible is false', (done) => {
    (
      service as unknown as {mockDeviceScenarios: unknown[]}
    ).mockDeviceScenarios.push({
      id: 'unsupported-wifi-device',
      configUiStatus: {wifi: {visible: false}},
    });
    service
      .batchGetDeviceWifiConfigs({
        devices: [{deviceId: 'unsupported-wifi-device'}],
      })
      .subscribe((res) => {
        expect(res.entries![0].status).toBe(
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        );
        expect(res.entries![0].readOnlyReason).toBe(
          'Wi-Fi configuration is not supported on this device',
        );
        done();
      });
  });

  it('should mark device read-only when wifiStatus editability is false', (done) => {
    (
      service as unknown as {mockDeviceScenarios: unknown[]}
    ).mockDeviceScenarios.push({
      id: 'uneditable-wifi-device',
      configUiStatus: {
        wifi: {
          visible: true,
          editability: {editable: false, reason: 'Device locked by admin'},
        },
      },
    });
    service
      .batchGetDeviceWifiConfigs({
        devices: [{deviceId: 'uneditable-wifi-device'}],
      })
      .subscribe((res) => {
        expect(res.entries![0].status).toBe(
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        );
        expect(res.entries![0].readOnlyReason).toBe('Device locked by admin');
        done();
      });
  });

  it('should use fallback reason when wifiStatus editability reason is empty', (done) => {
    (
      service as unknown as {mockDeviceScenarios: unknown[]}
    ).mockDeviceScenarios.push({
      id: 'uneditable-no-reason-device',
      configUiStatus: {
        wifi: {
          visible: true,
          editability: {editable: false},
        },
      },
    });
    service
      .batchGetDeviceWifiConfigs({
        devices: [{deviceId: 'uneditable-no-reason-device'}],
      })
      .subscribe((res) => {
        expect(res.entries![0].status).toBe(
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        );
        expect(res.entries![0].readOnlyReason).toBe(
          'Wi-Fi configuration is read-only',
        );
        done();
      });
  });

  it('should initialize scenario config when updating device without existing config', (done) => {
    (
      service as unknown as {mockDeviceScenarios: unknown[]}
    ).mockDeviceScenarios.push({
      id: 'device-without-config',
      config: undefined,
    });
    service
      .batchUpdateDeviceWifiConfigs({
        devices: [{deviceId: 'device-without-config'}],
        target: {wifi: {ssid: 'fresh-network', psk: 'secret', scanSsid: true}},
      })
      .subscribe((res) => {
        expect(res.errors).toEqual({});
        const updated = (
          service as unknown as {
            mockDeviceScenarios: Array<{id: string; config?: DeviceConfig}>;
          }
        ).mockDeviceScenarios.find((s) => s.id === 'device-without-config');
        expect(updated?.config).toBeDefined();
        expect(updated?.config?.wifi?.ssid).toBe('fresh-network');
        expect(updated?.config?.wifi?.scanSsid).toBeTrue();
        done();
      });
  });

  it('should detect when SSID matches but PSK or scanSsid differs in target matching', (done) => {
    service
      .batchGetDeviceWifiConfigs({
        devices: [{deviceId: '43021FDAQ000UM'}],
        target: {
          wifi: {
            ssid: 'GoogleGuest',
            psk: 'different-psk',
            scanSsid: true,
          },
        },
      })
      .subscribe((res) => {
        expect(res.entries![0].status).toBe(
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
        );
        done();
      });
  });

  it('should detect when device has no wifi and clearWifi target is specified', (done) => {
    (
      service as unknown as {mockDeviceScenarios: unknown[]}
    ).mockDeviceScenarios.push({
      id: 'device-with-no-wifi',
      config: {
        permissions: {owners: ['derekchen']},
        wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
      },
    });
    service
      .batchGetDeviceWifiConfigs({
        devices: [{deviceId: 'device-with-no-wifi'}],
        target: {clearWifi: {}},
      })
      .subscribe((res) => {
        expect(res.entries![0].status).toBe(
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
        );
        done();
      });
  });

  it('should return curated presets and fleet wifis sorted in getWifiSuggestions', (done) => {
    service.getWifiSuggestions().subscribe((res) => {
      const candidates = res.candidateWifis || [];
      expect(candidates.length).toBeGreaterThan(1);
      const curated = candidates.filter((c) => c.curatedPreset != null);
      const fleet = candidates.filter((c) => c.fleetInUse != null);
      expect(curated.length).toBeGreaterThan(0);
      expect(fleet.length).toBeGreaterThan(0);
      for (let i = 1; i < fleet.length; i++) {
        expect(fleet[i - 1].fleetInUse!.deviceCount!).toBeGreaterThanOrEqual(
          fleet[i].fleetInUse!.deviceCount!,
        );
      }
      done();
    });
  });

  it('should retrieve configurable dimensions', (done) => {
    service.getConfigurableDimensions().subscribe((res) => {
      expect(res).toBeDefined();
      expect(res.dimensions.length).toBeGreaterThan(0);
      expect(res.dimensions[0].dimensionName).toBe('recovery');
      expect(res.dimensions[0].dimensionList).toBe(
        DimensionList.DIMENSION_LIST_SUPPORTED,
      );
      expect(res.allowCustomDimensions).toBeTrue();
      done();
    });
  });

  it('should retrieve configurable dimension keys and system dimension names', (done) => {
    service
      .batchGetConfigurableDimensionKeys({
        devices: [{deviceId: 'dev-1'}, {deviceId: 'dev-2'}],
        dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
      })
      .subscribe((res) => {
        expect(res).toBeDefined();
        expect(res.candidateKeys!.length).toBeGreaterThan(0);
        expect(res.systemDimensionNames).toContain('model');
        done();
      });
  });

  it('should retrieve dimension value suggestions', (done) => {
    service
      .getDimensionValueSuggestions({
        dimensionName: 'recovery',
        dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
      })
      .subscribe((res) => {
        expect(res).toBeDefined();
        expect(res.candidateValues!.length).toBeGreaterThan(0);
        expect(res.candidateValues![0].values).toEqual(['true']);
        done();
      });
  });

  it('should retrieve device dimension configs and update them in batch', (done) => {
    service
      .batchGetDeviceDimensionConfigs({
        devices: [
          {deviceId: 'dev-1', hostName: 'host-1'},
          {deviceId: 'FailedDevice-0', hostName: 'host-2'},
        ],
        dimensionName: 'recovery',
        dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
        target: {valueList: {values: ['true']}},
      })
      .subscribe((res) => {
        expect(res).toBeDefined();
        expect(res.entries!.length).toBe(2);
        expect(res.entries![0].hostName).toBe('host-1');
        expect(res.entries![1].status).toBe(
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        );
        expect(res.entries![1].readOnlyReason).toContain(
          'Unrecognizable or invalid device ID',
        );

        service
          .batchUpdateDeviceDimensionConfigs({
            devices: [{deviceId: 'dev-1'}, {deviceId: 'fail-device-2'}],
            dimensionName: 'recovery',
            dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
            target: {valueList: {values: ['true']}},
          })
          .subscribe((updateRes) => {
            expect(updateRes.errors).toBeDefined();
            expect(updateRes.errors!['fail-device-2']).toBeDefined();
            done();
          });
      });
  });
});
