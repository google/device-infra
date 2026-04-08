/** @fileoverview Mock host scenario where all actions are not ready. */

import {MockHostScenario} from '../models';
import {SCENARIO_HOST_BASIC_EDITABLE} from './02_basic_editable';
import {createDefaultHostOverview} from './ui_status_utils';

/**
 * Represents a mock host scenario where all action bar items are not ready.
 * This scenario is used for testing and development purposes to simulate a
 * host in a specific state.
 */
export const SCENARIO_HOST_COMING_SOON: MockHostScenario = {
  ...SCENARIO_HOST_BASIC_EDITABLE,
  hostName: 'buttons-not-implemented.host.example.com',
  scenarioName: '11. Coming Soon',
  overview: createDefaultHostOverview(
    'buttons-not-implemented.host.example.com',
  ),
  deviceSummaries: [],
  actions: {
    configuration: {enabled: true, visible: true, tooltip: '', isReady: false},
    debug: {enabled: true, visible: true, tooltip: '', isReady: false},
    deploy: {enabled: true, visible: true, tooltip: '', isReady: false},
    start: {enabled: true, visible: true, tooltip: '', isReady: false},
    restart: {enabled: true, visible: true, tooltip: '', isReady: false},
    stop: {enabled: true, visible: true, tooltip: '', isReady: false},
    decommission: {enabled: true, visible: true, tooltip: '', isReady: false},
    updatePassThroughFlags: {
      enabled: true,
      visible: true,
      tooltip: '',
      isReady: false,
    },
    release: {enabled: true, visible: true, tooltip: '', isReady: false},
  },
};
