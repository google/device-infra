import {computed, DestroyRef, inject, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatDialogRef} from '@angular/material/dialog';
import {Observable, Subscription} from 'rxjs';
import {finalize} from 'rxjs/operators';

import {
  DeviceConfigChangeStatus,
  UpdateError,
} from '../../../../../core/models/device_config_models';
import {
  BulkApplyError,
  BulkApplyResult,
  BulkConfigDialogResult,
  BulkWizardViewMode,
} from '../../../models';
import {
  extractBatchApplyErrors,
  summarizeConfigEntries,
} from './bulk_config_utils';

/**
 * Composable encapsulating the shared Step Navigation, Async Step Loading/Error,
 * Review, Apply, ViewMode, and Close reactive lifecycle for bulk configuration
 * wizard dialogs (e.g. Wi-Fi and Dimension bulk config).
 *
 * Must be called within an Angular injection context (e.g. component field initializer).
 */
export function useBulkWizardSession<
  TEntry extends {deviceId: string; status?: DeviceConfigChangeStatus},
  TPhase extends string = 'review',
>(
  dialogRef: MatDialogRef<unknown, BulkConfigDialogResult | undefined>,
  initialPhase: TPhase = 'review' as TPhase,
) {
  const destroyRef = inject(DestroyRef);
  let stepSub: Subscription | null = null;

  /** Current active wizard phase. */
  const phase = signal<TPhase>(initialPhase);

  /** Whether the active step's async data request is currently in-flight. */
  const isStepLoading = signal<boolean>(false);

  /** Error message if the active step's async data request failed. */
  const stepError = signal<string | null>(null);

  /** Device entries with change status and read-only reasons evaluated by the backend for Review. */
  const reviewEntries = signal<TEntry[]>([]);

  /** Whether the batch update mutation is in-flight. */
  const isApplying = signal<boolean>(false);

  /** Terminal outcome of applying the batch update. */
  const applyResult = signal<BulkApplyResult | null>(null);

  /** Per-device failures returned by the backend. */
  const applyErrors = signal<BulkApplyError[]>([]);

  /** Single-pass review summary consolidating counts and willUpdateRows. */
  const reviewSummary = computed(() => summarizeConfigEntries(reviewEntries()));

  /** Number of devices successfully updated when partial errors occur. */
  const applySuccessCount = computed<number>(() =>
    Math.max(0, reviewSummary().willUpdateCount - applyErrors().length),
  );

  /**
   * Unified wizard view mode derived from terminal apply states and the current
   * active phase's loading/error flags.
   */
  const viewMode = computed<BulkWizardViewMode<TPhase>>(() => {
    if (isApplying()) return 'applying';
    if (applyResult() === 'success') return 'success';
    if (applyResult() === 'errors') return 'errors';
    const currentPhase = phase();
    if (isStepLoading()) return `${currentPhase}_loading`;
    if (stepError()) return `${currentPhase}_error`;
    return currentPhase;
  });

  /** Cancels any in-flight step request and clears step loading/error state. */
  const cancelStepTask = (): void => {
    stepSub?.unsubscribe();
    stepSub = null;
    isStepLoading.set(false);
    stepError.set(null);
  };

  /** Transitions the wizard to a new phase, cancelling any in-flight step request. */
  const setPhase = (nextPhase: TPhase): void => {
    cancelStepTask();
    phase.set(nextPhase);
  };

  /** Executes an async data request for the active step, cancelling any prior in-flight request. */
  const runStepTask = <TResponse>(
    request$: Observable<TResponse>,
    onSuccess: (res: TResponse) => void,
    fallbackErrorMessage: string,
  ): void => {
    cancelStepTask();
    isStepLoading.set(true);
    stepSub = request$
      .pipe(
        takeUntilDestroyed(destroyRef),
        finalize(() => {
          isStepLoading.set(false);
        }),
      )
      .subscribe({
        next: onSuccess,
        error: (err: unknown) => {
          stepError.set(
            (err as {message?: string})?.message || fallbackErrorMessage,
          );
        },
      });
  };

  /** Transitions to `'review'` and executes the backend dry-run / review check. */
  const loadReview = (
    request$: Observable<{entries?: TEntry[]}>,
    fallbackErrorMessage: string,
  ): void => {
    phase.set('review' as TPhase);
    runStepTask(
      request$,
      (res) => {
        reviewEntries.set(res.entries ?? []);
      },
      fallbackErrorMessage,
    );
  };

  /** Executes the batch update mutation for all WILL_UPDATE devices, locking dialog close while in-flight. */
  const apply = (
    createRequest: (
      updateIds: string[],
    ) => Observable<{errors?: Record<string, UpdateError>}>,
    fallbackErrorMessage: string,
  ): void => {
    const updateIds = reviewSummary().willUpdateRows.map((r) => r.deviceId);
    if (updateIds.length === 0) return;

    isApplying.set(true);
    dialogRef.disableClose = true;

    createRequest(updateIds)
      .pipe(
        takeUntilDestroyed(destroyRef),
        finalize(() => {
          isApplying.set(false);
          dialogRef.disableClose = false;
        }),
      )
      .subscribe({
        next: (res) => {
          const errors = extractBatchApplyErrors(res.errors);
          if (errors.length > 0) {
            applyErrors.set(errors);
            applyResult.set('errors');
          } else {
            applyResult.set('success');
          }
        },
        error: (err: unknown) => {
          const reason =
            (err as {message?: string})?.message || fallbackErrorMessage;
          applyErrors.set(updateIds.map((deviceId) => ({deviceId, reason})));
          applyResult.set('errors');
        },
      });
  };

  /** Closes the dialog and reports whether at least one device was updated. */
  const close = (): void => {
    const hasUpdated =
      applyResult() === 'success' ||
      (applyResult() === 'errors' && applySuccessCount() > 0);
    dialogRef.close(hasUpdated ? {updated: true} : undefined);
  };

  return {
    phase,
    isStepLoading,
    stepError,
    reviewEntries,
    isApplying,
    applyResult,
    applyErrors,
    reviewSummary,
    applySuccessCount,
    viewMode,
    cancelStepTask,
    setPhase,
    runStepTask,
    loadReview,
    apply,
    close,
  };
}
