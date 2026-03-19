import {HostConfig, SelectOption} from '../models/host_config_models';
import {DEFAULT_DEVICE_CONFIG} from './device_config_constants';

/**
 * Maneki device type options.
 */
export const MANEKI_DEVICE_TYPE_OPTIONS: SelectOption[] = [
  {label: 'Android', value: 'android'},
  {label: 'Roku', value: 'roku'},
  {label: 'Rdk', value: 'rdk'},
  {label: 'Raspberry Pi', value: 'raspberry_pi'},
  {label: 'Ps4', value: 'ps4'},
  {label: 'Ps5', value: 'ps5'},
  {label: 'Generic', value: 'generic'},
];

/**
 * Default values for the Host Configuration.
 */
export const DEFAULT_HOST_CONFIG: HostConfig = {
  permissions: {
    hostAdmins: [],
  },
  deviceConfigMode: 'PER_DEVICE',
  deviceConfig: DEFAULT_DEVICE_CONFIG,
  hostProperties: [],
  deviceDiscovery: {
    monitoredDeviceUuids: [],
    testbedUuids: [],
    miscDeviceUuids: [],
    overTcpIps: [],
    overSshDevices: [],
    manekiSpecs: [],
  },
};
