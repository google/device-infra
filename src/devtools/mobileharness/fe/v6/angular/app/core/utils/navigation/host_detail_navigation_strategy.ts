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

import {
  NavLinkConfig,
  PageNavigationStrategy,
  PageSpecificParamDefinition,
} from './page_navigation_strategy';

/** Strategy for Host Detail page (`/hosts/:hostName`). */
export class HostDetailNavigationStrategy extends PageNavigationStrategy<
  Extract<NavLinkConfig, {type: 'host'}>
> {
  readonly pageType = 'host' as const;
  override readonly externalPageName = 'host_details' as const;

  readonly pageSpecificParams: Record<string, PageSpecificParamDefinition> = {
    'host_ip': (input, isForParentFrame) =>
      isForParentFrame
        ? ((input['hostIp'] ?? input['host_ip']) as string | undefined)
        : undefined,
    'universe': true,
    'host_name': (input, isForParentFrame) =>
      isForParentFrame
        ? ((input['hostName'] ?? input['host_name']) as string | undefined)
        : undefined,
  };

  buildRoutePath(config: {hostName: string}): string {
    return `/hosts/${config.hostName}`;
  }
}
