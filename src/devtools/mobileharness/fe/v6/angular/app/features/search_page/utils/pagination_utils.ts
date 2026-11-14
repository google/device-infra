import {PAGE_SIZE_OPTIONS} from '../models';
import {readStorage, writeStorage} from './search_storage_utils';

const STORAGE_PAGE_SIZE_PREFIX = 'mh_fe_v6_page_size_';
const STORAGE_GROUP_EXPAND_PAGE_SIZE_PREFIX =
  'mh_fe_v6_group_expand_page_size_';

/**
 * Returns the stored value when it is one of the offered page-size options,
 * otherwise null, so a stale or hand-edited entry never produces an unexpected
 * request size or an empty selector.
 */
function toValidPageSize(parsed: unknown): number | null {
  return typeof parsed === 'number' && PAGE_SIZE_OPTIONS.includes(parsed)
    ? parsed
    : null;
}

/**
 * Retrieves the saved "Rows per page" of the flat fleet results table from
 * localStorage. Scoped per entity so the device and host search pages remember
 * it independently (`mh_fe_v6_page_size_devices`, `mh_fe_v6_page_size_hosts`).
 *
 * @param entity The search entity (e.g. 'devices', 'hosts').
 * @return The saved page size, or null if never saved or not a valid option.
 */
export function getStoredPageSize(entity: string): number | null {
  return toValidPageSize(readStorage(`${STORAGE_PAGE_SIZE_PREFIX}${entity}`));
}

/**
 * Persists the "Rows per page" of the flat fleet results table to
 * localStorage. Scoped per entity.
 */
export function saveStoredPageSize(entity: string, pageSize: number): void {
  writeStorage(`${STORAGE_PAGE_SIZE_PREFIX}${entity}`, pageSize);
}

/**
 * Retrieves the saved "Rows per page" used when a group card is expanded for
 * the first time. Scoped per entity (`mh_fe_v6_group_expand_page_size_devices`,
 * `mh_fe_v6_group_expand_page_size_hosts`); groups that are already loaded keep
 * the page size they were loaded with.
 *
 * @param entity The search entity (e.g. 'devices', 'hosts').
 * @return The saved page size, or null if never saved or not a valid option.
 */
export function getStoredGroupExpandPageSize(entity: string): number | null {
  return toValidPageSize(
    readStorage(`${STORAGE_GROUP_EXPAND_PAGE_SIZE_PREFIX}${entity}`),
  );
}

/**
 * Persists the "Rows per page" chosen inside an expanded group card to
 * localStorage, so the next group expanded for the first time starts with it.
 * Scoped per entity.
 */
export function saveStoredGroupExpandPageSize(
  entity: string,
  pageSize: number,
): void {
  writeStorage(`${STORAGE_GROUP_EXPAND_PAGE_SIZE_PREFIX}${entity}`, pageSize);
}
