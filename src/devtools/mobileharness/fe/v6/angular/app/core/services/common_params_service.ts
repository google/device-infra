import {DOCUMENT} from '@angular/common';
import {Injectable, inject, signal} from '@angular/core';

/**
 * Common query parameters supported across ALL pages in MHFE V6.
 * These parameters persist across the entire SPA lifecycle until full page reload.
 */
export const COMMON_QUERY_PARAMS = [
  'debug',
  'fake_data',
  'is_embedded_mode',
  'force_host_ready',
  'force_device_ready',
  'force_all_ready',
] as const;

/** Key type for supported common query parameters. */
export type CommonQueryParamKey = (typeof COMMON_QUERY_PARAMS)[number];

/**
 * Service to capture and manage Common Query Parameters across MHFE V6.
 *
 * Captures common parameters (debug, is_embedded_mode, force_*_ready) once
 * from the initial URL at SPA bootstrap and preserves them in memory across
 * the entire SPA lifecycle until full page reload.
 */
@Injectable({providedIn: 'root'})
export class CommonParamsService {
  private readonly document = inject(DOCUMENT, {optional: true});

  /** Reactive signal holding immutable active common query parameters. */
  readonly commonParams = signal<Readonly<Record<string, string>>>(
    this.initCommonParams(),
  );

  private initCommonParams(): Readonly<Record<string, string>> {
    const win =
      this.document?.defaultView ??
      (typeof window !== 'undefined' ? window : null);
    const search = win?.location?.search ?? '';
    const params = new URLSearchParams(search);

    const result: Record<string, string> = {};
    for (const key of COMMON_QUERY_PARAMS) {
      const val = params.get(key);
      if (val !== null && val !== '') {
        result[key] = val;
      }
    }
    return Object.freeze(result);
  }

  /** Returns an immutable reference of all active common query parameters. */
  getCommonParams(): Readonly<Record<string, string>> {
    return this.commonParams();
  }

  /** Retrieves the value of a specific common parameter, or null if not set. */
  getParam(key: CommonQueryParamKey): string | null {
    return this.commonParams()[key] ?? null;
  }

  /** Checks whether a specific common parameter is active. */
  hasParam(key: CommonQueryParamKey): boolean {
    return key in this.commonParams();
  }

  /** Returns whether the application is running in embedded mode. */
  isEmbeddedMode(): boolean {
    return this.getParam('is_embedded_mode') === 'true';
  }
}
