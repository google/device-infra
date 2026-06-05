import {
  DEFAULT_DEVICE_CONFIG,
  DEFAULT_DEVICE_CONFIG_UI_STATUS,
} from '../constants/device_config_constants';
import {
  DeviceConfig,
  DeviceConfigUiStatus,
  DeviceDimension,
  StabilitySettings,
  WifiConfig,
} from '../models/device_config_models';

/**
 * Normalizes a DeviceConfig object by ensuring that its critical parts
 * reflect at least their default structures.
 */
export function normalizeDeviceConfig(
  config: Partial<DeviceConfig> | null,
): DeviceConfig {
  if (!config) {
    return DEFAULT_DEVICE_CONFIG;
  }
  return {
    ...DEFAULT_DEVICE_CONFIG,
    ...config,
    permissions: {
      ...DEFAULT_DEVICE_CONFIG.permissions,
      ...(config.permissions || {}),
    },
    wifi: {
      ...DEFAULT_DEVICE_CONFIG.wifi,
      ...(config.wifi || {}),
    } as WifiConfig,
    dimensions: {
      ...DEFAULT_DEVICE_CONFIG.dimensions,
      ...(config.dimensions || {}),
    },
    settings: {
      ...DEFAULT_DEVICE_CONFIG.settings,
      ...(config.settings || {}),
    } as StabilitySettings,
  };
}

/**
 * Normalizes a DeviceConfigUiStatus object.
 */
export function normalizeDeviceConfigUiStatus(
  status?: Partial<DeviceConfigUiStatus>,
): DeviceConfigUiStatus {
  if (!status) {
    return DEFAULT_DEVICE_CONFIG_UI_STATUS;
  }
  return {
    permissions:
      status.permissions || DEFAULT_DEVICE_CONFIG_UI_STATUS.permissions,
    wifi: status.wifi || DEFAULT_DEVICE_CONFIG_UI_STATUS.wifi,
    dimensions: status.dimensions || DEFAULT_DEVICE_CONFIG_UI_STATUS.dimensions,
    settings: status.settings || DEFAULT_DEVICE_CONFIG_UI_STATUS.settings,
  };
}

/**
 * Checks if there are any empty dimensions (both name and value are empty).
 */
export function hasEmptyDimensions(dimensions?: {
  supported?: DeviceDimension[];
  required?: DeviceDimension[];
}): boolean {
  const supported = dimensions?.supported || [];
  const required = dimensions?.required || [];
  return (
    supported.some((item) => !item.name || !item.value) ||
    required.some((item) => !item.name || !item.value)
  );
}

/**
 * Filters out empty dimensions (where both name and value are empty).
 */
export function clearEmptyDimensions(dimensions?: {
  supported?: DeviceDimension[];
  required?: DeviceDimension[];
}): {
  supported: DeviceDimension[];
  required: DeviceDimension[];
} {
  if (!dimensions) {
    return {supported: [], required: []};
  }
  const supported = (dimensions.supported || []).filter(
    (item) => item.name && item.value,
  );
  const required = (dimensions.required || []).filter(
    (item) => item.name && item.value,
  );
  return {supported, required};
}
