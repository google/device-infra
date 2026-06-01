import {HttpRequest} from '@angular/common/http';

import {DeviceHeaderInfo} from '../models/device_action';
import {DeviceOverviewPageData} from '../models/device_overview';
import {HostHeaderInfo} from '../models/host_action';
import {
  DeviceSummary,
  GetHostDeviceSummariesResponse,
  HostOverviewPageData,
} from '../models/host_overview';

/**
 * Union type of all bodies that can be patched by the force ready feature.
 */
export type PatchableBody =
  | HostHeaderInfo
  | HostOverviewPageData
  | DeviceHeaderInfo
  | DeviceOverviewPageData
  | GetHostDeviceSummariesResponse;

/**
 * Rule for matching a request and patching its response.
 */
export interface PatchRule {
  matcher: (req: HttpRequest<unknown>) => boolean;
  handler: (body: unknown, forcedButtons: string[]) => PatchableBody;
  forcedButtons: string[];
}

/**
 * Modifies HostHeaderInfo to force specified buttons to be ready.
 */
export function modifyHostHeaderInfo(
  body: unknown,
  forcedButtons: string[],
): HostHeaderInfo {
  const b = body as HostHeaderInfo;
  if (b.actions) {
    return {
      ...b,
      actions: updateActions(b.actions, forcedButtons),
    };
  }
  return b;
}

/**
 * Modifies HostOverviewPageData to force specified buttons to be ready.
 */
export function modifyHostOverview(
  body: unknown,
  forcedButtons: string[],
): HostOverviewPageData {
  const b = body as HostOverviewPageData;
  const newBody = {...b};
  let modified = false;

  if (newBody.headerInfo?.actions) {
    newBody.headerInfo = {
      ...newBody.headerInfo,
      actions: updateActions(newBody.headerInfo.actions, forcedButtons),
    };
    modified = true;
  }

  if (newBody.overviewContent) {
    const newContent = {...newBody.overviewContent};
    let contentModified = false;

    if (newContent.labServer?.actions) {
      const updatedActions = updateActions(
        newContent.labServer.actions,
        forcedButtons,
      );
      if (updatedActions !== newContent.labServer.actions) {
        newContent.labServer = {
          ...newContent.labServer,
          actions: updatedActions,
        };
        contentModified = true;
      }
    }

    if (forcedButtons.includes('canUpgrade') || forcedButtons.includes('*')) {
      if (!newContent.canUpgrade) {
        newContent.canUpgrade = true;
        contentModified = true;
      }
    }

    if (contentModified) {
      newBody.overviewContent = newContent;
      modified = true;
    }
  }

  return modified ? newBody : b;
}

/**
 * Modifies DeviceHeaderInfo to force specified buttons to be ready.
 */
export function modifyDeviceHeaderInfo(
  body: unknown,
  forcedButtons: string[],
): DeviceHeaderInfo {
  const b = body as DeviceHeaderInfo;
  if (b.actions) {
    return {
      ...b,
      actions: updateActions(b.actions, forcedButtons),
    };
  }
  return b;
}

/**
 * Modifies DeviceOverviewPageData to force specified buttons to be ready.
 */
export function modifyDeviceOverview(
  body: unknown,
  forcedButtons: string[],
): DeviceOverviewPageData {
  const b = body as DeviceOverviewPageData;
  if (b.headerInfo?.actions) {
    return {
      ...b,
      headerInfo: {
        ...b.headerInfo,
        actions: updateActions(b.headerInfo.actions, forcedButtons),
      },
    };
  }
  return b;
}

/**
 * Modifies GetHostDeviceSummariesResponse to force specified buttons to be ready.
 */
export function modifyHostDevices(
  body: unknown,
  forcedButtons: string[],
): GetHostDeviceSummariesResponse {
  const b = body as GetHostDeviceSummariesResponse;
  if (b.deviceSummaries && Array.isArray(b.deviceSummaries)) {
    return {
      ...b,
      deviceSummaries: b.deviceSummaries.map((summary: DeviceSummary) => {
        if (summary.actions) {
          return {
            ...summary,
            actions: updateActions(summary.actions, forcedButtons),
          };
        }
        return summary;
      }),
    };
  }
  return b;
}

/**
 * Updates the isReady field of specified buttons in the actions object.
 */
export function updateActions<T>(actions: T, forcedButtons: string[]): T {
  const updatedActions = {...actions};
  let modified = false;
  const actionMap = updatedActions as unknown as Record<
    string,
    {isReady?: boolean; state?: {isReady?: boolean}}
  >;

  if (forcedButtons.includes('*')) {
    for (const key of Object.keys(actionMap)) {
      const action = actionMap[key];
      if (!action) continue;

      if (key === 'flash') {
        if (action.state && !action.state.isReady) {
          actionMap[key] = {
            ...action,
            state: {...action.state, isReady: true},
          };
          modified = true;
        }
      } else {
        if (!action.isReady) {
          actionMap[key] = {...action, isReady: true};
          modified = true;
        }
      }
    }
  } else {
    for (const btn of forcedButtons) {
      const action = actionMap[btn];
      if (!action) continue;

      if (btn === 'flash') {
        actionMap[btn] = {
          ...action,
          state: {...action.state, isReady: true},
        };
        modified = true;
      } else {
        actionMap[btn] = {...action, isReady: true};
        modified = true;
      }
    }
  }
  return modified ? (actionMap as unknown as T) : actions;
}
