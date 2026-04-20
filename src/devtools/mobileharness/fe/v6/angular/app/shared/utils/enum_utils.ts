import {DeviceProxyType} from 'app/core/models/host_overview';

/**
 * Safely maps any value (string, number, or undefined) to a DeviceProxyType.
 * Useful for normalizing backend responses that may return strings for enums.
 */
export function toDeviceProxyType(value: unknown): DeviceProxyType {
  if (value == null) return DeviceProxyType.NONE;
  if (typeof value === 'number') return value as unknown as DeviceProxyType;
  if (typeof value !== 'string') return DeviceProxyType.NONE;

  const lookupKey = value
    .replace('DEVICE_PROXY_TYPE_', '')
    .replace(/^UNSPECIFIED$/, 'NONE');

  const enumVal = (DeviceProxyType as Record<string, unknown>)[lookupKey];
  return typeof enumVal === 'number'
    ? (enumVal as unknown as DeviceProxyType)
    : DeviceProxyType.NONE;
}
