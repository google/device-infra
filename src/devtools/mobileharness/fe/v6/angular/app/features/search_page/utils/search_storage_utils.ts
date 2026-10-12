/**
 * Safely reads and JSON-parses a value from window.localStorage.
 * Returns null if localStorage is unavailable, the key is absent, or JSON parsing fails.
 */
export function readStorage(key: string): unknown {
  if (typeof window === 'undefined' || !window.localStorage) return null;
  try {
    const raw = window.localStorage.getItem(key);
    return raw !== null ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

/**
 * Safely JSON-stringifies and writes a value to window.localStorage.
 */
export function writeStorage(key: string, value: unknown): void {
  if (typeof window === 'undefined' || !window.localStorage) return;
  try {
    window.localStorage.setItem(key, JSON.stringify(value));
  } catch {}
}

/**
 * Safely removes a key from window.localStorage.
 */
export function removeStorage(key: string): void {
  if (typeof window === 'undefined' || !window.localStorage) return;
  try {
    window.localStorage.removeItem(key);
  } catch {}
}
