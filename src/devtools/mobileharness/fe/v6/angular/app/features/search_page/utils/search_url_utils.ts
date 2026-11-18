/**
 * @fileoverview Pure helpers for route/entity resolution, URL query parameter parsing (`f`, `gb`, `fleet`, `arsenal_query`), and canonical sync keys.
 */

import {ActivatedRoute, Params, Router} from '@angular/router';

import {ArsenalFilterParam} from '../../../core/models/search';
import {
  EntityType,
  FilterChip,
  FilterPair,
  FleetScope,
  ParsedArsenalQueryParams,
  ParsedUrlQueryChips,
} from '../models';
import {
  createGroupByChip,
  deduplicateFilterChipsByKey,
  getChipKey,
  normalizeKey,
  parseQueryFilterParam,
  safeDecodeURIComponent,
  serializeFilterChip,
} from './search_filter_utils';

/** Resolves the active EntityType from path or URL string based on supported domain entities. */
export function resolveEntityFromPathOrUrl(
  pathOrUrl: string,
  supportedEntities: Set<EntityType>,
  defaultEntity: EntityType = 'devices',
): EntityType {
  if (!pathOrUrl) return defaultEntity;
  const clean = pathOrUrl
    .replace(/^\//, '')
    .split('?')[0]
    .split('#')[0]
    .replace(/\/$/, '');
  const segment = clean.split('/')[0] as EntityType;
  return supportedEntities.has(segment) ? segment : defaultEntity;
}

/** Validates whether an active URL/path precisely matches a search store instance's bound entity. */
export function isSearchRouteActive(
  pathOrUrl: string,
  boundEntity: EntityType,
): boolean {
  if (!pathOrUrl || pathOrUrl === '/') return true;
  const clean = pathOrUrl
    .replace(/^\//, '')
    .split('?')[0]
    .split('#')[0]
    .replace(/\/$/, '');
  const segments = clean.split('/').filter(Boolean);
  return segments.length === 1 && segments[0] === boundEntity;
}

/** Normalizes Angular Router query param value (string or string[]) into a clean array of strings. */
export function getQueryParamAsArray(rawParam: unknown): string[] {
  if (Array.isArray(rawParam)) {
    return rawParam
      .map((item) => (item != null ? String(item).trim() : ''))
      .filter(Boolean);
  }
  if (typeof rawParam === 'string') {
    const trimmed = rawParam.trim();
    return trimmed ? [trimmed] : [];
  }
  return [];
}

/** Builds a normalized canonical key string representing URL query parameters for comparison and deduplication. */
export function buildUrlParamKey(
  filters: string[],
  groupBys: string[],
  fleet?: string | null,
): string {
  const normFilters = filters
    .map(normalizeKey)
    .filter(Boolean)
    .sort()
    .join('&');
  const normGb = groupBys.map(normalizeKey).filter(Boolean).sort().join(',');
  const normFleet = normalizeKey(fleet);
  const cleanFleet = normFleet && normFleet !== 'internal' ? normFleet : '';
  return `f=${normFilters}|gb=${normGb}|fleet=${cleanFleet}`;
}

/** Generates a normalized canonical key string for a given array of filter chips. */
export function getSerializedChipsKey(
  chips: FilterChip[],
  fleet?: string | null,
): string {
  const filters = chips
    .filter((c) => !c.isGroupBy)
    .map(serializeFilterChip)
    .filter(Boolean);
  const groupBys = chips
    .filter((c) => c.isGroupBy)
    .map(getChipKey)
    .filter(Boolean);
  return buildUrlParamKey(filters, groupBys, fleet);
}

/** Builds resolved filter chips from backend response. */
export function buildResolvedFilterChips<TFilter, TResolved>(
  filterPairs: Array<FilterPair<TFilter>>,
  resolvedList: TResolved[] | undefined | null,
  enrichFn: (
    chip: FilterChip,
    resolved: TResolved,
    filter: TFilter,
  ) => FilterChip | null | undefined,
): FilterChip[] {
  if (!resolvedList?.length) return [];
  const results: FilterChip[] = [];
  resolvedList.forEach((resolved, idx) => {
    const pair = filterPairs[idx];
    if (pair && resolved) {
      const enriched = enrichFn(pair.chip, resolved, pair.filter);
      if (enriched) {
        results.push(enriched);
      }
    }
  });
  return results;
}

/** Resolves initial URL from router state or browser location synchronously. */
export function getInitialRouterUrl(router: Router): string {
  const nav =
    typeof router.currentNavigation === 'function'
      ? router.currentNavigation()
      : null;
  if (nav?.finalUrl) return nav.finalUrl.toString();
  if (nav?.extractedUrl) return nav.extractedUrl.toString();
  if (router.url && router.url !== '/') return router.url;
  return (
    (typeof window !== 'undefined' && window.location?.pathname) ||
    router.url ||
    ''
  );
}

/** Parses route query params ('f' and 'gb') into structured filter and group-by chips. */
export function parseUrlChips(
  params: Params | null | undefined,
): ParsedUrlQueryChips {
  if (!params) {
    return {parsedFilters: [], groupByKeys: [], initialChips: []};
  }
  const fParams = getQueryParamAsArray(params['f']);
  const groupByKeys = Array.from(
    new Set(
      getQueryParamAsArray(params['gb'])
        .flatMap((g) => g.split(','))
        .map((k) => k.trim())
        .filter(Boolean),
    ),
  );
  const rawFilters = fParams
    .map(parseQueryFilterParam)
    .filter(Boolean) as FilterChip[];
  const parsedFilters = deduplicateFilterChipsByKey(rawFilters);
  const groupByChips = groupByKeys.map((gb: string) => createGroupByChip(gb));
  return {
    parsedFilters,
    groupByKeys,
    initialChips: [...parsedFilters, ...groupByChips],
  };
}

/** Resolves initial filter and group-by chips from ActivatedRoute snapshot query params. */
export function resolveInitialChips(route: ActivatedRoute): FilterChip[] {
  return parseUrlChips(route.snapshot?.queryParams).initialChips;
}

/** Resolves the fleet operational scope from URL query params. */
export function resolveFleetScope(
  params: Params | null | undefined,
  fleetSwitchable: boolean,
): FleetScope {
  if (!fleetSwitchable) return 'internal';
  return params?.['fleet'] === 'ats' ? 'ats' : 'internal';
}

/** Syntactically unpacks an opaque Alkali Arsenal query string (`?arsenal_query=...`). */
export function parseArsenalQueryParam(
  rawQuery: string,
): ParsedArsenalQueryParams {
  const clean = rawQuery.trim().replace(/^\?/, '');
  const searchParams = new URLSearchParams(clean);
  const filters: ArsenalFilterParam[] = [];
  for (const rawFilter of searchParams.getAll('filter')) {
    if (!rawFilter.trim()) continue;
    const parts = rawFilter.split(':');
    const key = safeDecodeURIComponent(parts[0] || '').trim();
    const matchType = safeDecodeURIComponent(parts[1] || '').trim();
    const valueType = safeDecodeURIComponent(parts[2] || '').trim();
    const rawValuePart = parts.slice(3).join(':');
    if (!key || (!matchType && !valueType && !rawValuePart)) continue;

    const normalizedValueType = valueType.toLowerCase();
    const isPattern =
      normalizedValueType === 'substring' || normalizedValueType === 'regex';
    const values = rawValuePart
      ? isPattern
        ? [safeDecodeURIComponent(rawValuePart)]
        : rawValuePart.split(',').map((v) => safeDecodeURIComponent(v))
      : [];
    filters.push({
      key,
      matchType,
      valueType: valueType || undefined,
      values: values.length > 0 ? values : undefined,
    });
  }
  const columns = searchParams
    .getAll('column')
    .flatMap((c) => c.split(','))
    .map((k) => safeDecodeURIComponent(k).trim())
    .filter(Boolean);
  const groupByKeys = searchParams
    .getAll('group_by')
    .flatMap((g) => g.split(','))
    .map((k) => safeDecodeURIComponent(k).trim())
    .filter(Boolean);
  return {filters, columns, groupByKeys};
}

/** Builds URL query parameters and sync key from active filter/group-by chips and fleet scope. */
export function buildOutgoingUrlQueryParams(
  chips: readonly FilterChip[],
  fleet: 'internal' | 'ats',
  legacyKeys: readonly string[],
): {
  syncKey: string;
  queryParams: Record<string, string | string[] | null>;
} {
  const filters = chips
    .filter((c) => !c.isGroupBy)
    .map(serializeFilterChip)
    .filter(Boolean);
  const groupBys = chips
    .filter((c) => c.isGroupBy)
    .map(getChipKey)
    .filter(Boolean);
  const syncKey = buildUrlParamKey(filters, groupBys, fleet);
  const queryParams: Record<string, string | string[] | null> = {
    'f': filters.length > 0 ? filters : null,
    'gb': groupBys.length > 0 ? groupBys.join(',') : null,
    'fleet': fleet !== 'internal' ? fleet : null,
    'q': null,
  };
  for (const key of legacyKeys) {
    queryParams[key] = null;
  }
  return {syncKey, queryParams};
}
