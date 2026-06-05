import {DEFAULT_HOST_CONFIG} from '../constants/host_config_constants';
import {HostConfig, HostProperty} from '../models/host_config_models';
import {
  clearEmptyProperties,
  hasEmptyProperties,
  normalizeHostConfig,
} from './host_config_utils';

describe('hostConfigUtils', () => {
  it('should return DEFAULT_HOST_CONFIG if config is null', () => {
    expect(normalizeHostConfig(null)).toEqual(DEFAULT_HOST_CONFIG);
  });

  it('should merge partial config with default config', () => {
    const partialConfig: Partial<HostConfig> = {
      permissions: {
        hostAdmins: ['admin1'],
      },
      deviceConfigMode: 'SHARED',
      hostProperties: [{key: 'k1', value: 'v1'}],
    };

    const expectedConfig: HostConfig = {
      permissions: {
        hostAdmins: ['admin1'],
      },
      deviceConfigMode: 'SHARED',
      deviceConfig: {
        permissions: {owners: [], executors: []},
        wifi: {type: 'none', ssid: 'GoogleGuest', psk: '', scanSsid: false},
        dimensions: {supported: [], required: []},
        settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
      },
      deviceDiscovery: {
        monitoredDeviceUuids: [],
        testbedUuids: [],
        miscDeviceUuids: [],
        overTcpIps: [],
        overSshDevices: [],
        manekiSpecs: [],
      },
      hostProperties: [{key: 'k1', value: 'v1'}],
    };

    expect(normalizeHostConfig(partialConfig)).toEqual(expectedConfig);
  });

  it('should handle DEVICE_CONFIG_MODE_UNSPECIFIED and default to PER_DEVICE', () => {
    const partialConfig: Partial<HostConfig> = {
      deviceConfigMode: 'DEVICE_CONFIG_MODE_UNSPECIFIED',
    };
    expect(normalizeHostConfig(partialConfig).deviceConfigMode).toBe(
      'PER_DEVICE',
    );

    const partialConfigNullMode: Partial<HostConfig> = {
      deviceConfigMode: undefined,
    };
    expect(normalizeHostConfig(partialConfigNullMode).deviceConfigMode).toBe(
      'PER_DEVICE',
    );
  });

  it('should return false if properties is undefined', () => {
    expect(hasEmptyProperties()).toBeFalse();
  });

  it('should return false if properties is empty', () => {
    expect(hasEmptyProperties([])).toBeFalse();
  });

  it('should return false if all properties have key and value', () => {
    const properties: HostProperty[] = [{key: 'k1', value: 'v1'}];
    expect(hasEmptyProperties(properties)).toBeFalse();
  });

  it('should return true if any property has empty key', () => {
    const properties: HostProperty[] = [
      {key: '', value: 'v1'},
      {key: 'k2', value: 'v2'},
    ];
    expect(hasEmptyProperties(properties)).toBeTrue();
  });

  it('should return true if any property has empty value', () => {
    const properties: HostProperty[] = [
      {key: 'k1', value: ''},
      {key: 'k2', value: 'v2'},
    ];
    expect(hasEmptyProperties(properties)).toBeTrue();
  });

  it('should return empty array if properties is undefined', () => {
    expect(clearEmptyProperties()).toEqual([]);
  });

  it('should filter out empty properties', () => {
    const properties: HostProperty[] = [
      {key: 'k1', value: 'v1'},
      {key: '', value: 'v2'},
      {key: 'k3', value: ''},
      {key: 'k4', value: 'v4'},
    ];

    const expected: HostProperty[] = [
      {key: 'k1', value: 'v1'},
      {key: 'k4', value: 'v4'},
    ];

    expect(clearEmptyProperties(properties)).toEqual(expected);
  });

  it('should filter out properties with empty key but non-empty value', () => {
    const properties: HostProperty[] = [{key: '', value: 'val'}];
    expect(clearEmptyProperties(properties)).toEqual([]);
  });

  it('should filter out properties with non-empty key but empty value', () => {
    const properties: HostProperty[] = [{key: 'key', value: ''}];
    expect(clearEmptyProperties(properties)).toEqual([]);
  });
});
