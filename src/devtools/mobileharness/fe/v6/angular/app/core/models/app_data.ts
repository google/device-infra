/**
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {InjectionToken} from '@angular/core';

/** Injects the application data loaded from index.html. */
export const APP_DATA = new InjectionToken<AppData>('APP_DATA');

/** General application data. */
export declare interface AppData {
  /** Current ADB version. */
  readonly adbVersion?: string;
  /** Current MTT version. */
  readonly mttVersion?: string;

  /** True if running in development mode. */
  readonly isDevMode?: boolean;
  /** Current labconsole version. */
  readonly labconsoleVersion?: string;
  /**
   * The url of lab console server. Do NOT pass value to this field, as it's a
   * computed filed via labConsoleServerPort and overrideLabConsoleServerUrl.
   * The compute logic is:
   * # when overrideLabConsoleServerUrl is set, use it.
   * # otherwise, use
   * ${window.location.protocol}//${window.location.hostname}
   *  :${labConsoleServerPort}
   */
  readonly labConsoleServerUrl?: string;
  /** The port of lab console server. */
  readonly labConsoleServerPort?: string;
  /**
   * The override lab console server url. If specified, will be used as the
   * lab console server url, which means the labConsoleServerPort will be
   * ignored.
   */
  readonly overrideLabConsoleServerUrl?: string;
  /** The UI platform of the current instance. */
  readonly uiPlatform?: string;
  /** env type. */
  readonly applicationId?: string;
  /** Current user's email. */
  readonly email?: string;
  /** Current user's display name. */
  readonly userDisplayName?: string;
  /** The mode in which the application was started. */
  readonly startMode?: string;
}


const OSS_DEFAULTS = {
  // for OSS, we use the localhost:8080 as the default lab console server url.
  overrideLabConsoleServerUrl: 'http://localhost:8080',
  uiPlatform: 'OSS',
  applicationId: 'lab-console-oss',
  email: '',
  userDisplayName: '',
  startMode: 'ng-serve',
};

/**
 * Gets the app data from the <script id="app-data"> element in the host HTML
 * page.
 */
export function getAppData(): AppData {
  const appDataElement = document.getElementById('app-data');
  const rawData = JSON.parse(appDataElement?.textContent || '{}') as AppData;
  const defaults = OSS_DEFAULTS;

  // normalize the data.
  const labConsoleServerPort = rawData.labConsoleServerPort ?? '9007';
  return {
    adbVersion: rawData.adbVersion ?? '10.0',
    mttVersion: rawData.mttVersion ?? '20.0',
    isDevMode: rawData.isDevMode ?? false,
    labconsoleVersion: rawData.labconsoleVersion ?? '1.0',
    labConsoleServerPort,
    // we should not set the default value for overrideLabConsoleServerUrl,
    // because, as its name suggested, if the overrideLabConsoleServerUrl is not
    // set, we should use the default lab console server url.
    overrideLabConsoleServerUrl: rawData.overrideLabConsoleServerUrl ??
        defaults.overrideLabConsoleServerUrl,
    // compute the labConsoleServerUrl via labConsoleServerPort and
    // overrideLabConsoleServerUrl.
    labConsoleServerUrl: rawData.overrideLabConsoleServerUrl ??
        `${window.location.protocol}//${window.location.hostname}:${
                             labConsoleServerPort}`,
    uiPlatform: rawData.uiPlatform ?? defaults.uiPlatform,
    applicationId: rawData.applicationId ?? defaults.applicationId,
    email: rawData.email ?? defaults.email,
    userDisplayName: rawData.userDisplayName ?? defaults.userDisplayName,
    startMode: rawData.startMode ?? defaults.startMode,
  };
}
