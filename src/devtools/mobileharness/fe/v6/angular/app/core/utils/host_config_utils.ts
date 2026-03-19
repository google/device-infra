import {DEFAULT_HOST_CONFIG} from '../constants/host_config_constants';
import {HostConfig} from '../models/host_config_models';
import {normalizeDeviceConfig} from './device_config_utils';

/**
 * Normalizes a HostConfig object by ensuring that its critical parts
 * reflect at least their default structures.
 */
export function normalizeHostConfig(
  config: Partial<HostConfig> | null,
): HostConfig {
  if (!config) {
    return DEFAULT_HOST_CONFIG;
  }
  const normalized = {
    ...DEFAULT_HOST_CONFIG,
    ...config,
    permissions: {
      ...DEFAULT_HOST_CONFIG.permissions,
      ...(config.permissions || {}),
    },
    // We handle deviceConfigMode normalization: if it's UNSPECIFIED,
    // we set it to the default (PER_DEVICE).
    deviceConfigMode:
      config.deviceConfigMode === 'DEVICE_CONFIG_MODE_UNSPECIFIED' ||
      !config.deviceConfigMode
        ? DEFAULT_HOST_CONFIG.deviceConfigMode
        : config.deviceConfigMode,
    deviceConfig: normalizeDeviceConfig(config.deviceConfig || null),
    deviceDiscovery: {
      ...DEFAULT_HOST_CONFIG.deviceDiscovery,
      ...(config.deviceDiscovery || {}),
    },
    hostProperties: config.hostProperties || [],
  };

  return normalized;
}
