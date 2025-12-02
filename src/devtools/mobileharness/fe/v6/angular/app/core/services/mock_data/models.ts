/**
 * @fileoverview Defines the interfaces for mock scenarios.
 */

import {DeviceConfig} from '../../models/device_config_models';
import {DeviceOverview, TestbedConfig} from '../../models/device_overview';
import {
  HealthinessStats,
  RecoveryTaskStats,
  TestResultStats,
} from '../../models/device_stats';
import {GetHostConfigResult} from '../../models/host_config_models';
import {DeviceSummary, HostOverview} from '../../models/host_overview';

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
  isQuarantined: boolean;
  quarantineExpiry?: string;
  healthinessStats?: HealthinessStats;
  testResultStats?: TestResultStats;
  recoveryTaskStats?: RecoveryTaskStats;
  actionVisibility?: {
    screenshot?: boolean;
    logcat?: boolean;
    flash?: boolean;
    remoteControl?: boolean;
    quarantine?: boolean;
  };
  testbedConfig?: TestbedConfig;
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
  overview?: HostOverview;
  deviceSummaries?: DeviceSummary[];
}
