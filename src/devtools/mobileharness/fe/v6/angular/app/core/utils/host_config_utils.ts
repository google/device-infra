import {DEFAULT_HOST_CONFIG} from '../constants/host_config_constants';
import {HostConfig, HostProperty} from '../models/host_config_models';
import {normalizeDeviceConfig} from './device_config_utils';

/**
 * Normalizes a HostConfig object by ensuring that its critical parts
 * reflect at least their default structures.
 */
export function normalizeHostConfig(
  config: Partial<HostConfig> | null,
): HostConfig {
  const defaultConfig = structuredClone(DEFAULT_HOST_CONFIG);
  if (!config) {
    return defaultConfig;
  }
  const normalized = {
    ...defaultConfig,
    ...config,
    permissions: {
      ...defaultConfig.permissions,
      ...(config.permissions || {}),
    },
    // We handle deviceConfigMode normalization: if it's UNSPECIFIED,
    // we set it to the default (PER_DEVICE).
    deviceConfigMode:
      config.deviceConfigMode === 'DEVICE_CONFIG_MODE_UNSPECIFIED' ||
      !config.deviceConfigMode
        ? defaultConfig.deviceConfigMode
        : config.deviceConfigMode,
    deviceConfig: normalizeDeviceConfig(config.deviceConfig || null),
    deviceDiscovery: {
      ...defaultConfig.deviceDiscovery,
      ...(config.deviceDiscovery || {}),
    },
    hostProperties: config.hostProperties || [],
  };

  return normalized;
}

/**
 * Checks if there are any empty host properties (both key and value are empty).
 */
export function hasEmptyProperties(properties?: HostProperty[]): boolean {
  const list = properties || [];
  return list.some((item) => !item.key || !item.value);
}

/**
 * Filters out empty host properties (where both key and value are empty).
 */
export function clearEmptyProperties(
  properties?: HostProperty[],
): HostProperty[] {
  const list = properties || [];
  return list.filter((item) => item.key && item.value);
}
