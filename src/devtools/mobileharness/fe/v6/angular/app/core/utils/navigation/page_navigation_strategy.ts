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

/** All known navigation page types in MHFE V6. */
export type NavPageType =
  | 'device'
  | 'host'
  | 'job'
  | 'test'
  | 'session'
  | 'device_search'
  | 'host_search'
  | 'session_search'
  | 'job_search'
  | 'test_search'
  | 'home';

/**
 * Configuration for NavLink component and navigation strategies.
 */
export type NavLinkConfig =
  | {
      type: 'host';
      hostName: string;
      hostIp?: string;
      universe?: string;
    }
  | {
      type: 'device';
      deviceId: string;
      hostName?: string;
      hostIp?: string;
      universe?: string;
    }
  | {type: 'job'; jobId: string}
  | {type: 'test'; jobId: string; testId: string}
  | {type: 'session'; sessionId: string};

/** External parent window navigation payload. */
export interface ExternalNavPayload {
  page:
  // TJS does NOT need to post message to external(parent) frame.
  // | 'job_details'
  // | 'test_details'
  // | 'session_details'
  'host_details' | 'device_details';
  params: Record<string, string>;
}

/**
 * Extractor function for dynamic parameters.
 * @param inputParameters The merged input dictionary (current URL params + config).
 * @param isForParentFrame True when generating payload for parent window (UrlService).
 * @return The parameter value string, or undefined if the parameter should be omitted.
 */
export type ParamExtractorFn = (
  inputParameters: Record<string, unknown>,
  isForParentFrame: boolean,
) => string | undefined;

/**
 * Definition for a page-specific parameter:
 * - `true`: extracts inputParameters[key] directly if present.
 * - `string`: extracts from an alternate property name in inputParameters (e.g. 'hostName' -> 'host_name').
 * - `ParamExtractorFn`: custom function providing dynamic extraction and context-awareness.
 */
export type PageSpecificParamDefinition = boolean | string | ParamExtractorFn;

/**
 * Abstract base class providing common parameter extraction, routing,
 * and external payload formatting for a specific page type.
 */
export abstract class PageNavigationStrategy<T = unknown> {
  abstract readonly pageType: string;
  abstract readonly pageSpecificParams: Record<
    string,
    PageSpecificParamDefinition
  >;

  /**
   * Name of the page for external parent window integration (e.g. 'device_details', 'host_details').
   * Undefined if the page does not support notifying the parent frame.
   */
  readonly externalPageName?: ExternalNavPayload['page'];

  /** Whether this page supports notifying parent window (UrlService). */
  get supportsExternalNavigation(): boolean {
    return this.externalPageName !== undefined;
  }

  /** Builds the internal Angular router path (e.g. `/devices/123`). */
  abstract buildRoutePath(config: T): string;

  /**
   * Sanitizes parameter dictionary by removing default universe ('google_1p').
   */
  protected sanitizeParams(
    params: Record<string, string>,
  ): Record<string, string> {
    if (params['universe'] === 'google_1p') {
      delete params['universe'];
    }
    return params;
  }

  /**
   * Generic iterator extracting allowed parameters based on pageSpecificParams.
   */
  extractPageSpecificParams(
    inputParameters: Record<string, unknown>,
    isForParentFrame: boolean,
  ): Record<string, string> {
    const result: Record<string, string> = {};

    for (const [key, definition] of Object.entries(this.pageSpecificParams)) {
      let val: unknown;

      if (definition === true) {
        val = inputParameters[key];
      } else if (typeof definition === 'string') {
        val = inputParameters[definition];
      } else if (typeof definition === 'function') {
        val = definition(inputParameters, isForParentFrame);
      } else {
        throw new Error(
          `Invalid parameter extraction definition for key: ${key} on page type: ${this.pageType}`,
        );
      }

      if (val !== undefined) {
        result[key] = String(val);
      }
    }

    return result;
  }

  /**
   * Computes the complete query parameters for Client-Side Navigation (CSN).
   * Automatically drops 'universe' if its value is 'google_1p' (default universe).
   */
  toCsnQueryParams(
    inputParameters: Record<string, unknown>,
    commonParams: Readonly<Record<string, string>> = {},
    customQueryParams: Record<string, string> = {},
  ): Record<string, string> {
    return this.sanitizeParams({
      ...commonParams,
      ...this.extractPageSpecificParams(inputParameters, false),
      ...customQueryParams,
    });
  }

  /**
   * Computes the external payload for parent window integration.
   * Throws if the page type does not support external navigation.
   */
  toExternalPayload(
    config: Partial<T> | Record<string, unknown>,
    commonParams: Readonly<Record<string, string>> = {},
    customQueryParams: Record<string, string> = {},
  ): ExternalNavPayload {
    if (!this.externalPageName) {
      throw new Error(
        `Does NOT support to notify parent for page type: ${this.pageType}`,
      );
    }
    return {
      page: this.externalPageName,
      params: this.sanitizeParams({
        ...commonParams,
        ...this.extractPageSpecificParams(
          config as Record<string, unknown>,
          true,
        ),
        ...customQueryParams,
      }),
    };
  }
}
