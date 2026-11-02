import {readStorage, removeStorage, writeStorage} from './search_storage_utils';

const STORAGE_AUTO_COLLAPSE_PREFIX = 'mh_fe_v6_auto_collapse_after_apply_';
const STORAGE_FILTER_COLLAPSED_PREFIX = 'mh_fe_v6_filter_collapsed_';

/**
 * Retrieves the saved auto-collapse preference from localStorage.
 * Scoped per entity and fleet partition.
 */
export function getStoredAutoCollapseAfterApply(
  entity: string,
  fleet: string,
): boolean | null {
  const parsed = readStorage(
    `${STORAGE_AUTO_COLLAPSE_PREFIX}${entity}_${fleet}`,
  );
  if (typeof parsed === 'boolean') {
    return parsed;
  }
  if (parsed === 'true') return true;
  if (parsed === 'false') return false;
  return null;
}

/**
 * Persists the auto-collapse preference to localStorage.
 * Scoped per entity and fleet partition.
 */
export function saveStoredAutoCollapseAfterApply(
  entity: string,
  fleet: string,
  enabled: boolean,
): void {
  writeStorage(`${STORAGE_AUTO_COLLAPSE_PREFIX}${entity}_${fleet}`, enabled);
}

/**
 * Clears the saved auto-collapse preference in localStorage for an entity and fleet.
 */
export function clearStoredAutoCollapseAfterApply(
  entity: string,
  fleet: string,
): void {
  removeStorage(`${STORAGE_AUTO_COLLAPSE_PREFIX}${entity}_${fleet}`);
}

/**
 * Retrieves the saved collapsed state of the quick filter / group-by panel
 * ("Hide" / "Suggested filters") from localStorage. Scoped per entity so that
 * the device and host search pages remember it independently.
 *
 * @param entity The search entity (e.g. 'devices', 'hosts').
 * @return True if the panel was hidden, false if shown, or null if never saved.
 */
export function getStoredFilterCollapsed(entity: string): boolean | null {
  const parsed = readStorage(`${STORAGE_FILTER_COLLAPSED_PREFIX}${entity}`);
  return typeof parsed === 'boolean' ? parsed : null;
}

/**
 * Persists the collapsed state of the quick filter / group-by panel to
 * localStorage. Scoped per entity.
 *
 * @param entity The search entity (e.g. 'devices', 'hosts').
 * @param collapsed Whether the panel is hidden.
 */
export function saveStoredFilterCollapsed(
  entity: string,
  collapsed: boolean,
): void {
  writeStorage(`${STORAGE_FILTER_COLLAPSED_PREFIX}${entity}`, collapsed);
}
