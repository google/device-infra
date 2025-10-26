/** @fileoverview Mock host scenario with no configuration. */

import {MockHostScenario} from '../models';
import {createDefaultUiStatus} from './ui_status_utils';

export const SCENARIO_HOST_NO_CONFIG: MockHostScenario = {
  hostName: 'host-no-config.example.com',
  scenarioName: '1. No Config',
  hostConfigResult: {
    hostConfig: undefined,
    uiStatus: createDefaultUiStatus(),
  },
  defaultDeviceConfig: null,
};
