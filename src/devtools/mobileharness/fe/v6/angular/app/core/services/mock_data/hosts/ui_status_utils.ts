/**
 * @fileoverview Utility functions for creating UI status objects for mock data.
 */

import {
  Editability,
  HostConfigUiStatus,
  HostPropertiesUiStatus,
  PartStatus,
} from '../../../models/host_config_models';

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
    deviceConfig: {...VISIBLE_EDITABLE},
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
