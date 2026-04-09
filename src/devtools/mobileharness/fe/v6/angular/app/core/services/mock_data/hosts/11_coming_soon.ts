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
  deviceSummaries: [
    {
      id: 'buttons-not-implemented.example.com',
      healthState: {
        health: 'IN_SERVICE_IDLE',
        title: 'In Service (Idle)',
        tooltip: 'Device is healthy and ready for tasks.',
      },
      types: [
        {type: 'AndroidDevice', isAbnormal: false},
        {type: 'AndroidFlashableDevice', isAbnormal: false},
        {type: 'AndroidOnlineDevice', isAbnormal: false},
        {type: 'AndroidRealDevice', isAbnormal: false},
      ],
      deviceStatus: {isCritical: false, status: 'IDLE'},
      label: 'golden-pixel',
      requiredDims: 'pool:prod',
      model: 'Pixel 8 Pro',
      version: '14',
    },
    {
      id: '43021FDAQ000UM',
      healthState: {
        health: 'OUT_OF_SERVICE_NEEDS_FIXING',
        title: 'Out of Service (Needs Fixing)',
        tooltip: 'The device is in an error state and requires attention.',
      },
      types: [
        {type: 'AndroidDevice', isAbnormal: false},
        {type: 'AndroidFlashableDevice', isAbnormal: false},
        {type: 'AndroidOnlineDevice', isAbnormal: false},
        {type: 'AndroidRealDevice', isAbnormal: false},
      ],
      deviceStatus: {status: 'MISSING', isCritical: true},
      label: 'golden-pixel',
      requiredDims: 'pool:prod',
      model: 'Pixel 8 Pro',
      version: '14',
    },
  ],
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
