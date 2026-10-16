/**
 * Developer knob for the fake (mock-data) backend: a simulated failure rate in percent.
 *
 * Set it from the browser DevTools console while running with `?fake_data=true`:
 *
 *   localStorage.setItem('mh_fe_v6_fake_failure_rate', '20');   // 20% of fake RPCs fail
 *   localStorage.removeItem('mh_fe_v6_fake_failure_rate');      // back to 0 (never fail)
 *
 * The value is read on every fake RPC call, so changes apply immediately without a reload.
 */

/** localStorage key holding the fake backend failure rate (integer percentage 0-100). */
export const FAKE_FAILURE_RATE_STORAGE_KEY = 'mh_fe_v6_fake_failure_rate';

/**
 * Returns the configured fake failure rate as an integer percentage in [0, 100].
 *
 * Missing, non-numeric, or unreadable values resolve to 0 (never fail). Values outside the range
 * are clamped.
 */
export function getFakeFailureRate(): number {
  try {
    if (typeof window === 'undefined' || !window.localStorage) return 0;
    const raw = window.localStorage.getItem(FAKE_FAILURE_RATE_STORAGE_KEY);
    if (raw === null) return 0;
    const parsed = Number(raw.trim());
    if (Number.isNaN(parsed)) return 0;
    return Math.min(100, Math.max(0, Math.floor(parsed)));
  } catch {
    return 0;
  }
}

/** Rolls the dice once against `getFakeFailureRate()`; true means the fake RPC should fail. */
export function shouldSimulateFailure(): boolean {
  const rate = getFakeFailureRate();
  if (rate <= 0) return false;
  if (rate >= 100) return true;
  return Math.random() * 100 < rate;
}
