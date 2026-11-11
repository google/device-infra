import {computed, DestroyRef, inject, Signal, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatDialogRef} from '@angular/material/dialog';
import {Observable, Subscription} from 'rxjs';
import {finalize} from 'rxjs/operators';

import {
  DeviceConfigChangeStatus,
  DeviceTarget,
  UpdateError,
} from '../../../../../core/models/device_config_models';
import {getErrorMessage} from '../../../../../shared/utils/error_utils';
import {
  BulkApplyError,
  BulkConfigDialogResult,
  BulkConfigReviewSummary,
} from '../../../models';
import {formatDeviceCount} from '../../../utils';

/** Cancellable, retryable async task tracker returned by `useInlineTask`. */
export interface BulkInlineTask {
  readonly isLoading: Signal<boolean>;
  readonly hasLoaded: Signal<boolean>;
  readonly error: Signal<string | null>;
  run: <TResponse>(
    createRequest: () => Observable<TResponse>,
    onSuccess: (res: TResponse) => void,
    fallbackErrorMessage?: string,
  ) => void;
  retry: () => void;
  cancel: () => void;
}

/** Discriminated union representing the mutually exclusive states of the batch apply mutation. */
type BulkApplyState =
  | {readonly status: 'idle'}
  | {readonly status: 'applying'}
  | {readonly status: 'rpc_error'; readonly message: string}
  | {readonly status: 'success'}
  | {readonly status: 'errors'; readonly errors: readonly BulkApplyError[]};

/** Reactive wizard session returned by `useBulkWizardSession`. */
export type BulkWizardSession<
  TEntry extends {
    deviceId: string;
    hostName?: string;
    status?: DeviceConfigChangeStatus;
  } = DeviceTarget,
  TPhase extends string = string,
> = ReturnType<typeof useBulkWizardSession<TEntry, TPhase>>;

/**
 * Creates a cancellable, retryable async task tracker tied to the current
 * component's `DestroyRef`.
 */
