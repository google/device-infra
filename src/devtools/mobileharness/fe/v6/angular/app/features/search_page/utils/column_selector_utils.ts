import {FleetColumnDescriptor} from '../../../core/models/search';
import {getErrorMessage} from '../../../shared/utils/error_utils';
import {readStorage, removeStorage, writeStorage} from './search_storage_utils';

const STORAGE_VISIBLE_PREFIX = 'mh_fe_v6_visible_columns_';

/**
 * Error message the BFF returns when a requested column key is not (or no
 * longer) known, e.g. `Unknown device key: field::uuid`.
 */
const UNKNOWN_COLUMN_KEY_PATTERN = /Unknown (?:device|host) key: (\S+)/;

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

/**
 * Returns the column key reported by an "Unknown device|host key" fleet search
 * error, or null if the error is something else.
 *
 * The BFF rejects a search whose requested columns contain a key it does not
 * know (typically a key saved to localStorage by an older UI version) with a
 * message like `Unknown device key: field::uuid`. Works for both the OSS
 * (`{code, message}`) and the internal (`{error: {message}}`) error bodies.
 */
export function getUnknownColumnKeyFromError(err: unknown): string | null {
  const match = UNKNOWN_COLUMN_KEY_PATTERN.exec(getErrorMessage(err));
  return match ? match[1] : null;
}
