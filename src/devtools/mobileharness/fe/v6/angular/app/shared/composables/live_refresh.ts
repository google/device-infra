import {DOCUMENT} from '@angular/common';
import {
  DestroyRef,
  InjectionToken,
  Signal,
  computed,
  inject,
  signal,
} from '@angular/core';
import {takeUntilDestroyed, toObservable} from '@angular/core/rxjs-interop';
import {EMPTY, Subject, combineLatest, fromEvent, timer} from 'rxjs';
import {startWith, switchMap} from 'rxjs/operators';

/** How often a live view fetches what it shows again, in milliseconds. */
export const LIVE_REFRESH_INTERVAL_MS = new InjectionToken<number>(
  'LIVE_REFRESH_INTERVAL_MS',
  {providedIn: 'root', factory: () => 30_000},
);

/** State and helpers returned by {@link useLiveRefresh}. */
export interface LiveRefreshState {
  /** Increments on each live refresh tick. */
  readonly refreshes: Signal<number>;
  /** Note shown while `live` is true, or empty when final. */
  readonly liveNote: Signal<string>;
  /** Records that the caller just completed a fetch so the next timer waits a full interval. */
  readonly markRefreshed: () => void;
}

/**
 * Composable that counts the refreshes of something that keeps changing while
 * `live` is true, for as long as the view showing it is `active` and the
 * browser tab is visible.
 *
 * The count goes up once every {@link LIVE_REFRESH_INTERVAL_MS}. A view that
 * comes back after a pause refreshes as soon as an interval has passed since
 * the last refresh, so a view hidden for a while is not left stale for another
 * full interval. When `live` transitions from false to true or the caller calls
 * `markRefreshed()`, the interval countdown resets.
 */
export function useLiveRefresh(
  live: Signal<boolean>,
  active: Signal<boolean>,
  entityLabel: 'test' | 'job' = 'test',
): LiveRefreshState {
  const intervalMs = inject(LIVE_REFRESH_INTERVAL_MS);
  const destroyRef = inject(DestroyRef);
  const doc = inject(DOCUMENT, {optional: true});
  const refreshes = signal(0);
  const pageVisible = signal(doc?.visibilityState !== 'hidden');
  const isShowing = computed(() => active() && pageVisible());
  const cycle$ = new Subject<void>();
  let dueWhileHidden = false;

  if (doc) {
    fromEvent(doc, 'visibilitychange')
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe(() => {
        pageVisible.set(doc.visibilityState !== 'hidden');
      });
  }

  combineLatest([toObservable(live), cycle$.pipe(startWith(undefined))])
    .pipe(
      switchMap(([isLive]) => {
        if (!isLive || dueWhileHidden) {
          if (!isLive) {
            dueWhileHidden = false;
          }
          return EMPTY;
        }
        return timer(intervalMs, intervalMs);
      }),
      takeUntilDestroyed(destroyRef),
    )
    .subscribe(() => {
      if (isShowing()) {
        refreshes.update((count) => count + 1);
      } else {
        dueWhileHidden = true;
        cycle$.next(undefined);
      }
    });

  toObservable(computed(() => live() && isShowing()))
    .pipe(takeUntilDestroyed(destroyRef))
    .subscribe((showingLive) => {
      if (showingLive && dueWhileHidden) {
        dueWhileHidden = false;
        refreshes.update((count) => count + 1);
        cycle$.next(undefined);
      }
    });

  const refreshSeconds = Math.round(intervalMs / 1000);
  const liveNote = computed(() =>
    live()
      ? `Updates every ${refreshSeconds} seconds while the ${entityLabel} runs.`
      : '',
  );

  return {
    refreshes: refreshes.asReadonly(),
    liveNote,
    markRefreshed: () => {
      dueWhileHidden = false;
      cycle$.next(undefined);
    },
  };
}
