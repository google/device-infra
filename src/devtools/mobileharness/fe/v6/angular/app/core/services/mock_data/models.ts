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
import {
  HostActions,
  ListTroubleshootScriptsResponse,
  PreflightLabServerReleaseResponse,
} from '../../models/host_action';
import {GetHostConfigResult} from '../../models/host_config_models';
import {DeviceSummary, HostOverview} from '../../models/host_overview';
import {TestOverviewData} from '../../models/test_overview';

/**
 * Common base interface for all mock scenarios.
 */
export interface BaseMockScenario {
  readonly scenarioName: string;
}

/**
 * Defines the complete data set for a single mock device scenario.
 * This includes its overview data (for the device detail page) and its
 * configuration data (for the configuration modal).
 */
export interface MockDeviceScenario extends BaseMockScenario {
  readonly id: string;
  readonly overview: DeviceOverview;
  config: DeviceConfig | null;
  readonly isQuarantined: boolean;
  readonly quarantineExpiry?: string;
  readonly healthinessStats?: HealthinessStats;
  readonly testResultStats?: TestResultStats;
  readonly recoveryTaskStats?: RecoveryTaskStats;
  readonly actionVisibility?: {
    readonly screenshot?: boolean;
    readonly logcat?: boolean;
    readonly flash?: boolean;
    readonly remoteControl?: boolean;
    readonly quarantine?: boolean;
  };
  readonly allActionsNotReady?: boolean;
  readonly testbedConfig?: TestbedConfig;
}

/**
 * Defines the data set for a mock host scenario.
 * This includes the host's name and its default device configuration.
 */
export interface MockHostScenario extends BaseMockScenario {
  readonly hostName: string;
  readonly hostConfigResult: GetHostConfigResult;
  readonly defaultDeviceConfig: DeviceConfig | null;
  readonly overview?: HostOverview;
  deviceSummaries?: DeviceSummary[];
  readonly actions: HostActions;
  readonly releaseResponse?: PreflightLabServerReleaseResponse;
  readonly troubleshootScriptsResponse?: ListTroubleshootScriptsResponse;
}

/**
 * Defines the data set for a mock test scenario.
 */
export interface MockTestScenario extends BaseMockScenario {
  readonly id: string;
  readonly overview: TestOverviewData;
  readonly log?: string;
  readonly cloudLogLink?: string;
}
