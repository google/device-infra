import {DeviceProxyType} from '@deviceinfra/app/core/models/host_overview';

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

  switch (lookupKey) {
    case 'NONE':
      return DeviceProxyType.NONE;
    case 'ADB_AND_VIDEO':
      return DeviceProxyType.ADB_AND_VIDEO;
    case 'ADB_ONLY':
      return DeviceProxyType.ADB_ONLY;
    case 'USB_IP':
      return DeviceProxyType.USB_IP;
    case 'SSH':
      return DeviceProxyType.SSH;
    case 'VIDEO':
      return DeviceProxyType.VIDEO;
    default:
      return DeviceProxyType.NONE;
  }
}
