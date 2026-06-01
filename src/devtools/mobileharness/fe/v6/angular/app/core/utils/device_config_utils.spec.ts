import {
  DEFAULT_DEVICE_CONFIG,
  DEFAULT_DEVICE_CONFIG_UI_STATUS,
} from '../constants/device_config_constants';
import {
  DeviceConfig,
  DeviceConfigUiStatus,
  StabilitySettings,
  WifiConfig,
} from '../models/device_config_models';
import {
  clearEmptyDimensions,
  hasEmptyDimensions,
  normalizeDeviceConfig,
  normalizeDeviceConfigUiStatus,
} from './device_config_utils';

describe('deviceConfigUtils', () => {
  describe('normalizeDeviceConfig', () => {
    it('should return DEFAULT_DEVICE_CONFIG if config is null', () => {
      expect(normalizeDeviceConfig(null)).toEqual(DEFAULT_DEVICE_CONFIG);
    });

    it('should merge partial config with default config', () => {
      const partialConfig: Partial<DeviceConfig> = {
        permissions: {
          owners: ['user1'],
        },
        wifi: {
          type: 'custom',
          ssid: 'MySSID',
          psk: 'password',
          scanSsid: true,
        },
        dimensions: {
          supported: [{name: 'dim1', value: 'val1'}],
        },
        settings: {
          maxConsecutiveFail: 3,
          maxConsecutiveTest: 5000,
        },
      };

      const expectedConfig: DeviceConfig = {
        permissions: {
          owners: ['user1'],
          executors: [],
        },
        wifi: {
          type: 'custom',
          ssid: 'MySSID',
          psk: 'password',
          scanSsid: true,
        },
        dimensions: {
          supported: [{name: 'dim1', value: 'val1'}],
          required: [],
        },
        settings: {
          maxConsecutiveFail: 3,
          maxConsecutiveTest: 5000,
        },
      };

      expect(normalizeDeviceConfig(partialConfig)).toEqual(expectedConfig);
    });

    it('should handle partially empty nested objects', () => {
      const partialConfig: Partial<DeviceConfig> = {
        permissions: {},
        wifi: {} as unknown as WifiConfig,
        dimensions: {},
        settings: {} as unknown as StabilitySettings,
      };

      const expectedConfig: DeviceConfig = {
        permissions: {
          owners: [],
          executors: [],
        },
        wifi: {
          type: 'none',
          ssid: 'GoogleGuest',
          psk: '',
          scanSsid: false,
        },
        dimensions: {
          supported: [],
          required: [],
        },
        settings: {
          maxConsecutiveFail: 5,
          maxConsecutiveTest: 10000,
        },
      };

      expect(normalizeDeviceConfig(partialConfig)).toEqual(expectedConfig);
    });
  });

  describe('normalizeDeviceConfigUiStatus', () => {
    it('should return DEFAULT_DEVICE_CONFIG_UI_STATUS if status is undefined', () => {
      expect(normalizeDeviceConfigUiStatus()).toEqual(
        DEFAULT_DEVICE_CONFIG_UI_STATUS,
      );
    });

    it('should merge partial status with default status', () => {
      const partialStatus: Partial<DeviceConfigUiStatus> = {
        permissions: {visible: false},
        wifi: {editability: {editable: true}},
      };

      const expectedStatus: DeviceConfigUiStatus = {
        permissions: {visible: false},
        wifi: {editability: {editable: true}},
        dimensions: DEFAULT_DEVICE_CONFIG_UI_STATUS.dimensions,
        settings: DEFAULT_DEVICE_CONFIG_UI_STATUS.settings,
      };

      expect(normalizeDeviceConfigUiStatus(partialStatus)).toEqual(
        expectedStatus,
      );
    });
  });

  describe('hasEmptyDimensions', () => {
    it('should return false if dimensions is undefined', () => {
      expect(hasEmptyDimensions()).toBeFalse();
    });

    it('should return false if both supported and required dimensions are empty', () => {
      expect(hasEmptyDimensions({supported: [], required: []})).toBeFalse();
    });

    it('should return false if all dimensions have name and value', () => {
      const dimensions = {
        supported: [{name: 'n1', value: 'v1'}],
        required: [{name: 'n2', value: 'v2'}],
      };
      expect(hasEmptyDimensions(dimensions)).toBeFalse();
    });

    it('should return true if any supported dimension has empty name', () => {
      const dimensions = {
        supported: [{name: '', value: 'v1'}],
      };
      expect(hasEmptyDimensions(dimensions)).toBeTrue();
    });

    it('should return true if any supported dimension has empty value', () => {
      const dimensions = {
        supported: [{name: 'n1', value: ''}],
      };
      expect(hasEmptyDimensions(dimensions)).toBeTrue();
    });

    it('should return true if any required dimension has empty name', () => {
      const dimensions = {
        required: [{name: '', value: 'v2'}],
      };
      expect(hasEmptyDimensions(dimensions)).toBeTrue();
    });

    it('should return true if any required dimension has empty value', () => {
      const dimensions = {
        required: [{name: 'n2', value: ''}],
      };
      expect(hasEmptyDimensions(dimensions)).toBeTrue();
    });
  });

  describe('clearEmptyDimensions', () => {
    it('should return empty arrays if dimensions is undefined', () => {
      expect(clearEmptyDimensions()).toEqual({supported: [], required: []});
    });

    it('should filter out empty dimensions', () => {
      const dimensions = {
        supported: [
          {name: 'n1', value: 'v1'},
          {name: '', value: 'v2'},
          {name: 'n3', value: ''},
        ],
        required: [
          {name: 'r1', value: 'rv1'},
          {name: '', value: ''},
        ],
      };

      const expected = {
        supported: [{name: 'n1', value: 'v1'}],
        required: [{name: 'r1', value: 'rv1'}],
      };

      expect(clearEmptyDimensions(dimensions)).toEqual(expected);
    });
  });
});
