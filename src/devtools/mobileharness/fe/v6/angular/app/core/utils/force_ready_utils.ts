import {HttpRequest} from '@angular/common/http';

import {ActionButtonState} from '../models/action_common';
import {DeviceHeaderInfo} from '../models/device_action';
import {DeviceOverviewPageData} from '../models/device_overview';
import {HostHeaderInfo} from '../models/host_action';
import {DeviceSummary, GetHostDeviceSummariesResponse, HostOverviewPageData} from '../models/host_overview';

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
export function modifyHostHeaderInfo(body: unknown, forcedButtons: string[]): HostHeaderInfo {
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
export function modifyHostOverview(body: unknown, forcedButtons: string[]): HostOverviewPageData {
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

  if (newBody.overviewContent?.labServer?.actions) {
    newBody.overviewContent = {
      ...newBody.overviewContent,
      labServer: {
        ...newBody.overviewContent.labServer,
        actions: updateActions(
          newBody.overviewContent.labServer.actions,
          forcedButtons,
        ),
      },
    };
    modified = true;
  }

  return modified ? newBody : b;
}

/**
 * Modifies DeviceHeaderInfo to force specified buttons to be ready.
 */
export function modifyDeviceHeaderInfo(body: unknown, forcedButtons: string[]): DeviceHeaderInfo {
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
export function modifyDeviceOverview(body: unknown, forcedButtons: string[]): DeviceOverviewPageData {
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
export function modifyHostDevices(body: unknown, forcedButtons: string[]): GetHostDeviceSummariesResponse {
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
  const actionMap = updatedActions as unknown as Record<string, ActionButtonState>;
  for (const btn of forcedButtons) {
    if (actionMap[btn]) {
      actionMap[btn] = {...actionMap[btn], isReady: true};
      modified = true;
    }
  }
  return modified ? (actionMap as unknown as T) : actions;
}


