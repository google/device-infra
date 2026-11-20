/**
 * @fileoverview Composable store slice synchronizing search chips and fleet scope bidirectionally with Angular Router query parameters.
 */

import {signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {NavigationEnd} from '@angular/router';
import {asapScheduler, EMPTY, of, Subject} from 'rxjs';
import {catchError, filter, map, observeOn, switchMap} from 'rxjs/operators';

import {FilterChip, SearchUrlSyncHost} from '../../models';
import {
  buildOutgoingUrlQueryParams,
  buildUrlParamKey,
  getQueryParamAsArray,
  getSerializedChipsKey,
  parseUrlChips,
  resolveFleetScope,
} from '../../utils';

/** Composable slice managing URL query-param parsing, backend chip resolution, and Router sync. */
export function useSearchUrlSync(host: SearchUrlSyncHost) {
  let lastSyncedUrlKey: string | null = null;
  let isInternalUrlSync = false;
  const isResolvingChips = signal<boolean>(false);

  const resolveFiltersSubject = new Subject<{
    parsedFilters: FilterChip[];
    gbKeys: string[];
    source?: string;
    baseKey?: string;
  } | null>();

  /** Cancels any in-flight backend chip resolution request and clears the resolving indicator. */
  function cancelPendingChipResolution(): void {
    isResolvingChips.set(false);
    resolveFiltersSubject.next(null);
  }

  /** Returns true when the store is already in its default initial state with no active UI overlays. */
  function isDefaultState(): boolean {
    if (
      host.browseAll() ||
      host.searchQuery().trim().length > 0 ||
      host.showValuePicker() ||
      host.showSuggestions()
    ) {
      return false;
    }
    const fleet = host.fleet();
    return (
      getSerializedChipsKey(host.activeChips(), fleet) ===
      getSerializedChipsKey(host.getDefaultChips(), fleet)
    );
  }

  /** Parses incoming URL query parameters (`f`, `gb`, `fleet`, `q`, `src`) and triggers backend chip resolution. */
  function syncFromQueryParams(
    params: Record<string, unknown> | null | undefined,
  ): void {
    if (!params || !host.isCurrentRouteActive()) return;
    if (host.handleLegacyQueryParams(params)) {
      lastSyncedUrlKey = null;
      return;
    }

    const fleetParam = resolveFleetScope(params, host.isFleetSwitchable);
    if (fleetParam !== host.fleet()) {
      host.fleet.set(fleetParam);
    }

    const fParams = getQueryParamAsArray(params['f']);
    const {parsedFilters, groupByKeys, initialChips} = parseUrlChips(params);
    const qParam = getQueryParamAsArray(params['q'])[0] || '';
    const srcParam = getQueryParamAsArray(params['src'])[0] || '';

    const urlKey = buildUrlParamKey(fParams, groupByKeys, fleetParam);
    const baseKey = qParam ? `${urlKey}|q:${qParam}` : urlKey;
    const incomingKey = srcParam ? `${baseKey}|src:${srcParam}` : baseKey;
    if (incomingKey === lastSyncedUrlKey) return;
    lastSyncedUrlKey = incomingKey;
    host.cancelPendingLegacyTranslation();

    if (fParams.length === 0 && groupByKeys.length === 0) {
      cancelPendingChipResolution();
      host.restoreDefaultState(false);
      if (srcParam) {
        resolveFiltersSubject.next({
          parsedFilters: [],
          gbKeys: [],
          source: srcParam,
          baseKey,
        });
      }
    } else {
      host.activeChips.set(initialChips);
      isResolvingChips.set(true);
      resolveFiltersSubject.next({
        parsedFilters,
        gbKeys: groupByKeys,
        source: srcParam || undefined,
        baseKey,
      });
    }

    if (qParam) {
      host.searchQuery.set(qParam);
      host.showSuggestions.set(true);
      host.focusInputTrigger.set(true);
    }
  }

  /** Serializes active filter/group-by chips and fleet scope into Angular Router query parameters. */
  function syncUrl(
    chips: FilterChip[] = host.activeChips(),
    fleet: 'internal' | 'ats' = host.fleet(),
    replaceUrl = false,
  ): void {
    const {syncKey, queryParams} = buildOutgoingUrlQueryParams(
      chips,
      fleet,
      host.getLegacyQueryParamKeys(),
    );
    lastSyncedUrlKey = syncKey;
    if (!replaceUrl) {
      cancelPendingChipResolution();
      host.cancelPendingLegacyTranslation();
    }
    isInternalUrlSync = true;
    host.router
      .navigate([], {
        relativeTo: host.route,
        queryParams,
        queryParamsHandling: 'merge',
        replaceUrl,
      })
      .then(() => {
        setTimeout(() => {
          isInternalUrlSync = false;
        }, 0);
      });
  }

  /** Strips only the transient `src` query parameter from the browser URL in-place. */
  function stripSourceQueryParam(baseKey?: string): void {
    if (!host.isCurrentRouteActive() || host.router.currentNavigation()) {
      return;
    }
    if (baseKey !== undefined) {
      lastSyncedUrlKey = baseKey;
    }
    isInternalUrlSync = true;
    host.router
      .navigate([], {
        relativeTo: host.route,
        queryParams: {'src': null},
        queryParamsHandling: 'merge',
        replaceUrl: true,
      })
      .then(() => {
        setTimeout(() => {
          isInternalUrlSync = false;
        }, 0);
      });
  }

  resolveFiltersSubject
    .pipe(
      switchMap((req) => {
        if (!req) return EMPTY;
        const {parsedFilters, gbKeys, source, baseKey} = req;
        const hasChipsToResolve = parsedFilters.length > 0 || gbKeys.length > 0;
        if (!hasChipsToResolve && !source) {
          return of({
            chips: [] as FilterChip[],
            updateChips: false,
            stripSource: false,
            baseKey,
          });
        }
        return host.resolveChipsFromBackend(parsedFilters, gbKeys, source).pipe(
          map((resolvedChips) => ({
            chips: resolvedChips || [],
            updateChips: hasChipsToResolve,
            stripSource: Boolean(source),
            baseKey,
          })),
          catchError(() =>
            of({
              chips: [] as FilterChip[],
              updateChips: false,
              stripSource: false,
              baseKey,
            }),
          ),
        );
      }),
      takeUntilDestroyed(host.destroyRef),
    )
    .subscribe(({chips, updateChips, stripSource, baseKey}) => {
      if (updateChips) {
        host.activeChips.set(chips);
      }
      isResolvingChips.set(false);
      if (stripSource) {
        stripSourceQueryParam(baseKey);
      }
    });

  host.route.queryParams
    .pipe(observeOn(asapScheduler), takeUntilDestroyed(host.destroyRef))
    .subscribe((params) => {
      syncFromQueryParams(params);
    });

  host.router.events
    .pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      observeOn(asapScheduler),
      takeUntilDestroyed(host.destroyRef),
    )
    .subscribe(() => {
      if (!host.isCurrentRouteActive()) {
        lastSyncedUrlKey = null;
        cancelPendingChipResolution();
        host.cancelPendingLegacyTranslation();
        host.focusInputTrigger.set(false);
        return;
      }
      if (isInternalUrlSync) return;
      const qp = host.route.snapshot?.queryParams;
      const hasLegacyParams = host
        .getLegacyQueryParamKeys()
        .some((key) => Boolean(qp?.[key]));
      if (host.supportsFleetScope && !hasLegacyParams) {
        const fleetParam = resolveFleetScope(qp, host.isFleetSwitchable);
        if (fleetParam !== host.fleet()) {
          host.fleet.set(fleetParam);
        }
      }
      if (
        qp?.['f'] ||
        qp?.['gb'] ||
        qp?.['q'] ||
        qp?.['src'] ||
        hasLegacyParams
      ) {
        syncFromQueryParams(qp);
        return;
      }
      if (!isDefaultState()) {
        host.restoreDefaultState(false);
      }
    });

  return {
    isResolvingChips: isResolvingChips.asReadonly(),
    cancelPendingChipResolution,
    syncUrl,
  };
}
