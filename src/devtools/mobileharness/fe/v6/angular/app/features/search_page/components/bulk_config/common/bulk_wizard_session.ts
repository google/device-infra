import {computed, DestroyRef, inject, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatDialogRef} from '@angular/material/dialog';
import {Observable, Subscription} from 'rxjs';
import {finalize} from 'rxjs/operators';

import {
  DeviceConfigChangeStatus,
  DeviceTarget,
  UpdateError,
} from '../../../../../core/models/device_config_models';
import {
  BulkApplyError,
  BulkConfigDialogResult,
  BulkConfigReviewSummary,
} from '../../../models';
import {formatDeviceCount} from '../../../utils';

function extractErrorMessage(err: unknown, fallback: string): string {
  return (err as {message?: string})?.message || fallback;
}

/**
 * Composable encapsulating the shared Step Navigation, Async Step Loading/Error,
 * Review, Apply, ViewMode, and Close reactive lifecycle for bulk configuration
 * wizard dialogs (e.g. Wi-Fi and Dimension bulk config).
 *
 * Must be called within an Angular injection context (e.g. component field initializer).
 */
export function useBulkWizardSession<
  TEntry extends {
    deviceId: string;
    hostName?: string;
    status?: DeviceConfigChangeStatus;
  },
  TPhase extends string = 'review',
