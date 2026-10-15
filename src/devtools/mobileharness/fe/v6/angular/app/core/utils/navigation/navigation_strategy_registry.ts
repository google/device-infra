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

import {DeviceDetailNavigationStrategy} from './device_detail_navigation_strategy';
import {HomeNavigationStrategy} from './home_navigation_strategy';
import {HostDetailNavigationStrategy} from './host_detail_navigation_strategy';
import {JobDetailNavigationStrategy} from './job_detail_navigation_strategy';
import {PageNavigationStrategy} from './page_navigation_strategy';
import {SearchNavigationStrategy} from './search_navigation_strategy';
import {SessionDetailNavigationStrategy} from './session_detail_navigation_strategy';
import {TestDetailNavigationStrategy} from './test_detail_navigation_strategy';

/**
 * Registry holding all navigation strategies.
 * Provides lookup by page type and allows extending with new page types.
 */
// tslint:disable-next-line:class-as-namespace
export class NavigationStrategyRegistry {
  private static readonly strategies = new Map<
    string,
    PageNavigationStrategy<unknown>
  >();

  static register(strategy: PageNavigationStrategy<unknown>): void {
    NavigationStrategyRegistry.strategies.set(strategy.pageType, strategy);
  }

  static get<T = unknown>(
    pageType: string,
  ): PageNavigationStrategy<T> | undefined {
    return NavigationStrategyRegistry.strategies.get(pageType) as
      | PageNavigationStrategy<T>
      | undefined;
  }

  static getRequired<T = unknown>(pageType: string): PageNavigationStrategy<T> {
    const strategy = NavigationStrategyRegistry.get<T>(pageType);
    if (!strategy) {
      throw new Error(
        `No navigation strategy registered for page type: ${pageType}`,
      );
    }
    return strategy;
  }
}

// Register default detail strategies in the registry.
NavigationStrategyRegistry.register(new DeviceDetailNavigationStrategy());
NavigationStrategyRegistry.register(new HostDetailNavigationStrategy());
NavigationStrategyRegistry.register(new JobDetailNavigationStrategy());
NavigationStrategyRegistry.register(new TestDetailNavigationStrategy());
NavigationStrategyRegistry.register(new SessionDetailNavigationStrategy());

// Register default search and home strategies in the registry.
NavigationStrategyRegistry.register(
  new SearchNavigationStrategy('device_search', '/devices', ['f', 'gb', 'fleet']),
);
NavigationStrategyRegistry.register(
  new SearchNavigationStrategy('host_search', '/hosts', ['f', 'gb', 'fleet']),
);
NavigationStrategyRegistry.register(
  new SearchNavigationStrategy('session_search', '/sessions', []),
);
NavigationStrategyRegistry.register(
  new SearchNavigationStrategy('job_search', '/jobs', []),
);
NavigationStrategyRegistry.register(
  new SearchNavigationStrategy('test_search', '/tests', []),
);
NavigationStrategyRegistry.register(new HomeNavigationStrategy());
