import {FleetColumnDescriptor} from '../../../core/models/search';
import {readStorage, removeStorage, writeStorage} from './search_storage_utils';

const STORAGE_VISIBLE_PREFIX = 'mh_fe_v6_visible_columns_';

/**
 * Retrieves custom visible table columns saved by the user from localStorage.
 * Scoped per entity and fleet partition.
 */
export function getStoredVisibleColumns(
  entity: string,
  fleet: string,
): FleetColumnDescriptor[] | null {
  const parsed = readStorage(`${STORAGE_VISIBLE_PREFIX}${entity}_${fleet}`);
  return Array.isArray(parsed) && parsed.length > 0
    ? (parsed as FleetColumnDescriptor[])
    : null;
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
  writeStorage(`${STORAGE_VISIBLE_PREFIX}${entity}_${fleet}`, columns);
}

/**
 * Clears custom visible table columns saved in localStorage for an entity and fleet.
 */
export function clearStoredVisibleColumns(entity: string, fleet: string): void {
  removeStorage(`${STORAGE_VISIBLE_PREFIX}${entity}_${fleet}`);
}