>(initialPhase: TPhase = 'review' as TPhase) {
  const destroyRef = inject(DestroyRef);
  const dialogRef =
    inject<MatDialogRef<unknown, BulkConfigDialogResult | undefined>>(
      MatDialogRef,
    );
  let stepSub: Subscription | null = null;
  let retryStepFn: (() => void) | null = null;

  /** Current active wizard phase. */
  const phase = signal<TPhase>(initialPhase);

  /** Whether the active step's async data request is currently in-flight. */
  const isStepLoading = signal<boolean>(false);

  /** Loading message for the active step's in-flight request. */
  const stepLoadingMessage = signal<string>('');

  /** Error message if the active step's async data request failed. */
  const stepError = signal<string | null>(null);

  /** Active status message for `'loading'` or `'step_error'` views. */
  const statusMessage = computed<string>(
    () => stepError() ?? stepLoadingMessage(),
  );

  /** Device entries with change status and read-only reasons evaluated by the backend for Review. */
  const reviewEntries = signal<TEntry[]>([]);

  /** Whether the batch update mutation is in-flight. */
  const isApplying = signal<boolean>(false);

  /** Terminal outcome of applying the batch update. */
  const applyResult = signal<'success' | 'errors' | null>(null);

  /** Per-device failures returned by the backend. */
  const applyErrors = signal<BulkApplyError[]>([]);

  /** Single-pass review summary consolidating counts and willUpdateRows. */
  const reviewSummary = computed<
    BulkConfigReviewSummary & {willUpdateRows: TEntry[]}
  >(() => {
    let alreadyMatchCount = 0;
    let readOnlyCount = 0;
    const willUpdateRows: TEntry[] = [];

    for (const entry of reviewEntries()) {
      switch (entry.status) {
        case DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE:
          willUpdateRows.push(entry);
          break;
        case DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES:
          alreadyMatchCount++;
          break;
        case DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY:
          readOnlyCount++;
          break;
        default:
          break;
      }
    }

    const willUpdateCount = willUpdateRows.length;
    return {
      willUpdateRows,
      willUpdateCount,
      willUpdateLabel: formatDeviceCount(willUpdateCount),
      alreadyMatchCount,
      alreadyMatchLabel: formatDeviceCount(alreadyMatchCount),
      readOnlyCount,
      readOnlyLabel: formatDeviceCount(readOnlyCount),
    };
  });

  /**
   * Unified wizard view mode derived from terminal apply states, step
   * loading/error status, and the current active phase.
   */
  const viewMode = computed<
    TPhase | 'loading' | 'step_error' | 'applying' | 'success' | 'errors'
  >(() => {
    if (isApplying()) return 'applying';
    if (applyResult() === 'success') return 'success';
    if (applyResult() === 'errors') return 'errors';
    if (isStepLoading()) return 'loading';
    if (stepError()) return 'step_error';
    return phase();
  });

  /** Resolves the dialog headline across terminal apply states and active step phases. */
  const resolveHeadline = (
    targetName: string,
    stepHeadline: string,
  ): string => {
    if (isApplying()) return `Applying ${targetName}`;
    if (applyResult() === 'success') return `${targetName} updated`;
    if (applyResult() === 'errors') {
      return `${formatDeviceCount(applyErrors().length)} could not be updated`;
    }
    return stepHeadline;
  };

  /** Resolves the dialog support subtitle, clearing it during terminal apply states. */
  const resolveSupportText = (stepSupportText: string): string =>
    isApplying() || applyResult() !== null ? '' : stepSupportText;

  /** Transitions the wizard to a phase, cancelling any in-flight step request and clearing step loading/error state. */
  const setPhase = (nextPhase: TPhase): void => {
    stepSub?.unsubscribe();
    stepSub = null;
    isStepLoading.set(false);
    stepError.set(null);
    phase.set(nextPhase);
  };

  /** Executes an async data request for the active (or specified) step, cancelling any prior in-flight request. */
  const runStepTask = <TResponse>(
    requestSource: Observable<TResponse> | (() => Observable<TResponse>),
    onSuccess: (res: TResponse) => void,
    fallbackErrorMessage: string,
    targetPhase?: TPhase,
    loadingMessage = '',
  ): void => {
    const resolvedPhase = targetPhase ?? phase();
    retryStepFn = () => {
      runStepTask(
        requestSource,
        onSuccess,
        fallbackErrorMessage,
        resolvedPhase,
        loadingMessage,
      );
    };
    setPhase(resolvedPhase);
    stepLoadingMessage.set(loadingMessage);
    isStepLoading.set(true);
    const request$ =
      typeof requestSource === 'function' ? requestSource() : requestSource;
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
          stepError.set(extractErrorMessage(err, fallbackErrorMessage));
        },
      });
  };

  /** Transitions to `'review'` and executes the backend dry-run / review check. */
  const loadReview = (
    requestSource:
      | Observable<{entries?: TEntry[]}>
      | (() => Observable<{entries?: TEntry[]}>),
    fallbackErrorMessage: string,
    loadingMessage = 'Checking device configurations…',
  ): void => {
    reviewEntries.set([]);
    runStepTask(
      requestSource,
      (res) => {
        reviewEntries.set(res.entries ?? []);
      },
      fallbackErrorMessage,
      'review' as TPhase,
      loadingMessage,
    );
  };

  /** Retries the most recent step request. */
  const retryStep = (): void => {
    retryStepFn?.();
  };

  /** Executes the batch update mutation for all WILL_UPDATE devices, locking dialog close while in-flight. */
  const apply = (
    createRequest: (
      updateTargets: DeviceTarget[],
    ) => Observable<{errors?: Record<string, UpdateError>}>,
    fallbackErrorMessage: string,
  ): void => {
    if (isApplying()) return;
    const updateTargets: DeviceTarget[] = reviewSummary().willUpdateRows.map(
      (r) => ({
        deviceId: r.deviceId,
        ...(r.hostName ? {hostName: r.hostName} : {}),
      }),
    );
    if (updateTargets.length === 0) return;

    isApplying.set(true);
    applyResult.set(null);
    applyErrors.set([]);
    dialogRef.disableClose = true;

    createRequest(updateTargets)
      .pipe(
        takeUntilDestroyed(destroyRef),
        finalize(() => {
          isApplying.set(false);
          dialogRef.disableClose = false;
        }),
      )
      .subscribe({
        next: (res) => {
          const errors = Object.entries(res.errors || {}).map(
            ([deviceId, err]) => ({
              deviceId,
              reason: err.message || err.code,
            }),
          );
          if (errors.length > 0) {
            applyErrors.set(errors);
            applyResult.set('errors');
          } else {
            applyResult.set('success');
          }
        },
        error: (err: unknown) => {
          const reason = extractErrorMessage(err, fallbackErrorMessage);
          applyErrors.set(
            updateTargets.map(({deviceId}) => ({deviceId, reason})),
          );
          applyResult.set('errors');
        },
      });
  };

  /** Closes the dialog and reports whether at least one device was updated. */
  const close = (): void => {
    const hasUpdated =
      applyResult() === 'success' ||
      (applyResult() === 'errors' &&
        reviewSummary().willUpdateCount > applyErrors().length);
    dialogRef.close(hasUpdated ? {updated: true} : undefined);
  };

  return {
    phase: phase.asReadonly(),
    stepError: stepError.asReadonly(),
    statusMessage,
    reviewEntries: reviewEntries.asReadonly(),
    applyErrors: applyErrors.asReadonly(),
    reviewSummary,
    viewMode,
    resolveHeadline,
    resolveSupportText,
    setPhase,
    runStepTask,
    loadReview,
    retryStep,
    apply,
    close,
  };
}
