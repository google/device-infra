/** @fileoverview Mock host scenario with no configuration. */

import {MockHostScenario} from '../models';
import {
  createDefaultHostOverview,
  createDefaultUiStatus,
} from './ui_status_utils';

export const SCENARIO_HOST_NO_CONFIG: MockHostScenario = {
  hostName: 'host-no-config.example.com',
  scenarioName: '1. No Config',
  overview: createDefaultHostOverview('host-no-config.example.com'),
  deviceSummaries: [],
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
};
