/**
 * @fileoverview Utility functions for creating UI status objects for mock data.
 */

import {
  Editability,
  HostConfigUiStatus,
  HostPropertiesUiStatus,
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
    sshAccess: {...VISIBLE_EDITABLE},
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
