/** @fileoverview Mock host scenario with no configuration. */

import {MockHostScenario} from '../models';
import {
  createDefaultHostOverview,
  createDefaultUiStatus,
  createHostActions,
} from './ui_status_utils';

const SCENARIO_HOST_NO_CONFIG_DATA: MockHostScenario = {
  hostName: 'host-no-config.example.com',
  scenarioName: '1. No Config',
  overview: createDefaultHostOverview('host-no-config.example.com'),
  deviceSummaries: [],
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
  actions: createHostActions(),
  releaseResponse: {
    permissionDenied: {},
  },
};

/**
 * Returns the mock scenario for host no config.
 */
export function SCENARIO_HOST_NO_CONFIG(callCount?: number): MockHostScenario {
  return SCENARIO_HOST_NO_CONFIG_DATA;
}
