/**
 * @fileoverview Defines the interfaces for mock scenarios.
 */

import {DeviceConfig} from '../../models/device_config_models';
import {DeviceOverview} from '../../models/device_overview';
import {GetHostConfigResult} from '../../models/host_config_models';

/**
 * Defines the complete data set for a single mock device scenario.
 * This includes its overview data (for the device detail page) and its
 * configuration data (for the configuration modal).
 */
export interface MockDeviceScenario {
  id: string;
  scenarioName: string;
  overview: DeviceOverview;
  config: DeviceConfig | null;
}

/**
 * Defines the data set for a mock host scenario.
 * This includes the host's name and its default device configuration.
 */
export interface MockHostScenario {
  hostName: string;
  scenarioName: string;
  hostConfigResult: GetHostConfigResult;
  defaultDeviceConfig: DeviceConfig | null;
}
