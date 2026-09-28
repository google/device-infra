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
  PageNavigationStrategy,
  PageSpecificParamDefinition,
} from './page_navigation_strategy';

/** Strategy for Search & Home pages with simple route paths and whitelisted parameters. */
export class SimpleNavigationStrategy extends PageNavigationStrategy {
  readonly pageSpecificParams: Record<string, PageSpecificParamDefinition> = {};

  constructor(
    readonly pageType: string,
    private readonly routePath: string,
    allowedParams: readonly string[],
  ) {
    super();
    for (const param of allowedParams) {
      this.pageSpecificParams[param] = true;
    }
  }

  buildRoutePath(): string {
    return this.routePath;
  }
}
