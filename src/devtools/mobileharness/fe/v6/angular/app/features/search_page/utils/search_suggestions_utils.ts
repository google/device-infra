const STORAGE_AUTO_COLLAPSE_PREFIX = 'mh_fe_v6_auto_collapse_after_apply_';

function readStorage(key: string): unknown {
  if (typeof window === 'undefined' || !window.localStorage) return null;
  try {
    const raw = window.localStorage.getItem(key);
    return raw !== null ? JSON.parse(raw) : null;
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
  writeStorage(
    `${STORAGE_AUTO_COLLAPSE_PREFIX}${entity}_${fleet}`,
    enabled,
  );
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
