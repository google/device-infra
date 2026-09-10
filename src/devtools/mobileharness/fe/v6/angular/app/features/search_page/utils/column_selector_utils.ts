import {FleetColumnDescriptor} from '../../../core/models/search';

const STORAGE_VISIBLE_PREFIX = 'mh_fe_v6_visible_columns_';

function readStorage(key: string): unknown {
  if (typeof window === 'undefined' || !window.localStorage) return null;
  try {
    const raw = window.localStorage.getItem(key);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: unknown): void {
  if (typeof window === 'undefined' || !window.localStorage) return;
  try {
    window.localStorage.setItem(key, JSON.stringify(value));
  } catch {}
}

function removeStorage(key: string): void {
  if (typeof window === 'undefined' || !window.localStorage) return;
  try {
    window.localStorage.removeItem(key);
  } catch {}
}

/**
 * Retrieves custom visible table columns saved by the user from localStorage.
 * Scoped per entity and fleet partition.
 */
export function getStoredVisibleColumns(
  entity: string,
  fleet: string,
): FleetColumnDescriptor[] | null {
  const parsed = readStorage(
    `${STORAGE_VISIBLE_PREFIX}${entity}_${fleet}`,
  );
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
