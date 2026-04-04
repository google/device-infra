/**
 * @fileoverview Utility functions for creating UI status objects for mock data.
 */

import {HostActions} from '../../../models/host_action';
import {
  Editability,
  HostConfigUiStatus,
  PartStatus,
} from '../../../models/host_config_models';
import {HostOverview} from '../../../models/host_overview';

const EDITABLE: Editability = {editable: true};
const VISIBLE_EDITABLE: PartStatus = {visible: true, editability: EDITABLE};

/**
 * Creates a default HostConfigUiStatus where all parts are visible and editable.
 */
export function createDefaultUiStatus(): HostConfigUiStatus {
  return {
    hostAdmins: {...VISIBLE_EDITABLE},
    deviceConfigMode: {...VISIBLE_EDITABLE},
    deviceConfig: {
      sectionStatus: {...VISIBLE_EDITABLE},
      subSections: {},
    },
    hostProperties: {
      sectionStatus: {...VISIBLE_EDITABLE},
    },
    deviceDiscovery: {...VISIBLE_EDITABLE},
  };
}

/**
 * Creates a PartStatus object.
 */
export function createPartStatus(
  visible: boolean,
  editable = false,
  reason?: string,
): PartStatus {
  if (!visible) {
    return {visible: false};
  }
  return {
    visible: true,
    editability: {editable, ...(reason && {reason})},
  };
}

/**
 * Creates a default HostOverview with basic running status.
 */
export function createDefaultHostOverview(hostName: string): HostOverview {
  return {
    hostName,
    ip: '192.168.1.1',
    os: 'gLinux',
    canUpgrade: false,
    labTypeDisplayNames: ['Satellite Lab'],
    labServer: {
      connectivity: {
        state: 'RUNNING',
        title: 'Running',
        tooltip: 'Host is running and connected.',
      },
      activity: {
        state: 'STARTED',
        title: 'Started',
        tooltip: 'Lab Server is started.',
      },
      version: 'R123.45.6',
      passThroughFlags: '',
    },
    daemonServer: {
      status: {
        state: 'RUNNING',
        title: 'Running',
        tooltip: 'Daemon Server is running.',
      },
      version: '24.08.01',
    },
    properties: {},
  };
}

/**
 * Creates HostActions based on the host state.
 * @param status The status of the host (RUNNING, STOPPED, MISSING, ERROR, etc.)
 * @param isCoreLab Whether the host belongs to a Shared/Core Lab.
 */
export function createHostActions(
  status = 'RUNNING',
  isCoreLab = false,
): HostActions {
  const isRunning = status === 'RUNNING';
  const isStopped = status === 'STOPPED';
  const isMissing = status === 'MISSING';
  const isError = status === 'ERROR';

  const manageHostEnabled = !isCoreLab;
  const startEnabled = !isCoreLab && (isStopped || isMissing);
  const restartEnabled = !isCoreLab && (isRunning || isError);
  const stopEnabled = !isCoreLab && (isRunning || isError);
  const removeEnabled = isMissing;

  return {
    configuration: {
      enabled: manageHostEnabled,
      visible: true,
      tooltip: manageHostEnabled
        ? 'Configure host properties'
        : 'Configuration is not available for Shared Labs',
    },
    debug: {
      enabled: true,
      visible: true,
      tooltip: 'Run and view live diagnostic commands on the host',
    },
    release: {
      enabled: manageHostEnabled,
      visible: true,
      tooltip: manageHostEnabled
        ? 'Deploy a release or edit Pass Through Flags'
        : 'Release management is not available for Shared Labs',
    },
    restart: {
      enabled: restartEnabled,
      visible: restartEnabled,
      tooltip: restartEnabled
        ? 'Restart the lab server by redeploying its current software version and Pass Through Flags.'
        : '',
    },
    stop: {
      enabled: stopEnabled,
      visible: stopEnabled,
      tooltip: stopEnabled
        ? 'Drains running tests, then stops the lab server via Legislator rollout.'
        : '',
    },
    start: {
      enabled: startEnabled,
      visible: startEnabled,
      tooltip: startEnabled
        ? 'Start the lab server by deploying its configured software version and Pass Through Flags.'
        : '',
    },
    decommission: {
      enabled: removeEnabled,
      visible: removeEnabled,
      tooltip: removeEnabled
        ? 'Decommission this missing host record from OmniLab'
        : '',
    },
    // Not directly on the action bar but included in the model
    deploy: {
      enabled: manageHostEnabled,
      visible: true,
      tooltip: manageHostEnabled
        ? 'Deploy a release'
        : 'Release management is not available for Shared Labs',
    },
    updatePassThroughFlags: {
      enabled: manageHostEnabled,
      visible: manageHostEnabled,
      tooltip: manageHostEnabled
        ? 'Edit Pass Through Flags'
        : 'Pass Through Flags management is not available for Shared Labs',
    },
  };
}
