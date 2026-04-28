/**
 * @fileoverview Utility functions for creating UI status objects for mock data.
 */

import {HostActions, LabServerActions} from '../../../models/host_action';
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
      actions: createLabServerActions('RUNNING'),
    },
    showPassThroughFlags: true,
    daemonServer: {
      status: {
        state: 'RUNNING',
        title: 'Running',
        tooltip: 'Daemon Server is running.',
      },
      version: '24.08.01',
    },
    properties: {},
    diagnosticLinks: [
      {
        label: 'Tradefed Log',
        url: 'http://example.com/tradefed_log',
        category: 'LAB_SERVER',
      },
      {
        label: 'Test Log',
        url: 'http://example.com/test_log',
        category: 'DAEMON_SERVER',
      },
      {
        label: 'Host Statusz',
        url: 'http://example.com/host_statusz',
        category: 'OVERVIEW',
      },
    ],
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
  const isMissing = status === 'MISSING';
  const manageHostEnabled = !isCoreLab;
  const removeEnabled = isMissing;

  return {
    configuration: {
      enabled: manageHostEnabled,
      visible: true,
      tooltip: manageHostEnabled
        ? 'Configure host properties'
        : 'Configuration is not available for Shared Labs',
      isReady: true,
    },
    debug: {
      enabled: true,
      visible: true,
      tooltip: 'Run and view live diagnostic commands on the host',
      isReady: true,
    },
    decommission: {
      enabled: removeEnabled,
      visible: removeEnabled,
      tooltip: removeEnabled
        ? 'Decommission this missing host record from OmniLab'
        : '',
      isReady: true,
    },
  };
}

/**
 * Creates LabServerActions based on the host state.
 * @param status The status of the lab server (RUNNING, STOPPED, MISSING, ERROR, etc.)
 * @param isCoreLab Whether the host belongs to a Shared/Core Lab.
 */
export function createLabServerActions(
  status = 'RUNNING',
  isCoreLab = false,
): LabServerActions {
  const isMissing = status === 'MISSING';
  const isRunning = status === 'RUNNING';
  const actionEnabled = !isCoreLab;

  return {
    release: {
      enabled: !isMissing && actionEnabled,
      visible: true,
      tooltip: isMissing
        ? 'Cannot release a missing host'
        : isCoreLab
          ? 'Cannot release in a Shared Lab'
          : '',
      isReady: false,
    },
    start: {
      enabled: !isRunning && !isMissing && actionEnabled,
      visible: !isRunning && !isMissing,
      tooltip: isRunning
        ? 'Lab server is already running'
        : isCoreLab
          ? 'Cannot start in a Shared Lab'
          : '',
      isReady: true,
    },
    restart: {
      enabled: isRunning && actionEnabled,
      visible: true,
      tooltip: !isRunning
        ? 'Lab server must be running to restart'
        : isCoreLab
          ? 'Cannot restart in a Shared Lab'
          : '',
      isReady: true,
    },
    stop: {
      enabled: isRunning && actionEnabled,
      visible: true,
      tooltip: !isRunning
        ? 'Lab server is not running'
        : isCoreLab
          ? 'Cannot stop in a Shared Lab'
          : '',
      isReady: true,
    },
  };
}
