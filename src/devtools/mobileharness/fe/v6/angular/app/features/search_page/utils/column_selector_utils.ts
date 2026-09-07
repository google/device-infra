import {FleetColumnDescriptor} from '../../../core/models/search';

const STORAGE_VISIBLE_COLUMNS_PREFIX = 'mh_fe_v6_visible_columns_';

/**
 * Retrieves custom visible table columns saved by the user from localStorage.
 * Scoped per entity and fleet partition. Returns isomorphic FleetColumnDescriptor array.
 */
export function getStoredVisibleColumns(
  entity: string,
  fleet: string,
): FleetColumnDescriptor[] | null {
  if (typeof window === 'undefined' || !window.localStorage) return null;
  try {
    const raw = window.localStorage.getItem(
      `${STORAGE_VISIBLE_COLUMNS_PREFIX}${entity}_${fleet}`,
    );
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length > 0) {
        return parsed as FleetColumnDescriptor[];
      }
    }
  } catch {}
  return null;
}

/**
 * Persists customized visible table column descriptors to localStorage.
 * Scoped per entity and fleet partition.
 */
export function saveStoredVisibleColumns(
  entity: string,
  fleet: string,
  columns: FleetColumnDescriptor[],
): void {
  if (typeof window === 'undefined' || !window.localStorage) return;
  try {
    window.localStorage.setItem(
      `${STORAGE_VISIBLE_COLUMNS_PREFIX}${entity}_${fleet}`,
      JSON.stringify(columns),
    );
  } catch {}
}

/**
 * Clears custom visible table columns saved in localStorage for an entity and fleet.
 */
export function clearStoredVisibleColumns(entity: string, fleet: string): void {
  if (typeof window === 'undefined' || !window.localStorage) return;
  try {
    window.localStorage.removeItem(
      `${STORAGE_VISIBLE_COLUMNS_PREFIX}${entity}_${fleet}`,
    );
  } catch {}
}