export function useInlineTask(defaultFallbackError = ''): BulkInlineTask {
  const destroyRef = inject(DestroyRef);
  let sub: Subscription | null = null;
  let retryFn: (() => void) | null = null;
  const isLoading = signal<boolean>(false);
  const hasLoaded = signal<boolean>(false);
  const error = signal<string | null>(null);

  const cancel = (): void => {
    sub?.unsubscribe();
    error.set(null);
  };

  const run = <TResponse>(
    createRequest: () => Observable<TResponse>,
    onSuccess: (res: TResponse) => void,
    fallbackErrorMessage = defaultFallbackError,
  ): void => {
    retryFn = () => {
      run(createRequest, onSuccess, fallbackErrorMessage);
    };
    cancel();
    hasLoaded.set(false);
    isLoading.set(true);
    sub = createRequest()
      .pipe(
        takeUntilDestroyed(destroyRef),
        finalize(() => {
          isLoading.set(false);
        }),
      )
      .subscribe({
        next: (res) => {
          onSuccess(res);
          hasLoaded.set(true);
        },
        error: (err: unknown) => {
          error.set(getErrorMessage(err, fallbackErrorMessage));
        },
      });
  };

  const retry = (): void => {
    retryFn?.();
  };

  return {
    isLoading: isLoading.asReadonly(),
    hasLoaded: hasLoaded.asReadonly(),
    error: error.asReadonly(),
    run,
    retry,
    cancel,
  };
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

  const stepTask = useInlineTask();

  /** Current active wizard phase. */
  const phase = signal<TPhase>(initialPhase);

  /** Fallback headline title for a failed step RPC. */
  const stepErrorTitle = signal<string | null>(null);

  /** Device entries with change status and read-only reasons evaluated by the backend for Review. */
  const reviewEntries = signal<TEntry[]>([]);

  /** Unified state machine for the batch apply mutation. */
  const applyState = signal<BulkApplyState>({status: 'idle'});

  /** Per-device failures returned by the backend when `applyState().status === 'errors'`. */
  const applyErrors = computed<readonly BulkApplyError[]>(() => {
    const s = applyState();
    return s.status === 'errors' ? s.errors : [];
  });

  /** Whole-RPC error message when the batch update request itself fails (keeps user on review step to retry). */
  const applyRpcError = computed<string | null>(() => {
    const s = applyState();
    return s.status === 'rpc_error' ? s.message : null;
  });

  /** Single-pass review summary consolidating device status counts and labels. */
  const reviewSummary = computed<BulkConfigReviewSummary>(() => {
    let willUpdateCount = 0;
    let alreadyMatchCount = 0;
    let readOnlyCount = 0;

    for (const entry of reviewEntries()) {
      switch (entry.status) {
        case DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE:
          willUpdateCount++;
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

    return {
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
    const applyStatus = applyState().status;
    if (
      applyStatus === 'applying' ||
      applyStatus === 'success' ||
      applyStatus === 'errors'
    ) {
      return applyStatus;
    }
    if (stepTask.isLoading()) return 'loading';
    if (stepTask.error()) return 'step_error';
    return phase();
  });

  /** Transitions the wizard to a phase, cancelling any in-flight step request and resetting apply/step error state. */
  const setPhase = (nextPhase: TPhase): void => {
    stepTask.cancel();
    applyState.set({status: 'idle'});
    phase.set(nextPhase);
  };

  /** Transitions to `'review'` and executes the backend dry-run / review check. */
  const loadReview = (
    createRequest: () => Observable<{entries?: TEntry[]}>,
    fallbackErrorMessage: string,
  ): void => {
    reviewEntries.set([]);
    setPhase('review' as TPhase);
    stepErrorTitle.set(fallbackErrorMessage || null);
    stepTask.run(
      createRequest,
      (res) => {
        reviewEntries.set(res.entries ?? []);
      },
      fallbackErrorMessage,
    );
  };

  /** Executes the batch update mutation for all WILL_UPDATE devices, locking dialog close while in-flight. */
  const apply = (
    createRequest: (
      updateTargets: DeviceTarget[],
    ) => Observable<{errors?: Record<string, UpdateError>}>,
    fallbackErrorMessage: string,
  ): void => {
    if (applyState().status === 'applying') return;
    const updateTargets: DeviceTarget[] = [];
    for (const r of reviewEntries()) {
      if (
        r.status ===
        DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE
      ) {
        updateTargets.push(
          r.hostName
            ? {deviceId: r.deviceId, hostName: r.hostName}
            : {deviceId: r.deviceId},
        );
      }
    }
    if (updateTargets.length === 0) return;

    applyState.set({status: 'applying'});
    dialogRef.disableClose = true;

    createRequest(updateTargets)
      .pipe(
        takeUntilDestroyed(destroyRef),
        finalize(() => {
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
          applyState.set(
            errors.length > 0
              ? {status: 'errors', errors}
              : {status: 'success'},
          );
        },
        error: (err: unknown) => {
          applyState.set({
            status: 'rpc_error',
            message: getErrorMessage(err, fallbackErrorMessage),
          });
        },
      });
  };

  /** Closes the dialog and reports whether at least one device was updated. */
  const close = (): void => {
    const s = applyState();
    const hasUpdated =
      s.status === 'success' ||
      (s.status === 'errors' &&
        reviewSummary().willUpdateCount > s.errors.length);
    dialogRef.close(hasUpdated ? {updated: true} : undefined);
  };

  return {
    phase: phase.asReadonly(),
    stepError: stepTask.error,
    stepErrorTitle: stepErrorTitle.asReadonly(),
    reviewEntries: reviewEntries.asReadonly(),
    applyErrors,
    applyRpcError,
    reviewSummary,
    viewMode,
    setPhase,
    loadReview,
    retryStep: stepTask.retry,
    apply,
    close,
  };
}
