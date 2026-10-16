import {AllSiteIdType} from '../../core/models/search';
import {NavPageType} from '../../core/utils/navigation';

/**
 * Valid identifier scope keys for the Header Jump Bar and `/search` query parameter `scope`.
 */
export type JumpScopeKey = 'any' | 'device' | 'host' | 'test' | 'job' | 'session';

/**
 * Searchable entity page types used by fallback chips on 404 and Ambiguous views.
 */
export type EntitySearchPageType = Extract<
  NavPageType,
  | 'device_search'
  | 'host_search'
  | 'test_search'
  | 'job_search'
  | 'session_search'
>;

/**
 * Metadata definition for a single Jump Bar identifier scope option.
 */
export interface JumpScopeDefinition {
  /** Scope key used in URL query parameters (`scope=<key>`, omitted when `'any'`). */
  readonly key: JumpScopeKey;
  /** Label displayed inside the leading scope chip button and dropdown menu. */
  readonly label: string;
  /** Material Symbols icon name for this scope. */
  readonly icon: string;
  /** Input placeholder text when this scope is active. */
  readonly placeholder: string;
  /** Subtitle text shown in the Resolving (loading) card on `/search`. */
  readonly resolvingDesc: string;
  /** Corresponding proto `AllSiteIdType` (undefined for `'any'`, which uses `anyId: {}`). */
  readonly idType?: AllSiteIdType;
  /** Target entity search page type for scoped fallback navigation. */
  readonly searchPageType?: EntitySearchPageType;
  /** Plural entity label for scoped fallback button (e.g. "Devices"). */
  readonly searchLabel?: string;
}

/**
 * Ordered list of all Jump Bar scope options matching the UX prototype.
 */
export const JUMP_SCOPE_LIST: readonly JumpScopeDefinition[] = [
  {
    key: 'any',
    label: 'Any ID',
    icon: 'travel_explore',
    placeholder: 'Jump to a device, host, test, job, or session by ID',
    resolvingDesc:
      'Looking up exact identifier across Devices, Hosts, Tests, Jobs, and Sessions.',
  },
  {
    key: 'device',
    label: 'Device ID',
    icon: 'devices',
    placeholder: 'Jump to a device by ID',
    resolvingDesc: 'Looking up exact device ID across connected lab hosts.',
    idType: AllSiteIdType.ID_TYPE_DEVICE_ID,
    searchPageType: 'device_search',
    searchLabel: 'Devices',
  },
  {
    key: 'host',
    label: 'Host name',
    icon: 'dns',
    placeholder: 'Jump to a host by name',
    resolvingDesc: 'Looking up exact host name across labs.',
    idType: AllSiteIdType.ID_TYPE_HOST_NAME,
    searchPageType: 'host_search',
    searchLabel: 'Hosts',
  },
  {
    key: 'test',
    label: 'Test ID',
    icon: 'fact_check',
    placeholder: 'Jump to a test by ID',
    resolvingDesc: 'Looking up exact test ID.',
    idType: AllSiteIdType.ID_TYPE_TEST_ID,
    searchPageType: 'test_search',
    searchLabel: 'Tests',
  },
  {
    key: 'job',
    label: 'Job ID',
    icon: 'task',
    placeholder: 'Jump to a job by ID',
    resolvingDesc: 'Looking up exact job ID.',
    idType: AllSiteIdType.ID_TYPE_JOB_ID,
    searchPageType: 'job_search',
    searchLabel: 'Jobs',
  },
  {
    key: 'session',
    label: 'Session ID',
    icon: 'view_timeline',
    placeholder: 'Jump to a session by ID',
    resolvingDesc: 'Looking up exact session ID.',
    idType: AllSiteIdType.ID_TYPE_SESSION_ID,
    searchPageType: 'session_search',
    searchLabel: 'Sessions',
  },
];

/**
 * Lookup map from JumpScopeKey to JumpScopeDefinition.
 */
export const JUMP_SCOPES: Readonly<Record<JumpScopeKey, JumpScopeDefinition>> =
  JUMP_SCOPE_LIST.reduce(
    (acc, def) => {
      acc[def.key] = def;
      return acc;
    },
    {} as Record<JumpScopeKey, JumpScopeDefinition>,
  );

/**
 * Fallback entity search chip metadata for the 5 entity search pages.
 */
export interface FallbackEntityChip {
  /** Entity key ('device' | 'host' | 'test' | 'job' | 'session'). */
  readonly key: Exclude<JumpScopeKey, 'any'>;
  /** Plural display label (e.g. 'Devices'). */
  readonly pluralLabel: string;
  /** Material Symbols icon name. */
  readonly icon: string;
  /** Target navigation strategy page type. */
  readonly searchPageType: EntitySearchPageType;
}

/**
 * Ordered list of the 5 entity search fallback options displayed on 404 and Ambiguous views.
 */
export const FALLBACK_ENTITY_CHIPS: readonly FallbackEntityChip[] = [
  {
    key: 'device',
    pluralLabel: 'Devices',
    icon: 'devices',
    searchPageType: 'device_search',
  },
  {
    key: 'host',
    pluralLabel: 'Hosts',
    icon: 'dns',
    searchPageType: 'host_search',
  },
  {
    key: 'test',
    pluralLabel: 'Tests',
    icon: 'fact_check',
    searchPageType: 'test_search',
  },
  {
    key: 'job',
    pluralLabel: 'Jobs',
    icon: 'task',
    searchPageType: 'job_search',
  },
  {
    key: 'session',
    pluralLabel: 'Sessions',
    icon: 'view_timeline',
    searchPageType: 'session_search',
  },
];

/**
 * Normalizes a raw URL `scope` query parameter string into a valid `JumpScopeKey`.
 *
 * Input:
 *   - rawScope: Optional string from URL query parameters.
 * Output:
 *   - JumpScopeKey ('any' | 'device' | 'host' | 'test' | 'job' | 'session').
 * Explanation:
 *   Defaults to `'any'` when `rawScope` is missing or not one of the known scope keys.
 */
export function normalizeJumpScope(rawScope?: string | null): JumpScopeKey {
  const cleaned = (rawScope || '').trim().toLowerCase();
  if (
    cleaned === 'device' ||
    cleaned === 'host' ||
    cleaned === 'test' ||
    cleaned === 'job' ||
    cleaned === 'session'
  ) {
    return cleaned;
  }
  return 'any';
}

/** Routes where the Header Jump Bar is hidden because the page has its own search box. */
export const ENTITY_SEARCH_PATHS: ReadonlySet<string> = new Set([
  'devices',
  'hosts',
  'tests',
  'jobs',
  'sessions',
]);

/**
 * Returns true when the global Header Jump Bar should be displayed for the given route path.
 *
 * Input:
 *   - routePath: Active primary route config path string.
 * Output:
 *   - boolean (`true` on Home, `/search`, and Detail pages; `false` on Entity Search pages).
 * Explanation:
 *   Hides the Header Jump Bar on the 5 Entity Search routes (`/devices`, `/hosts`,
 *   `/tests`, `/jobs`, `/sessions`) which render their own dedicated search bar.
 */
export function isHeaderJumpBarVisibleOnRoute(routePath: string): boolean {
  return !ENTITY_SEARCH_PATHS.has(routePath);
}
