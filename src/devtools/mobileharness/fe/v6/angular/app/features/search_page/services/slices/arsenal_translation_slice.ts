/**
 * @fileoverview Composable store slice translating legacy `?arsenal_query=...` URL parameters into Fleet search state via the BFF.
 */

import {DestroyRef, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {EMPTY, Subject} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';

import {TranslateArsenalSearchResponse} from '../../../../core/models/search';
import {SEARCH_SERVICE} from '../../../../core/services/search/search_service';
import {SnackBarService} from '../../../../shared/services/snackbar_service';
import {
  ArsenalTranslatorSliceOptions,
  ParsedArsenalQueryParams,
} from '../../models';
import {
  formatFleetChipWarnings,
  parseArsenalQueryParam,
  saveStoredVisibleColumns,
  toSearchEntityProto,
  unpackArsenalTranslation,
} from '../../utils';

/** Composable encapsulating legacy `?arsenal_query=...` BFF translation and URL rewriting. */
export function useArsenalTranslator(opts: ArsenalTranslatorSliceOptions) {
  const searchService = inject(SEARCH_SERVICE);
  const snackBarService = inject(SnackBarService);
  const destroyRef = inject(DestroyRef);
  let lastArsenalKey: string | null = null;

  const translateArsenalSubject = new Subject<{
    arsenalKey: string;
    unpacked: ParsedArsenalQueryParams;
  } | null>();

  /** Applies the translated Arsenal search state, warnings, and URL rewrite to the Fleet store. */
  const applyArsenalTranslation = (
    res: TranslateArsenalSearchResponse,
    unpacked: ParsedArsenalQueryParams,
  ): void => {
    lastArsenalKey = null;
    const {targetFleet, allChips, shouldBrowseAll, warnings} =
      unpackArsenalTranslation(res);
    if (opts.fleet() !== targetFleet) {
      opts.fleet.set(targetFleet);
    }
    if (unpacked.columns.length > 0 && res.columns?.length) {
      opts.visibleColumnDescriptors.set(res.columns);
      saveStoredVisibleColumns(opts.entity(), targetFleet, res.columns);
    }
    if (warnings.length > 0) {
      snackBarService.showWarning(formatFleetChipWarnings(warnings));
    }
    opts.browseAll.set(shouldBrowseAll);
    opts.activeChips.set(allChips);
    opts.syncUrl(allChips, targetFleet, /* replaceUrl= */ true);
    if (allChips.length > 0 || shouldBrowseAll) {
      opts.executeSearch();
    }
  };

  translateArsenalSubject
    .pipe(
      switchMap((req) => {
        if (!req) return EMPTY;
        const {arsenalKey, unpacked} = req;
        return searchService
          .translateArsenalSearch({
            entity: toSearchEntityProto(opts.entity()),
            filters: unpacked.filters.length > 0 ? unpacked.filters : undefined,
            columns: unpacked.columns.length > 0 ? unpacked.columns : undefined,
            groupByKeys:
              unpacked.groupByKeys.length > 0
                ? unpacked.groupByKeys
                : undefined,
          })
          .pipe(
            map((res) => ({res, arsenalKey, unpacked})),
            catchError(() => {
              if (
                lastArsenalKey === arsenalKey &&
                opts.isCurrentRouteActive()
              ) {
                snackBarService.showWarning(
                  'Could not translate legacy Arsenal query.',
                );
              }
              return EMPTY;
            }),
          );
      }),
      takeUntilDestroyed(destroyRef),
    )
    .subscribe(({res, arsenalKey, unpacked}) => {
      if (lastArsenalKey === arsenalKey && opts.isCurrentRouteActive()) {
        applyArsenalTranslation(res, unpacked);
      }
    });

  /** Cancels any in-flight legacy Arsenal query translation RPC. */
  const cancelPendingLegacyTranslation = (): void => {
    lastArsenalKey = null;
    translateArsenalSubject.next(null);
  };

  /** Parses and dispatches `?arsenal_query=...` for BFF translation if present in `params`. */
  const handleLegacyQueryParams = (
    params: Record<string, unknown>,
  ): boolean => {
    const rawArsenalQuery =
      typeof params['arsenal_query'] === 'string'
        ? params['arsenal_query'].trim()
        : '';
    if (!rawArsenalQuery) return false;
    const arsenalKey = `arsenal:${rawArsenalQuery}`;
    if (arsenalKey === lastArsenalKey) return true;
    lastArsenalKey = arsenalKey;
    opts.cancelPendingChipResolution();
    translateArsenalSubject.next({
      arsenalKey,
      unpacked: parseArsenalQueryParam(rawArsenalQuery),
    });
    return true;
  };

  return {
    cancelPendingLegacyTranslation,
    handleLegacyQueryParams,
  };
}
